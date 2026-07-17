package com.groupmantcg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.ClanID;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;

/** Owns the effective collection and the grow-only official-GIM union. */
@Slf4j
@Singleton
class SharedCollectionService
{
	private static final int PROTOCOL = 3;
	private static final String CACHE_KEY = "sharedCollectionV1";
	private static final int REFRESH_TICKS = 5;
	private static final int REANNOUNCE_TICKS = 100;
	private static final DateTimeFormatter PULL_DATE = DateTimeFormatter.ofPattern("d MMM uuuu")
		.withZone(ZoneId.systemDefault());

	private final Client client;
	private final ClientThread clientThread;
	private final ConfigManager configManager;
	private final GroupmanTcgConfig config;
	private final LocalCollection localCollection;
	private final CardBitsetIndex index;
	private final GroupCacheCodec cacheCodec;
	private final PartyService partyService;
	private final WSClient wsClient;

	private volatile Set<String> sharedCards = Collections.emptySet();
	private volatile Map<String, MemberCollection> memberCollections = Collections.emptyMap();
	private volatile GroupSyncStatus status = soloStatus(0);
	private Set<String> roster = Collections.emptySet();
	private final Set<String> syncedMembers = new HashSet<>();
	private final Set<String> hostedMembers = new HashSet<>();
	private String groupKey;
	private String groupName;
	private String lastSentBits;
	private long lastPartyId = -1L;
	private int ticks;
	private boolean started;

	@Inject
	SharedCollectionService(Client client, ClientThread clientThread, ConfigManager configManager,
		GroupmanTcgConfig config, LocalCollection localCollection, CardBitsetIndex index,
		GroupCacheCodec cacheCodec, PartyService partyService, WSClient wsClient)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.configManager = configManager;
		this.config = config;
		this.localCollection = localCollection;
		this.index = index;
		this.cacheCodec = cacheCodec;
		this.partyService = partyService;
		this.wsClient = wsClient;
	}

	void start()
	{
		started = true;
		wsClient.registerMessage(GroupCollectionSnapshotMessage.class);
		requestRefresh(true);
	}

	void stop()
	{
		started = false;
		wsClient.unregisterMessage(GroupCollectionSnapshotMessage.class);
		reset();
	}

	Set<String> cards()
	{
		return status.isActive() ? sharedCards : localCollection.getCards();
	}

	GroupSyncStatus status()
	{
		return status;
	}

	Map<String, Set<String>> memberCollections()
	{
		List<MemberCollection> sorted = new ArrayList<>(memberCollections.values());
		sorted.sort(Comparator.comparing(member -> member.displayName, String.CASE_INSENSITIVE_ORDER));
		Map<String, Set<String>> result = new LinkedHashMap<>();
		for (MemberCollection member : sorted)
		{
			result.put(member.displayName, member.cards);
		}
		return Collections.unmodifiableMap(result);
	}

	List<String> ownersOf(Set<String> cardNames)
	{
		List<String> owners = new ArrayList<>();
		for (MemberCollection member : memberCollections.values())
		{
			for (String cardName : cardNames)
			{
				if (member.cards.contains(cardName))
				{
					owners.add(member.displayName);
					break;
				}
			}
		}
		owners.sort(String.CASE_INSENSITIVE_ORDER);
		return owners;
	}

	List<String> ownershipDetails(Set<String> cardNames)
	{
		List<String> details = new ArrayList<>();
		PartyMember localMember = partyService.getLocalMember();
		String localName = localMember == null || localMember.getDisplayName() == null
			? null : EntityCardCatalog.normalize(localMember.getDisplayName());
		for (String owner : ownersOf(cardNames))
		{
			String ownerKey = EntityCardCatalog.normalize(owner);
			if (localName != null && localName.equals(ownerKey))
			{
				LocalCollection.CardSummary summary = null;
				for (String cardName : cardNames)
				{
					LocalCollection.CardSummary candidate = localCollection.summary(cardName);
					if (candidate != null && (summary == null || candidate.copies() > summary.copies()))
					{
						summary = candidate;
					}
				}
				if (summary != null)
				{
					details.add(describe(owner, summary.copies(), summary.foilCopies(), summary.debugCopies(),
						summary.pulledBy(), summary.firstPulledAt()));
					continue;
				}
			}

			MemberCollection member = memberCollections.get(ownerKey);
			HostedCollectionSnapshot.CardDetails hosted = null;
			if (member != null)
			{
				for (String cardName : cardNames)
				{
					HostedCollectionSnapshot.CardDetails candidate = member.details.get(cardName);
					if (candidate != null && (hosted == null || candidate.copies() > hosted.copies()))
					{
						hosted = candidate;
					}
				}
			}
			if (hosted != null)
			{
				details.add(describe(owner, hosted.copies(), hosted.foilCopies(), hosted.debugCopies(),
					Collections.emptySet(), hosted.firstPulledAt()));
			}
			else
			{
				details.add(owner);
			}
		}
		return details;
	}

	private static String describe(String owner, int copies, int foils, int debug,
		Set<String> pulledBy, long firstPulledAt)
	{
		StringBuilder detail = new StringBuilder(owner).append(": ").append(copies)
			.append(copies == 1 ? " copy" : " copies");
		if (foils > 0)
		{
			detail.append(", ").append(foils).append(" foil");
		}
		if (debug > 0)
		{
			detail.append(", ").append(debug).append(" debug grant").append(debug == 1 ? "" : "s");
		}
		if (!pulledBy.isEmpty())
		{
			detail.append(", pulled by ").append(String.join(" / ", pulledBy));
		}
		if (firstPulledAt > 0L)
		{
			detail.append(" on ").append(PULL_DATE.format(Instant.ofEpochMilli(firstPulledAt)));
		}
		return detail.toString();
	}

	String activeGroupKey()
	{
		return started && status.isActive() && partyService.isInParty() ? groupKey : null;
	}

	String verifiedPartySenderName(long memberId, String incomingGroupKey)
	{
		if (!started || !status.isActive() || !partyService.isInParty() || groupKey == null
			|| !groupKey.equals(incomingGroupKey))
		{
			return null;
		}
		PartyMember sender = partyService.getMemberById(memberId);
		if (sender == null || sender.getDisplayName() == null
			|| !roster.contains(EntityCardCatalog.normalize(sender.getDisplayName())))
		{
			return null;
		}
		return sender.getDisplayName().trim();
	}

	PartyMember verifiedPartyMember(String displayName)
	{
		if (!started || !status.isActive() || !partyService.isInParty() || displayName == null)
		{
			return null;
		}
		PartyMember member = partyService.getMemberByDisplayName(displayName);
		if (member == null || member.getDisplayName() == null
			|| !roster.contains(EntityCardCatalog.normalize(member.getDisplayName())))
		{
			return null;
		}
		return member;
	}

	void onTick()
	{
		if (started && ++ticks % REFRESH_TICKS == 0)
		{
			refresh(ticks % REANNOUNCE_TICKS == 0);
		}
	}

	void localCollectionChanged()
	{
		localCollection.invalidate();
		requestRefresh(true);
	}

	void contextChanged()
	{
		requestRefresh(true);
	}

	void profileChanged()
	{
		localCollection.invalidate();
		clientThread.invokeLater(() ->
		{
			reset();
			refresh(true);
		});
	}

	void partyChanged()
	{
		clientThread.invokeLater(() ->
		{
			syncedMembers.clear();
			lastPartyId = -1L;
			refresh(true);
		});
	}

	void snapshotReceived(GroupCollectionSnapshotMessage message)
	{
		clientThread.invokeLater(() -> merge(message));
	}

	void hostedSnapshotReceived(HostedCollectionSnapshot snapshot)
	{
		if (!started || snapshot == null || !status.isActive())
		{
			return;
		}
		boolean changed = false;
		Set<String> incomingHostedMembers = new HashSet<>();
		for (Map.Entry<String, Map<String, HostedCollectionSnapshot.CardDetails>> entry
			: snapshot.members().entrySet())
		{
			if (entry.getKey() == null || entry.getKey().trim().isEmpty())
			{
				continue;
			}
			String memberKey = EntityCardCatalog.normalize(entry.getKey());
			incomingHostedMembers.add(memberKey);
			Set<String> cards = new HashSet<>(entry.getValue().keySet());
			changed |= setMemberCollection(entry.getKey(), cards, entry.getValue());
		}
		// An empty member map accompanies an unlock-only delta. A non-empty map is an authoritative
		// refresh and lets us remove personal views for members who were revoked from hosted sync.
		if (!snapshot.members().isEmpty())
		{
			Set<String> departed = new HashSet<>(hostedMembers);
			departed.removeAll(incomingHostedMembers);
			if (!departed.isEmpty())
			{
				Map<String, MemberCollection> updated = new HashMap<>(memberCollections);
				for (String memberKey : departed)
				{
					changed |= updated.remove(memberKey) != null;
				}
				memberCollections = Collections.unmodifiableMap(updated);
			}
			hostedMembers.clear();
			hostedMembers.addAll(incomingHostedMembers);
		}
		Set<String> combined = new HashSet<>(sharedCards);
		combined.addAll(snapshot.unlocks());
		for (MemberCollection member : memberCollections.values())
		{
			combined.addAll(member.cards);
		}
		if (!combined.equals(sharedCards))
		{
			setSharedCards(combined);
			changed = true;
		}
		if (changed)
		{
			persist();
			updateActiveStatus();
			send(true);
		}
	}

	private void requestRefresh(boolean forceSend)
	{
		clientThread.invokeLater(() -> refresh(forceSend));
	}

	private void refresh(boolean forceSend)
	{
		Set<String> local = localCollection.getCards();
		if (!started || config.collectionMode() != CollectionMode.GROUP_IRONMAN)
		{
			reset();
			status = soloStatus(local.size());
			return;
		}

		if (client.getGameState() != GameState.LOGGED_IN || client.getAccountType() == null
			|| !client.getAccountType().isGroupIronman())
		{
			reset();
			status = inactiveStatus(local.size(), "Log into a Group Ironman account");
			return;
		}

		ClanSettings settings = client.getClanSettings(ClanID.GROUP_IRONMAN);
		if (settings == null || settings.getName() == null || settings.getMembers() == null)
		{
			reset();
			status = inactiveStatus(local.size(), "Waiting for the official GIM roster");
			return;
		}

		String discoveredKey = PrivacyIdentifiers.groupKey(settings.getName());
		if (!discoveredKey.equals(groupKey))
		{
			groupKey = discoveredKey;
			groupName = settings.getName().trim();
			LoadedCache loaded = loadCache();
			sharedCards = loaded.sharedCards;
			memberCollections = loaded.memberCollections;
			syncedMembers.clear();
			hostedMembers.clear();
			lastSentBits = null;
			lastPartyId = -1L;
		}

		Set<String> discoveredRoster = new HashSet<>();
		for (ClanMember member : settings.getMembers())
		{
			if (member != null && member.getName() != null)
			{
				discoveredRoster.add(EntityCardCatalog.normalize(member.getName()));
			}
		}
		roster = Collections.unmodifiableSet(discoveredRoster);
		syncedMembers.retainAll(roster);
		retainRosterMembers();

		PartyMember localPartyMember = partyService.getLocalMember();
		boolean localMemberChanged = false;
		if (localPartyMember != null && localPartyMember.getDisplayName() != null)
		{
			String localName = EntityCardCatalog.normalize(localPartyMember.getDisplayName());
			if (roster.contains(localName))
			{
				syncedMembers.add(localName);
				localMemberChanged = setMemberCollection(localPartyMember.getDisplayName(), local);
			}
		}

		Set<String> combined = new HashSet<>(sharedCards);
		boolean grew = combined.addAll(local);
		if (grew)
		{
			setSharedCards(combined);
		}
		if (grew || localMemberChanged)
		{
			persist();
		}
		updateActiveStatus();
		send(forceSend || grew || localMemberChanged);
	}

	private void merge(GroupCollectionSnapshotMessage message)
	{
		if (!started || !status.isActive() || !partyService.isInParty() || message == null
			|| message.getProtocol() < 1 || message.getProtocol() > PROTOCOL
			|| !groupKey.equals(message.getGroupKey())
			|| !index.fingerprint().equals(message.getCatalogFingerprint()))
		{
			return;
		}

		PartyMember sender = partyService.getMemberById(message.getMemberId());
		if (sender == null || sender.getDisplayName() == null)
		{
			return;
		}
		String senderName = EntityCardCatalog.normalize(sender.getDisplayName());
		if (!roster.contains(senderName))
		{
			log.debug("Rejected group snapshot from a non-roster party member");
			return;
		}

		final Set<String> incoming;
		try
		{
			incoming = index.decode(message.getUnlockBits());
		}
		catch (IllegalArgumentException ex)
		{
			log.debug("Rejected malformed group snapshot", ex);
			return;
		}

		boolean memberChanged = false;
		if (message.getProtocol() >= 2 && message.getMemberUnlockBits() != null)
		{
			try
			{
				memberChanged = setMemberCollection(sender.getDisplayName(), index.decode(message.getMemberUnlockBits()));
			}
			catch (IllegalArgumentException ex)
			{
				log.debug("Rejected malformed member collection snapshot", ex);
				return;
			}
		}
		syncedMembers.add(senderName);
		Set<String> combined = new HashSet<>(sharedCards);
		int previous = combined.size();
		boolean grew = combined.addAll(incoming);
		if (grew)
		{
			setSharedCards(combined);
		}
		if (grew || memberChanged)
		{
			persist();
		}
		updateActiveStatus();
		if (grew)
		{
			send(true);
			if (config.syncMessages())
			{
				int added = combined.size() - previous;
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"[Groupman TCG] The group gained " + added + " unlock" + (added == 1 ? "" : "s") + ".", null);
			}
		}
	}

	private LoadedCache loadCache()
	{
		try
		{
			String json = configManager.getRSProfileConfiguration(GroupmanTcgConfig.GROUP, CACHE_KEY);
			GroupCacheCodec.Cache cache = cacheCodec.decode(json);
			if (cache == null || !groupKey.equals(cache.groupKey)
				|| !index.fingerprint().equals(cache.catalogFingerprint))
			{
				return LoadedCache.empty();
			}
			Map<String, MemberCollection> members = new HashMap<>();
			for (Map.Entry<String, String> entry : cache.memberUnlockBits.entrySet())
			{
				if (entry.getKey() == null || entry.getValue() == null)
				{
					continue;
				}
				String displayName = entry.getKey().trim();
				if (!displayName.isEmpty())
				{
					members.put(EntityCardCatalog.normalize(displayName),
						new MemberCollection(displayName, index.decode(entry.getValue())));
				}
			}
			return new LoadedCache(index.decode(cache.unlockBits), members);
		}
		catch (Exception ex)
		{
			log.debug("Unable to load shared collection cache", ex);
			return LoadedCache.empty();
		}
	}

	private void persist()
	{
		String bits = index.encode(sharedCards);
		Map<String, String> memberBits = new LinkedHashMap<>();
		for (MemberCollection member : memberCollections.values())
		{
			memberBits.put(member.displayName, index.encode(member.cards));
		}
		configManager.setRSProfileConfiguration(GroupmanTcgConfig.GROUP, CACHE_KEY,
			cacheCodec.encode(groupKey, index.fingerprint(), bits, memberBits));
	}

	private void send(boolean force)
	{
		if (!status.isActive() || !partyService.isInParty())
		{
			return;
		}
		String bits = index.encode(sharedCards);
		long partyId = partyService.getPartyId();
		if (!force && partyId == lastPartyId && bits.equals(lastSentBits))
		{
			return;
		}
		GroupCollectionSnapshotMessage message = new GroupCollectionSnapshotMessage();
		message.setProtocol(PROTOCOL);
		message.setGroupKey(groupKey);
		message.setCatalogFingerprint(index.fingerprint());
		message.setUnlockBits(bits);
		message.setMemberUnlockBits(index.encode(localCollection.getCards()));
		partyService.send(message);
		lastSentBits = bits;
		lastPartyId = partyId;
	}

	private void updateActiveStatus()
	{
		boolean inParty = partyService.isInParty();
		status = new GroupSyncStatus(true, true, inParty, groupName, sharedCards.size(),
			syncedMembers.size(), roster.size(), inParty ? "Live Party sync" : "Using offline cache; join RuneLite Party to sync");
	}

	private void setSharedCards(Set<String> cards)
	{
		sharedCards = Collections.unmodifiableSet(new HashSet<>(cards));
	}

	private boolean setMemberCollection(String displayName, Set<String> cards)
	{
		return setMemberCollection(displayName, cards, null);
	}

	private boolean setMemberCollection(String displayName, Set<String> cards,
		Map<String, HostedCollectionSnapshot.CardDetails> hostedDetails)
	{
		if (displayName == null || displayName.trim().isEmpty())
		{
			return false;
		}
		String shownName = displayName.trim();
		String key = EntityCardCatalog.normalize(shownName);
		MemberCollection current = memberCollections.get(key);
		Map<String, HostedCollectionSnapshot.CardDetails> details = hostedDetails;
		if (details == null)
		{
			details = new HashMap<>();
			if (current != null)
			{
				for (Map.Entry<String, HostedCollectionSnapshot.CardDetails> entry : current.details.entrySet())
				{
					if (cards.contains(entry.getKey()))
					{
						details.put(entry.getKey(), entry.getValue());
					}
				}
			}
		}
		if (current != null && current.displayName.equals(shownName) && current.cards.equals(cards)
			&& current.details.equals(details))
		{
			return false;
		}
		Map<String, MemberCollection> updated = new HashMap<>(memberCollections);
		updated.put(key, new MemberCollection(shownName, cards, details));
		memberCollections = Collections.unmodifiableMap(updated);
		return true;
	}

	private void retainRosterMembers()
	{
		Map<String, MemberCollection> retained = new HashMap<>();
		for (Map.Entry<String, MemberCollection> entry : memberCollections.entrySet())
		{
			if (roster.contains(entry.getKey()) || hostedMembers.contains(entry.getKey()))
			{
				retained.put(entry.getKey(), entry.getValue());
			}
		}
		memberCollections = Collections.unmodifiableMap(retained);
	}

	private void reset()
	{
		sharedCards = Collections.emptySet();
		memberCollections = Collections.emptyMap();
		roster = Collections.emptySet();
		syncedMembers.clear();
		hostedMembers.clear();
		groupKey = null;
		groupName = null;
		lastSentBits = null;
		lastPartyId = -1L;
	}

	private GroupSyncStatus inactiveStatus(int cards, String detail)
	{
		return new GroupSyncStatus(true, false, partyService.isInParty(), "", cards, 0, 0, detail);
	}

	private static GroupSyncStatus soloStatus(int cards)
	{
		return new GroupSyncStatus(false, false, false, "", cards, 0, 0, "Solo OSRS TCG collection");
	}

	private static final class MemberCollection
	{
		private final String displayName;
		private final Set<String> cards;
		private final Map<String, HostedCollectionSnapshot.CardDetails> details;

		private MemberCollection(String displayName, Set<String> cards)
		{
			this(displayName, cards, Collections.emptyMap());
		}

		private MemberCollection(String displayName, Set<String> cards,
			Map<String, HostedCollectionSnapshot.CardDetails> details)
		{
			this.displayName = displayName;
			this.cards = Collections.unmodifiableSet(new HashSet<>(cards));
			this.details = Collections.unmodifiableMap(new HashMap<>(details));
		}
	}

	private static final class LoadedCache
	{
		private final Set<String> sharedCards;
		private final Map<String, MemberCollection> memberCollections;

		private LoadedCache(Set<String> sharedCards, Map<String, MemberCollection> memberCollections)
		{
			this.sharedCards = Collections.unmodifiableSet(new HashSet<>(sharedCards));
			this.memberCollections = Collections.unmodifiableMap(new HashMap<>(memberCollections));
		}

		private static LoadedCache empty()
		{
			return new LoadedCache(Collections.emptySet(), Collections.emptyMap());
		}
	}
}
