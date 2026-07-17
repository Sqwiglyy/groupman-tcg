package com.groupmantcg;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.chatbox.ChatboxPanelManager;

/** Handles Top Trumps challenges and results between server members. */
@Singleton
class TopTrumpsService
{
	static final String MENU_OPTION = "Top Trumps";
	private static final int MAX_SEEN_EVENTS = 128;
	private static final long MAX_EVENT_AGE_MILLIS = 120_000L;

	private final Client client;
	private final ClientThread clientThread;
	private final GroupmanTcgConfig config;
	private final HostedSyncService hosted;
	private final CardVisualCatalog cards;
	private final CardArtService cardArt;
	private final ChatboxPanelManager chatbox;
	private final Set<String> seenEvents = new LinkedHashSet<>();

	private boolean started;
	private ResultView activeResult;

	@Inject
	TopTrumpsService(Client client, ClientThread clientThread, GroupmanTcgConfig config,
		HostedSyncService hosted, CardVisualCatalog cards, CardArtService cardArt,
		ChatboxPanelManager chatbox)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.hosted = hosted;
		this.cards = cards;
		this.cardArt = cardArt;
		this.chatbox = chatbox;
	}

	void start()
	{
		started = true;
	}

	synchronized void stop()
	{
		started = false;
		seenEvents.clear();
		activeResult = null;
	}

	boolean canChallenge(String playerName)
	{
		if (!started || !config.topTrumpsEnabled())
		{
			return false;
		}
		HostedSyncStatus.Member member = hosted.memberByPlayerName(playerName);
		return member != null && hosted.canChallenge(member.id());
	}

	void challenge(String playerName)
	{
		HostedSyncStatus.Member member = hosted.memberByPlayerName(playerName);
		if (member == null)
		{
			gameMessage("That player is not an approved member of your Group TCG server.");
			return;
		}
		challengeMember(member);
	}

	void challengeMember(HostedSyncStatus.Member member)
	{
		if (!started || !config.topTrumpsEnabled() || member == null || !hosted.canChallenge(member.id()))
		{
			gameMessage("That player is not available for a server Top Trumps challenge.");
			return;
		}
		hosted.challengeMember(member.id(), error -> clientThread.invokeLater(() ->
		{
			if (error == null)
			{
				gameMessage("Top Trumps challenge sent to " + displayName(member) + ".");
			}
			else
			{
				gameMessage(error);
			}
		}));
	}

	synchronized void hostedEvent(HostedApiClient.TopTrumpsEvent event)
	{
		if (!started || !config.topTrumpsEnabled() || event == null || event.challengeId == null
			|| event.type == null || event.challenger == null || event.challenged == null
			|| event.createdAt < System.currentTimeMillis() - MAX_EVENT_AGE_MILLIS
			|| !rememberEvent(event.type + ':' + event.challengeId))
		{
			return;
		}
		String localMemberId = hosted.status().memberId();
		if (localMemberId.isEmpty())
		{
			return;
		}
		switch (event.type)
		{
			case "challenge":
				if (localMemberId.equals(event.challenged.id)
					&& event.expiresAt >= System.currentTimeMillis())
				{
					showChallenge(event);
				}
				break;
			case "declined":
				if (localMemberId.equals(event.challenger.id))
				{
					gameMessage(displayName(event.challenged) + " declined your Top Trumps challenge.");
				}
				break;
			case "result":
				if (localMemberId.equals(event.challenger.id) || localMemberId.equals(event.challenged.id))
				{
					showResult(event);
				}
				break;
			default:
				break;
		}
	}

	private void showChallenge(HostedApiClient.TopTrumpsEvent event)
	{
		String challenger = displayName(event.challenger);
		chatbox.openTextMenuInput("Top Trumps challenge from " + challenger)
			.option("Accept", () -> respond(event, true))
			.option("Decline", () -> respond(event, false))
			.build();
		gameMessage(challenger + " challenged you to Top Trumps.");
	}

	private void respond(HostedApiClient.TopTrumpsEvent event, boolean accepted)
	{
		if (event.expiresAt < System.currentTimeMillis())
		{
			gameMessage("That Top Trumps challenge has expired.");
			return;
		}
		hosted.respondTopTrumps(event.challengeId, accepted, error -> clientThread.invokeLater(() ->
		{
			if (error == null)
			{
				gameMessage(accepted ? "Top Trumps challenge accepted." : "Top Trumps challenge declined.");
			}
			else
			{
				gameMessage(error);
			}
		}));
	}

	private void showResult(HostedApiClient.TopTrumpsEvent event)
	{
		if (event.challengerCard == null || event.challengedCard == null)
		{
			return;
		}
		CardVisualCatalog.CardVisual challengerCard = cards.find(event.challengerCard);
		CardVisualCatalog.CardVisual challengedCard = cards.find(event.challengedCard);
		if (challengerCard == null || challengedCard == null)
		{
			return;
		}
		int winner = TopTrumpsRules.winner(challengerCard.score(), challengedCard.score(),
			event.challengeId, challengerCard.displayName(), challengedCard.displayName());
		boolean tieBreak = Double.compare(challengerCard.score(), challengedCard.score()) == 0;
		long duration = Math.max(5, Math.min(20, config.topTrumpsDuration())) * 1_000L;
		activeResult = new ResultView(event.challengeId, displayName(event.challenger),
			displayName(event.challenged), challengerCard, challengedCard, winner, tieBreak,
			System.currentTimeMillis() + duration);
		cardArt.preload(Arrays.asList(challengerCard.displayName(), challengedCard.displayName()));
		gameMessage(activeResult.winnerName() + " wins Top Trumps" + (tieBreak ? " on the tie-break" : "") + "!");
	}

	synchronized void onTick()
	{
		if (activeResult != null && activeResult.expiresAt < System.currentTimeMillis())
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

	private boolean rememberEvent(String eventKey)
	{
		if (!seenEvents.add(eventKey))
		{
			return false;
		}
		while (seenEvents.size() > MAX_SEEN_EVENTS)
		{
			seenEvents.remove(seenEvents.iterator().next());
		}
		return true;
	}

	private void gameMessage(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[Group TCG] " + message, null);
	}

	private static String displayName(HostedSyncStatus.Member member)
	{
		return safeName(member.playerName().isEmpty() ? member.label() : member.playerName());
	}

	private static String displayName(HostedApiClient.MemberRef member)
	{
		return safeName(member.playerName == null || member.playerName.trim().isEmpty()
			? member.label : member.playerName);
	}

	private static String safeName(String value)
	{
		if (value == null)
		{
			return "Server member";
		}
		String clean = value.trim();
		return clean.isEmpty() ? "Server member" : clean.substring(0, Math.min(16, clean.length()));
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
}
