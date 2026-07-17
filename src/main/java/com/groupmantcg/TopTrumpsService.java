package com.groupmantcg;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;

/** Runs consent, card draw, validation, and result state for live group Top Trumps duels. */
@Singleton
class TopTrumpsService
{
	static final String MENU_OPTION = "Top Trumps";
	private static final int PROTOCOL = 1;
	private static final long CHALLENGE_MILLIS = 30_000L;
	private static final int MAX_SEEN_CHALLENGES = 32;

	private final Client client;
	private final ClientThread clientThread;
	private final GroupmanTcgConfig config;
	private final SharedCollectionService collection;
	private final CardVisualCatalog cards;
	private final CardArtService cardArt;
	private final PartyService partyService;
	private final WSClient wsClient;
	private final ChatboxPanelManager chatbox;
	private final SecureRandom random = new SecureRandom();
	private final Set<String> seenChallenges = new LinkedHashSet<>();

	private boolean started;
	private OutgoingChallenge outgoing;
	private ResultView activeResult;

	@Inject
	TopTrumpsService(Client client, ClientThread clientThread, GroupmanTcgConfig config,
		SharedCollectionService collection, CardVisualCatalog cards, CardArtService cardArt,
		PartyService partyService, WSClient wsClient, ChatboxPanelManager chatbox)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.collection = collection;
		this.cards = cards;
		this.cardArt = cardArt;
		this.partyService = partyService;
		this.wsClient = wsClient;
		this.chatbox = chatbox;
	}

	void start()
	{
		started = true;
		wsClient.registerMessage(TopTrumpsChallengeMessage.class);
		wsClient.registerMessage(TopTrumpsResponseMessage.class);
		wsClient.registerMessage(TopTrumpsResultMessage.class);
	}

	synchronized void stop()
	{
		started = false;
		wsClient.unregisterMessage(TopTrumpsChallengeMessage.class);
		wsClient.unregisterMessage(TopTrumpsResponseMessage.class);
		wsClient.unregisterMessage(TopTrumpsResultMessage.class);
		seenChallenges.clear();
		outgoing = null;
		activeResult = null;
	}

	boolean canChallenge(String playerName)
	{
		if (!started || !config.topTrumpsEnabled() || collection.cards().size() < 2)
		{
			return false;
		}
		PartyMember target = collection.verifiedPartyMember(playerName);
		PartyMember local = partyService.getLocalMember();
		return target != null && local != null && target.getMemberId() != local.getMemberId();
	}

	synchronized void challenge(String playerName)
	{
		if (!canChallenge(playerName))
		{
			gameMessage("That player is not an online, verified group member.");
			return;
		}
		long now = System.currentTimeMillis();
		if (outgoing != null && outgoing.expiresAt > now)
		{
			gameMessage("Wait for your current Top Trumps challenge to finish.");
			return;
		}
		PartyMember target = collection.verifiedPartyMember(playerName);
		String groupKey = collection.activeGroupKey();
		if (target == null || groupKey == null)
		{
			gameMessage("Join the same RuneLite Party as your group member first.");
			return;
		}

		String challengeId = UUID.randomUUID().toString();
		long expiresAt = now + CHALLENGE_MILLIS;
		outgoing = new OutgoingChallenge(challengeId, target.getMemberId(), target.getDisplayName(), expiresAt);
		TopTrumpsChallengeMessage message = new TopTrumpsChallengeMessage();
		message.setProtocol(PROTOCOL);
		message.setGroupKey(groupKey);
		message.setChallengeId(challengeId);
		message.setTargetMemberId(target.getMemberId());
		message.setExpiresAt(expiresAt);
		partyService.send(message);
		gameMessage("Top Trumps challenge sent to " + safeName(target.getDisplayName()) + ".");
	}

	void challengeReceived(TopTrumpsChallengeMessage message)
	{
		clientThread.invokeLater(() -> acceptChallenge(message));
	}

	void responseReceived(TopTrumpsResponseMessage message)
	{
		clientThread.invokeLater(() -> acceptResponse(message));
	}

	void resultReceived(TopTrumpsResultMessage message)
	{
		clientThread.invokeLater(() -> acceptResult(message));
	}

	synchronized void onTick()
	{
		long now = System.currentTimeMillis();
		if (outgoing != null && outgoing.expiresAt < now)
		{
			gameMessage("Your Top Trumps challenge to " + safeName(outgoing.targetName) + " expired.");
			outgoing = null;
		}
		if (activeResult != null && activeResult.expiresAt < now)
		{
			activeResult = null;
		}
	}

	synchronized ResultView currentResult()
	{
		if (!config.topTrumpsEnabled())
		{
			activeResult = null;
			return null;
		}
		onTick();
		return activeResult;
	}

	private synchronized void acceptChallenge(TopTrumpsChallengeMessage message)
	{
		PartyMember local = partyService.getLocalMember();
		if (!started || !config.topTrumpsEnabled() || local == null || message == null
			|| message.getProtocol() != PROTOCOL || message.getTargetMemberId() != local.getMemberId()
			|| !validChallengeId(message.getChallengeId()) || !rememberChallenge(message.getChallengeId())
			|| message.getExpiresAt() < System.currentTimeMillis()
			|| message.getExpiresAt() > System.currentTimeMillis() + CHALLENGE_MILLIS + 5_000L)
		{
			return;
		}
		String challenger = collection.verifiedPartySenderName(message.getMemberId(), message.getGroupKey());
		if (challenger == null)
		{
			return;
		}

		chatbox.openTextMenuInput("Top Trumps challenge from " + safeName(challenger))
			.option("Accept", () -> respond(message, true))
			.option("Decline", () -> respond(message, false))
			.build();
		gameMessage(safeName(challenger) + " challenged you to Top Trumps.");
	}

	private synchronized void respond(TopTrumpsChallengeMessage challenge, boolean accepted)
	{
		if (challenge.getExpiresAt() < System.currentTimeMillis() || collection.activeGroupKey() == null)
		{
			gameMessage("That Top Trumps challenge has expired.");
			return;
		}
		TopTrumpsResponseMessage response = new TopTrumpsResponseMessage();
		response.setProtocol(PROTOCOL);
		response.setGroupKey(challenge.getGroupKey());
		response.setChallengeId(challenge.getChallengeId());
		response.setChallengerMemberId(challenge.getMemberId());
		response.setAccepted(accepted);
		partyService.send(response);
		gameMessage(accepted ? "Top Trumps challenge accepted." : "Top Trumps challenge declined.");
	}

	private synchronized void acceptResponse(TopTrumpsResponseMessage message)
	{
		PartyMember local = partyService.getLocalMember();
		if (!started || local == null || message == null || message.getProtocol() != PROTOCOL
			|| message.getChallengerMemberId() != local.getMemberId() || outgoing == null
			|| !outgoing.challengeId.equals(message.getChallengeId())
			|| outgoing.expiresAt < System.currentTimeMillis()
			|| outgoing.targetMemberId != message.getMemberId()
			|| collection.verifiedPartySenderName(message.getMemberId(), message.getGroupKey()) == null)
		{
			return;
		}
		OutgoingChallenge acceptedChallenge = outgoing;
		outgoing = null;
		if (!message.isAccepted())
		{
			gameMessage(safeName(acceptedChallenge.targetName) + " declined your Top Trumps challenge.");
			return;
		}

		TopTrumpsRules.Match match = TopTrumpsRules.draw(collection.cards(), cards, random,
			acceptedChallenge.challengeId);
		if (match == null)
		{
			gameMessage("The shared collection needs at least two valid cards for Top Trumps.");
			return;
		}
		PartyMember target = partyService.getMemberById(acceptedChallenge.targetMemberId);
		if (target == null)
		{
			gameMessage("The challenged player left the RuneLite Party.");
			return;
		}

		TopTrumpsResultMessage result = new TopTrumpsResultMessage();
		result.setProtocol(PROTOCOL);
		result.setGroupKey(collection.activeGroupKey());
		result.setChallengeId(acceptedChallenge.challengeId);
		result.setChallengerMemberId(local.getMemberId());
		result.setChallengedMemberId(target.getMemberId());
		result.setChallengerCard(match.challengerCard().displayName());
		result.setChallengedCard(match.challengedCard().displayName());
		partyService.send(result);
		showResult(result, local.getDisplayName(), target.getDisplayName());
	}

	private synchronized void acceptResult(TopTrumpsResultMessage message)
	{
		PartyMember local = partyService.getLocalMember();
		if (!started || !config.topTrumpsEnabled() || local == null || message == null
			|| message.getProtocol() != PROTOCOL || !validChallengeId(message.getChallengeId())
			|| message.getMemberId() != message.getChallengerMemberId()
			|| (local.getMemberId() != message.getChallengerMemberId()
				&& local.getMemberId() != message.getChallengedMemberId())
			|| collection.verifiedPartySenderName(message.getMemberId(), message.getGroupKey()) == null)
		{
			return;
		}
		PartyMember challenger = partyService.getMemberById(message.getChallengerMemberId());
		PartyMember challenged = partyService.getMemberById(message.getChallengedMemberId());
		if (challenger == null || challenged == null)
		{
			return;
		}
		showResult(message, challenger.getDisplayName(), challenged.getDisplayName());
	}

	private void showResult(TopTrumpsResultMessage result, String challengerName, String challengedName)
	{
		if (activeResult != null && activeResult.challengeId().equals(result.getChallengeId()))
		{
			return;
		}
		CardVisualCatalog.CardVisual challengerCard = cards.find(result.getChallengerCard());
		CardVisualCatalog.CardVisual challengedCard = cards.find(result.getChallengedCard());
		if (challengerCard == null || challengedCard == null
			|| !collection.cards().contains(EntityCardCatalog.normalize(challengerCard.displayName()))
			|| !collection.cards().contains(EntityCardCatalog.normalize(challengedCard.displayName())))
		{
			return;
		}
		int winner = TopTrumpsRules.winner(challengerCard.score(), challengedCard.score(),
			result.getChallengeId(), challengerCard.displayName(), challengedCard.displayName());
		boolean tieBreak = Double.compare(challengerCard.score(), challengedCard.score()) == 0;
		long duration = Math.max(5, Math.min(20, config.topTrumpsDuration())) * 1_000L;
		activeResult = new ResultView(result.getChallengeId(), safeName(challengerName), safeName(challengedName),
			challengerCard, challengedCard, winner, tieBreak, System.currentTimeMillis() + duration);
		cardArt.preload(Arrays.asList(challengerCard.displayName(), challengedCard.displayName()));
		gameMessage(activeResult.winnerName() + " wins Top Trumps" + (tieBreak ? " on the tie-break" : "") + "!");
	}

	private boolean rememberChallenge(String challengeId)
	{
		if (!seenChallenges.add(challengeId))
		{
			return false;
		}
		while (seenChallenges.size() > MAX_SEEN_CHALLENGES)
		{
			seenChallenges.remove(seenChallenges.iterator().next());
		}
		return true;
	}

	private void gameMessage(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[Groupman TCG] " + message, null);
	}

	private static boolean validChallengeId(String value)
	{
		return value != null && value.length() >= 8 && value.length() <= 64;
	}

	private static String safeName(String value)
	{
		if (value == null)
		{
			return "Group member";
		}
		String clean = value.trim();
		return clean.isEmpty() ? "Group member" : clean.substring(0, Math.min(12, clean.length()));
	}

	static final class ResultView
	{
		private final String challengeId;
		private final String challengerName;
		private final String challengedName;
		private final CardVisualCatalog.CardVisual challengerCard;
		private final CardVisualCatalog.CardVisual challengedCard;
		private final int winner;
		private final boolean tieBreak;
		private final long expiresAt;

		private ResultView(String challengeId, String challengerName, String challengedName,
			CardVisualCatalog.CardVisual challengerCard, CardVisualCatalog.CardVisual challengedCard,
			int winner, boolean tieBreak, long expiresAt)
		{
			this.challengeId = challengeId;
			this.challengerName = challengerName;
			this.challengedName = challengedName;
			this.challengerCard = challengerCard;
			this.challengedCard = challengedCard;
			this.winner = winner;
			this.tieBreak = tieBreak;
			this.expiresAt = expiresAt;
		}

		String challengeId() { return challengeId; }
		String challengerName() { return challengerName; }
		String challengedName() { return challengedName; }
		CardVisualCatalog.CardVisual challengerCard() { return challengerCard; }
		CardVisualCatalog.CardVisual challengedCard() { return challengedCard; }
		int winner() { return winner; }
		boolean tieBreak() { return tieBreak; }
		long expiresAt() { return expiresAt; }
		String winnerName() { return winner == 0 ? challengerName : challengedName; }
	}

	private static final class OutgoingChallenge
	{
		private final String challengeId;
		private final long targetMemberId;
		private final String targetName;
		private final long expiresAt;

		private OutgoingChallenge(String challengeId, long targetMemberId, String targetName, long expiresAt)
		{
			this.challengeId = challengeId;
			this.targetMemberId = targetMemberId;
			this.targetName = targetName;
			this.expiresAt = expiresAt;
		}
	}
}
