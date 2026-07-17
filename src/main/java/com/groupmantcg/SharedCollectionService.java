package com.groupmantcg;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

/** Owns the effective solo collection or the grow-only collection of an approved private server. */
@Slf4j
@Singleton
class SharedCollectionService
{
	private static final String CACHE_KEY = "sharedCollectionV1";
	private static final int REFRESH_TICKS = 5;
	private static final DateTimeFormatter PULL_DATE = DateTimeFormatter.ofPattern("d MMM uuuu")
		.withZone(ZoneId.systemDefault());

	private final Client client;
	private final ClientThread clientThread;
	private final ConfigManager configManager;
	private final GroupmanTcgConfig config;
	private final LocalCollection localCollection;
	private final CardBitsetIndex index;
	private final GroupCacheCodec cacheCodec;

	private volatile Set<String> sharedCards = Collections.emptySet();
	private volatile Map<String, MemberCollection> memberCollections = Collections.emptyMap();
	private volatile GroupSyncStatus status = soloStatus(0);
	private String groupId;
	private String groupName;
	private String localPlayerName;
	private boolean approved;
	private int ticks;
	private boolean started;

	@Inject
	SharedCollectionService(Client client, ClientThread clientThread, ConfigManager configManager,
		GroupmanTcgConfig config, LocalCollection localCollection, CardBitsetIndex index,
		GroupCacheCodec cacheCodec)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.configManager = configManager;
		this.config = config;
		this.localCollection = localCollection;
		this.index = index;
		this.cacheCodec = cacheCodec;
	}

	void start()
	{
		started = true;
		requestRefresh();
	}

	void stop()
	{
		started = false;
		clearServerState();
		status = soloStatus(localCollection.getCards().size());
	}

	Set<String> cards()
	{
		return config.collectionMode() == CollectionMode.GROUP_IRONMAN && status.isActive()
			? sharedCards : localCollection.getCards();
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

	CollectionAlbumSnapshot collectionAlbum(String collectionKey, String displayName)
	{
		String requestedKey = collectionKey == null ? "" : EntityCardCatalog.normalize(collectionKey);
		String localKey = localPlayerName == null ? "" : EntityCardCatalog.normalize(localPlayerName);
		if (!requestedKey.isEmpty())
		{
			if (requestedKey.equals(localKey))
			{
				return localAlbum(displayName);
			}
			MemberCollection member = memberCollections.get(requestedKey);
			if (member == null)
			{
				return new CollectionAlbumSnapshot(displayName, Collections.emptySet(), Collections.emptyMap());
			}
			Map<String, CollectionAlbumSnapshot.Ownership> ownership = new HashMap<>();
			addHostedOwnership(ownership, member);
			return new CollectionAlbumSnapshot(member.displayName, member.cards, ownership);
		}

		if (config.collectionMode() != CollectionMode.GROUP_IRONMAN || !status.isActive())
		{
			return localAlbum(displayName);
		}

		Map<String, CollectionAlbumSnapshot.Ownership> ownership = new HashMap<>();
		for (Map.Entry<String, MemberCollection> entry : memberCollections.entrySet())
		{
			if (!entry.getKey().equals(localKey))
			{
				addHostedOwnership(ownership, entry.getValue());
			}
		}
		addLocalOwnership(ownership);
		return new CollectionAlbumSnapshot(displayName, sharedCards, ownership);
	}

	private CollectionAlbumSnapshot localAlbum(String displayName)
	{
		Map<String, CollectionAlbumSnapshot.Ownership> ownership = new HashMap<>();
		addLocalOwnership(ownership);
		return new CollectionAlbumSnapshot(displayName, localCollection.getCards(), ownership);
	}

	private void addLocalOwnership(Map<String, CollectionAlbumSnapshot.Ownership> destination)
	{
		for (LocalCollection.CardInstance instance : localCollection.snapshot().instances())
		{
			CollectionAlbumSnapshot.Ownership copy = instance.foil()
				? new CollectionAlbumSnapshot.Ownership(0, 1)
				: new CollectionAlbumSnapshot.Ownership(1, 0);
			destination.merge(instance.normalizedName(), copy,
				CollectionAlbumSnapshot.Ownership::plus);
		}
	}

	private static void addHostedOwnership(
		Map<String, CollectionAlbumSnapshot.Ownership> destination, MemberCollection member)
	{
		for (String cardName : member.cards)
		{
			String normalized = EntityCardCatalog.normalize(cardName);
			HostedCollectionSnapshot.CardDetails detail = member.details.get(normalized);
			CollectionAlbumSnapshot.Ownership copies;
			if (detail == null || detail.copies() <= 0)
			{
				copies = new CollectionAlbumSnapshot.Ownership(1, 0);
			}
			else
			{
				int foils = Math.max(0, Math.min(detail.foilCopies(), detail.copies()));
				copies = new CollectionAlbumSnapshot.Ownership(detail.copies() - foils, foils);
			}
			destination.merge(normalized, copies, CollectionAlbumSnapshot.Ownership::plus);
		}
	}

	List<HostedCollectionSnapshot.RecentCard> recentCards(String collectionKey, int limit)
	{
		String requestedKey = collectionKey == null ? "" : EntityCardCatalog.normalize(collectionKey);
		String localKey = localPlayerName == null ? "" : EntityCardCatalog.normalize(localPlayerName);
		if (!requestedKey.isEmpty())
		{
			if (requestedKey.equals(localKey))
			{
				return recentLocalCards(localPlayerName, limit);
			}
			MemberCollection member = memberCollections.get(requestedKey);
			return member == null ? Collections.emptyList()
				: HostedCollectionSnapshot.newestCards(member.recentCards, limit);
		}

		if (config.collectionMode() != CollectionMode.GROUP_IRONMAN || !status.isActive())
		{
			return recentLocalCards(localPlayerName, limit);
		}

		List<HostedCollectionSnapshot.RecentCard> combined = new ArrayList<>();
		for (Map.Entry<String, MemberCollection> entry : memberCollections.entrySet())
		{
			if (!entry.getKey().equals(localKey))
			{
				combined.addAll(entry.getValue().recentCards);
			}
		}
		combined.addAll(recentLocalCards(localPlayerName, limit));
		return HostedCollectionSnapshot.newestCards(combined, limit);
	}

	private List<HostedCollectionSnapshot.RecentCard> recentLocalCards(String owner, int limit)
	{
		List<HostedCollectionSnapshot.RecentCard> recent = new ArrayList<>();
		for (LocalCollection.CardInstance instance : localCollection.snapshot().instances())
		{
			if (instance.pulledAt() <= 0L || isDebugPull(instance.pulledBy()))
			{
				continue;
			}
			recent.add(new HostedCollectionSnapshot.RecentCard(instance.id(), instance.displayName(),
				instance.foil(), instance.pulledAt(), owner));
		}
		return HostedCollectionSnapshot.newestCards(recent, limit);
	}

	private static boolean isDebugPull(String pulledBy)
	{
		return pulledBy != null && pulledBy.regionMatches(true, 0, "DEBUG_", 0, "DEBUG_".length());
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
		String localKey = localPlayerName == null ? null : EntityCardCatalog.normalize(localPlayerName);
		for (String owner : ownersOf(cardNames))
		{
			String ownerKey = EntityCardCatalog.normalize(owner);
			if (localKey != null && localKey.equals(ownerKey))
			{
				LocalCollection.CardSummary summary = bestLocalSummary(cardNames);
				if (summary != null)
				{
					details.add(describe(owner, summary.copies(), summary.foilCopies(), summary.debugCopies(),
						summary.pulledBy(), summary.firstPulledAt()));
					continue;
				}
			}

			MemberCollection member = memberCollections.get(ownerKey);
			HostedCollectionSnapshot.CardDetails hosted = bestHostedSummary(member, cardNames);
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

	private LocalCollection.CardSummary bestLocalSummary(Set<String> cardNames)
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
		return summary;
	}

	private static HostedCollectionSnapshot.CardDetails bestHostedSummary(MemberCollection member,
		Set<String> cardNames)
	{
		if (member == null)
		{
			return null;
		}
		HostedCollectionSnapshot.CardDetails hosted = null;
		for (String cardName : cardNames)
		{
			HostedCollectionSnapshot.CardDetails candidate = member.details.get(cardName);
			if (candidate != null && (hosted == null || candidate.copies() > hosted.copies()))
			{
				hosted = candidate;
			}
		}
		return hosted;
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

	void onTick()
	{
		if (started && ++ticks % REFRESH_TICKS == 0)
		{
			refresh();
		}
	}

	void localCollectionChanged()
	{
		localCollection.invalidate();
		requestRefresh();
	}

	void contextChanged()
	{
		requestRefresh();
	}

	void profileChanged()
	{
		localCollection.invalidate();
		clientThread.invokeLater(() ->
		{
			clearServerState();
			refresh();
		});
	}

	void hostedContextChanged(String incomingGroupId, String incomingGroupName,
		String incomingPlayerName, boolean incomingApproved)
	{
		clientThread.invokeLater(() -> applyHostedContext(incomingGroupId, incomingGroupName,
			incomingPlayerName, incomingApproved));
	}

	private void applyHostedContext(String incomingGroupId, String incomingGroupName,
		String incomingPlayerName, boolean incomingApproved)
	{
		if (!started)
		{
			return;
		}
		if (incomingGroupId == null || incomingGroupId.trim().isEmpty())
		{
			clearServerState();
			refresh();
			return;
		}
		String cleanGroupId = incomingGroupId.trim();
		if (!cleanGroupId.equals(groupId))
		{
			groupId = cleanGroupId;
			LoadedCache loaded = loadCache();
			sharedCards = loaded.sharedCards;
			memberCollections = loaded.memberCollections;
		}
		groupName = incomingGroupName == null || incomingGroupName.trim().isEmpty()
			? "Private server" : incomingGroupName.trim();
		localPlayerName = incomingPlayerName == null ? null : incomingPlayerName.trim();
		approved = incomingApproved;
		refresh();
	}

	void hostedSnapshotReceived(HostedCollectionSnapshot snapshot)
	{
		if (!started || snapshot == null)
		{
			return;
		}
		if (!snapshot.groupId().equals(groupId))
		{
			applyHostedContext(snapshot.groupId(), snapshot.groupName(), snapshot.localPlayerName(), true);
		}
		else
		{
			groupName = snapshot.groupName();
			localPlayerName = snapshot.localPlayerName();
			approved = true;
		}

		int previousSize = sharedCards.size();
		if (!snapshot.members().isEmpty())
		{
			Map<String, MemberCollection> updated = new HashMap<>();
			Map<String, List<HostedCollectionSnapshot.RecentCard>> recentByMember = new HashMap<>();
			for (Map.Entry<String, List<HostedCollectionSnapshot.RecentCard>> entry
				: snapshot.recentCards().entrySet())
			{
				recentByMember.put(EntityCardCatalog.normalize(entry.getKey()), entry.getValue());
			}
			for (Map.Entry<String, Map<String, HostedCollectionSnapshot.CardDetails>> entry
				: snapshot.members().entrySet())
			{
				if (entry.getKey() == null || entry.getKey().trim().isEmpty())
				{
					continue;
				}
				String shownName = entry.getKey().trim();
				String memberKey = EntityCardCatalog.normalize(shownName);
				updated.put(memberKey, new MemberCollection(shownName, entry.getValue().keySet(),
					entry.getValue(), recentByMember.getOrDefault(memberKey, Collections.emptyList())));
			}
			memberCollections = Collections.unmodifiableMap(updated);
		}

		Set<String> combined = new HashSet<>(sharedCards);
		combined.addAll(snapshot.unlocks());
		for (MemberCollection member : memberCollections.values())
		{
			combined.addAll(member.cards);
		}
		if (config.collectionMode() == CollectionMode.GROUP_IRONMAN)
		{
			combined.addAll(localCollection.getCards());
		}
		setSharedCards(combined);
		persist();
		refresh();

		int added = sharedCards.size() - previousSize;
		if (added > 0 && config.collectionMode() == CollectionMode.GROUP_IRONMAN && config.syncMessages())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"[Group TCG] The server collection gained " + added + " unlock" + (added == 1 ? "" : "s") + ".",
				null);
		}
	}

	private void requestRefresh()
	{
		clientThread.invokeLater(this::refresh);
	}

	private void refresh()
	{
		Set<String> local = localCollection.getCards();
		if (!started || config.collectionMode() == CollectionMode.SOLO)
		{
			status = soloStatus(local.size());
			return;
		}
		if (groupId == null || !approved)
		{
			status = inactiveStatus(local.size(), "Join an approved private server group");
			return;
		}

		boolean changed = false;
		if (localPlayerName != null && !localPlayerName.isEmpty())
		{
			changed = setMemberCollection(localPlayerName, local);
		}
		Set<String> combined = new HashSet<>(sharedCards);
		changed |= combined.addAll(local);
		if (changed)
		{
			setSharedCards(combined);
			persist();
		}
		status = new GroupSyncStatus(true, true, true, groupName, sharedCards.size(),
			memberCollections.size(), memberCollections.size(), "Shared through the private Group TCG server");
	}

	private LoadedCache loadCache()
	{
		try
		{
			String json = configManager.getRSProfileConfiguration(GroupmanTcgConfig.GROUP, CACHE_KEY);
			GroupCacheCodec.Cache cache = cacheCodec.decode(json);
			if (cache == null || !groupId.equals(cache.groupKey)
				|| !index.fingerprint().equals(cache.catalogFingerprint))
			{
				return LoadedCache.empty();
			}
			Map<String, MemberCollection> members = new HashMap<>();
			for (Map.Entry<String, String> entry : cache.memberUnlockBits.entrySet())
			{
				if (entry.getKey() != null && entry.getValue() != null && !entry.getKey().trim().isEmpty())
				{
					String displayName = entry.getKey().trim();
					members.put(EntityCardCatalog.normalize(displayName),
						new MemberCollection(displayName, index.decode(entry.getValue()), Collections.emptyMap(),
							Collections.emptyList()));
				}
			}
			return new LoadedCache(index.decode(cache.unlockBits), members);
		}
		catch (Exception ex)
		{
			log.debug("Unable to load server collection cache", ex);
			return LoadedCache.empty();
		}
	}

	private void persist()
	{
		if (groupId == null)
		{
			return;
		}
		Map<String, String> memberBits = new LinkedHashMap<>();
		for (MemberCollection member : memberCollections.values())
		{
			memberBits.put(member.displayName, index.encode(member.cards));
		}
		configManager.setRSProfileConfiguration(GroupmanTcgConfig.GROUP, CACHE_KEY,
			cacheCodec.encode(groupId, index.fingerprint(), index.encode(sharedCards), memberBits));
	}

	private boolean setMemberCollection(String displayName, Set<String> cards)
	{
		String shownName = displayName.trim();
		String key = EntityCardCatalog.normalize(shownName);
		MemberCollection current = memberCollections.get(key);
		Map<String, HostedCollectionSnapshot.CardDetails> details = new HashMap<>();
		List<HostedCollectionSnapshot.RecentCard> recentCards = new ArrayList<>();
		if (current != null)
		{
			for (Map.Entry<String, HostedCollectionSnapshot.CardDetails> entry : current.details.entrySet())
			{
				if (cards.contains(entry.getKey()))
				{
					details.put(entry.getKey(), entry.getValue());
				}
			}
			for (HostedCollectionSnapshot.RecentCard recent : current.recentCards)
			{
				if (cards.contains(EntityCardCatalog.normalize(recent.cardName())))
				{
					recentCards.add(recent);
				}
			}
		}
		if (current != null && current.displayName.equals(shownName) && current.cards.equals(cards)
			&& current.details.equals(details) && current.recentCards.equals(recentCards))
		{
			return false;
		}
		Map<String, MemberCollection> updated = new HashMap<>(memberCollections);
		updated.put(key, new MemberCollection(shownName, cards, details, recentCards));
		memberCollections = Collections.unmodifiableMap(updated);
		return true;
	}

	private void setSharedCards(Set<String> cards)
	{
		sharedCards = Collections.unmodifiableSet(new HashSet<>(cards));
	}

	private void clearServerState()
	{
		sharedCards = Collections.emptySet();
		memberCollections = Collections.emptyMap();
		groupId = null;
		groupName = null;
		localPlayerName = null;
		approved = false;
	}

	private static GroupSyncStatus inactiveStatus(int cards, String detail)
	{
		return new GroupSyncStatus(true, false, false, "", cards, 0, 0, detail);
	}

	private static GroupSyncStatus soloStatus(int cards)
	{
		return new GroupSyncStatus(false, true, false, "", cards, 0, 0,
			"Solo unlocks; server membership and Top Trumps remain available");
	}

	private static final class MemberCollection
	{
		private final String displayName;
		private final Set<String> cards;
		private final Map<String, HostedCollectionSnapshot.CardDetails> details;
		private final List<HostedCollectionSnapshot.RecentCard> recentCards;

		private MemberCollection(String displayName, Set<String> cards,
			Map<String, HostedCollectionSnapshot.CardDetails> details,
			List<HostedCollectionSnapshot.RecentCard> recentCards)
		{
			this.displayName = displayName;
			this.cards = Collections.unmodifiableSet(new HashSet<>(cards));
			this.details = Collections.unmodifiableMap(new HashMap<>(details));
			this.recentCards = HostedCollectionSnapshot.newestCards(recentCards, recentCards.size());
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
