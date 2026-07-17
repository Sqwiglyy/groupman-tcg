package com.groupmantcg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;

/** Durable private-server membership, collection, pack, and Top Trumps sync. */
@Slf4j
@Singleton
class HostedSyncService
{
	private static final long SYNC_INTERVAL_MILLIS = 5_000L;
	private static final long RETRY_INTERVAL_MILLIS = 60_000L;
	private static final long MEMBER_REFRESH_MILLIS = 5 * 60_000L;
	private static final int COLLECTION_CHUNK = 150;
	private static final int MAX_PENDING_PACKS = 25;
	private static final int MAX_RECENT_CARDS = 20;

	private final Client client;
	private final ClientThread clientThread;
	private final GroupmanTcgConfig config;
	private final LocalCollection localCollection;
	private final SharedCollectionService sharedCollection;
	private final HostedApiClient api;
	private final HostedProfileStore profileStore;
	private final Provider<GroupPackRevealService> packRevealService;
	private final Provider<TopTrumpsService> topTrumpsService;
	private final ScheduledExecutorService executor;
	private final AtomicBoolean busy = new AtomicBoolean();
	private final Deque<HostedApiClient.PackUpload> pendingPacks = new ArrayDeque<>();

	private volatile HostedProfile profile;
	private volatile HostedSyncStatus status = HostedSyncStatus.simple(
		HostedSyncStatus.State.NOT_LINKED, "Create or join a private group");
	private volatile PlayerContext context = PlayerContext.empty();
	private volatile long nextSyncAt;
	private volatile boolean forceUpload;
	private volatile boolean started;
	private long lastMemberRefreshAt;
	private long lastMemberVersion = -1L;
	private List<HostedSyncStatus.Member> lastMembers = Collections.emptyList();

	@Inject
	HostedSyncService(Client client, ClientThread clientThread, GroupmanTcgConfig config,
		LocalCollection localCollection, SharedCollectionService sharedCollection, HostedApiClient api,
		HostedProfileStore profileStore, Provider<GroupPackRevealService> packRevealService,
		Provider<TopTrumpsService> topTrumpsService, ScheduledExecutorService executor)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.localCollection = localCollection;
		this.sharedCollection = sharedCollection;
		this.api = api;
		this.profileStore = profileStore;
		this.packRevealService = packRevealService;
		this.topTrumpsService = topTrumpsService;
		this.executor = executor;
	}

	void start()
	{
		started = true;
		reloadProfile();
		publishHostedContext();
		nextSyncAt = 0L;
	}

	void stop()
	{
		started = false;
		synchronized (pendingPacks)
		{
			pendingPacks.clear();
		}
	}

	void onTick()
	{
		if (!started)
		{
			return;
		}
		updateContext();
		if (!config.hostedSyncEnabled())
		{
			status = HostedSyncStatus.simple(HostedSyncStatus.State.DISABLED, "Private server disabled in settings");
			return;
		}
		HostedProfile current = profile;
		if (current == null)
		{
			status = HostedSyncStatus.simple(HostedSyncStatus.State.NOT_LINKED, "Create or join a private group");
			return;
		}
		PlayerContext currentContext = context;
		if (!currentContext.ready())
		{
			status = statusFor(current, HostedSyncStatus.State.WRONG_PROFILE,
				"Log into the linked RuneScape character", lastMembers, status.lastSyncedAt());
			return;
		}
		if (!EntityCardCatalog.normalize(current.rsn).equals(EntityCardCatalog.normalize(currentContext.rsn)))
		{
			status = statusFor(current, HostedSyncStatus.State.WRONG_PROFILE,
				"This server token belongs to " + current.rsn, lastMembers, status.lastSyncedAt());
			return;
		}
		long now = System.currentTimeMillis();
		if (now < nextSyncAt || !busy.compareAndSet(false, true))
		{
			return;
		}
		LocalCollection.Snapshot snapshot = localCollection.snapshot();
		status = statusFor(current, HostedSyncStatus.State.SYNCING, "Syncing with Cloudflare...",
			lastMembers, status.lastSyncedAt());
		executor.execute(() -> syncInBackground(current, snapshot));
	}

	void localCollectionChanged()
	{
		forceUpload = true;
		nextSyncAt = Math.min(nextSyncAt, System.currentTimeMillis() + 3_000L);
	}

	void profileChanged()
	{
		context = PlayerContext.empty();
		reloadProfile();
		publishHostedContext();
		lastMembers = Collections.emptyList();
		lastMemberRefreshAt = 0L;
		lastMemberVersion = -1L;
		synchronized (pendingPacks)
		{
			pendingPacks.clear();
		}
		nextSyncAt = 0L;
	}

	void contextChanged()
	{
		nextSyncAt = 0L;
	}

	void collectionModeChanged()
	{
		forceUpload = true;
		nextSyncAt = 0L;
		sharedCollection.contextChanged();
	}

	void syncNow()
	{
		nextSyncAt = 0L;
	}

	HostedSyncStatus status()
	{
		return status;
	}

	HostedSyncStatus.Member memberByPlayerName(String playerName)
	{
		if (playerName == null)
		{
			return null;
		}
		String key = EntityCardCatalog.normalize(playerName);
		for (HostedSyncStatus.Member member : lastMembers)
		{
			if (member.approved() && key.equals(EntityCardCatalog.normalize(member.playerName())))
			{
				return member;
			}
		}
		return null;
	}

	boolean canChallenge(String memberId)
	{
		HostedProfile current = profile;
		if (!started || !config.hostedSyncEnabled() || current == null || !current.approved()
			|| memberId == null || memberId.equals(current.memberId))
		{
			return false;
		}
		for (HostedSyncStatus.Member member : lastMembers)
		{
			if (member.approved() && member.id().equals(memberId))
			{
				return true;
			}
		}
		return false;
	}

	void challengeMember(String memberId, ActionCallback callback)
	{
		HostedProfile current = profile;
		if (current == null || !canChallenge(memberId))
		{
			complete(callback, "That player is not an approved member of this private server.");
			return;
		}
		runConcurrentAction(callback, () ->
		{
			api.createTopTrumpsChallenge(current, memberId);
			if (profile != current || !started)
			{
				throw new IOException("The active RuneScape profile changed");
			}
			nextSyncAt = 0L;
		});
	}

	void respondTopTrumps(String challengeId, boolean accepted, ActionCallback callback)
	{
		HostedProfile current = profile;
		if (current == null || !current.approved())
		{
			complete(callback, "Connect to the approved server membership first.");
			return;
		}
		runConcurrentAction(callback, () ->
		{
			api.respondTopTrumpsChallenge(current, challengeId, accepted);
			if (profile != current || !started)
			{
				throw new IOException("The active RuneScape profile changed");
			}
			nextSyncAt = 0L;
		});
	}

	void createGroup(String setupKey, ActionCallback callback)
	{
		PlayerContext currentContext = context;
		String cleanSetupKey = setupKey == null ? "" : setupKey.trim();
		if (!currentContext.ready())
		{
			complete(callback, "Log into the RuneScape character first.");
			return;
		}
		if (cleanSetupKey.length() < 16 || cleanSetupKey.length() > 256)
		{
			complete(callback, "Enter this Worker's private setup key (16 to 256 characters).");
			return;
		}
		String selectedServer;
		try
		{
			selectedServer = api.configuredBaseUrl();
		}
		catch (IOException ex)
		{
			complete(callback, ex.getMessage());
			return;
		}
		runAction(callback, () ->
		{
			HostedApiClient.CreateResponse response = api.createGroupAt(selectedServer, cleanSetupKey,
				currentContext.rsn, collectionModeValue());
			if (response.group == null || response.member == null || response.invite == null)
			{
				throw new IOException("The hosted service returned an incomplete group");
			}
			if (context != currentContext || !started)
			{
				throw new IOException("The active RuneScape profile changed during setup");
			}
			HostedProfile created = profileFrom(response.group, response.member, currentContext.rsn);
			created.serverUrl = selectedServer;
			created.inviteCode = response.invite.code;
			created.inviteExpiresAt = response.invite.expiresAt;
			profileStore.save(created);
			profile = created;
			publishHostedContext();
			forceUpload = true;
			status = statusFor(created, HostedSyncStatus.State.SYNCING, "Hosted group created; initial sync pending",
				Collections.emptyList(), 0L);
			nextSyncAt = 0L;
		});
	}

	void joinGroup(String groupId, String inviteCode, ActionCallback callback)
	{
		PlayerContext currentContext = context;
		String cleanGroup = groupId == null ? "" : groupId.trim();
		String cleanInvite = inviteCode == null ? "" : inviteCode.trim();
		if (!currentContext.ready())
		{
			complete(callback, "Log into the RuneScape character first.");
			return;
		}
		if (cleanGroup.isEmpty() || cleanInvite.isEmpty())
		{
			complete(callback, "Enter both the group ID and invite code.");
			return;
		}
		String selectedServer;
		try
		{
			selectedServer = api.configuredBaseUrl();
		}
		catch (IOException ex)
		{
			complete(callback, ex.getMessage());
			return;
		}
		runAction(callback, () ->
		{
			HostedApiClient.JoinResponse response = api.joinGroupAt(selectedServer, cleanGroup, cleanInvite,
				currentContext.rsn, collectionModeValue());
			if (response.member == null)
			{
				throw new IOException("The hosted service returned an incomplete membership");
			}
			if (context != currentContext || !started)
			{
				throw new IOException("The active RuneScape profile changed during setup");
			}
			HostedProfile joined = profileFrom(null, response.member, currentContext.rsn);
			joined.groupId = cleanGroup;
			joined.serverUrl = selectedServer;
			profileStore.save(joined);
			profile = joined;
			publishHostedContext();
			status = statusFor(joined, HostedSyncStatus.State.WAITING_APPROVAL,
				"Waiting for the group owner to approve " + response.member.label,
				Collections.emptyList(), 0L);
			nextSyncAt = 0L;
		});
	}

	void approveMember(String memberId, ActionCallback callback)
	{
		HostedProfile current = profile;
		if (current == null || !current.owner())
		{
			complete(callback, "Only the hosted group owner can approve members.");
			return;
		}
		runAction(callback, () ->
		{
			api.approveMember(current, memberId);
			if (profile != current || !started)
			{
				throw new IOException("The active RuneScape profile changed");
			}
			nextSyncAt = 0L;
		});
	}

	void revokeMember(String memberId, ActionCallback callback)
	{
		HostedProfile current = profile;
		if (current == null || !current.owner())
		{
			complete(callback, "Only the hosted group owner can remove members.");
			return;
		}
		runAction(callback, () ->
		{
			api.revokeMember(current, memberId);
			if (profile != current || !started)
			{
				throw new IOException("The active RuneScape profile changed");
			}
			nextSyncAt = 0L;
		});
	}

	void rotateInvite(ActionCallback callback)
	{
		HostedProfile current = profile;
		if (current == null || !current.owner())
		{
			complete(callback, "Only the hosted group owner can create invites.");
			return;
		}
		runAction(callback, () ->
		{
			HostedApiClient.InviteResponse response = api.rotateInvite(current);
			if (response.invite == null)
			{
				throw new IOException("The hosted service did not return an invite");
			}
			if (profile != current || !started)
			{
				throw new IOException("The active RuneScape profile changed");
			}
			current.inviteCode = response.invite.code;
			current.inviteExpiresAt = response.invite.expiresAt;
			if (profile != current || !started)
			{
				return;
			}
			profileStore.save(current);
			status = statusFor(current, HostedSyncStatus.State.ONLINE, "New 30-day invite created",
				lastMembers, status.lastSyncedAt());
		});
	}

	void disconnect()
	{
		profileStore.clear();
		profile = null;
		lastMembers = Collections.emptyList();
		lastMemberRefreshAt = 0L;
		lastMemberVersion = -1L;
		status = HostedSyncStatus.simple(HostedSyncStatus.State.NOT_LINKED, "Create or join a private group");
		sharedCollection.hostedContextChanged(null, null, null, false);
		synchronized (pendingPacks)
		{
			pendingPacks.clear();
		}
	}

	void queueLocalPack(List<GroupPackRevealService.Pull> pulls)
	{
		HostedProfile current = profile;
		PlayerContext currentContext = context;
		if (!started || !config.hostedSyncEnabled() || current == null || !current.approved()
			|| !currentContext.ready()
			|| !EntityCardCatalog.normalize(current.rsn).equals(EntityCardCatalog.normalize(currentContext.rsn))
			|| pulls == null || pulls.isEmpty())
		{
			return;
		}
		List<HostedApiClient.CardPullUpload> cards = new ArrayList<>();
		long openedAt = 0L;
		for (GroupPackRevealService.Pull pull : pulls)
		{
			cards.add(new HostedApiClient.CardPullUpload(pull.cardName(), pull.foil(), pull.newForCollection()));
			if (pull.pulledAt() > 0L)
			{
				openedAt = openedAt == 0L ? pull.pulledAt() : Math.min(openedAt, pull.pulledAt());
			}
		}
		if (openedAt == 0L)
		{
			openedAt = System.currentTimeMillis();
		}
		HostedApiClient.PackUpload upload = new HostedApiClient.PackUpload(
			"pack_" + UUID.randomUUID(), openedAt, cards);
		synchronized (pendingPacks)
		{
			while (pendingPacks.size() >= MAX_PENDING_PACKS)
			{
				pendingPacks.removeFirst();
			}
			pendingPacks.addLast(upload);
		}
		nextSyncAt = 0L;
	}

	private void syncInBackground(HostedProfile current, LocalCollection.Snapshot localSnapshot)
	{
		try
		{
			HostedApiClient.GroupResponse group = api.getGroup(current);
			validateIdentity(current, group);
			updateProfileFromGroup(current, group);
			lastMembers = publicMembers(group.members);
			uploadPendingPacks(current);

			String fingerprint = fingerprint(localSnapshot.instances());
			if (forceUpload || !fingerprint.equals(current.lastUploadedFingerprint))
			{
				uploadCollection(current, localSnapshot.instances());
				current.lastUploadedFingerprint = fingerprint;
				forceUpload = false;
			}

			Set<String> unlocks = new HashSet<>();
			List<HostedApiClient.PackEvent> events = new ArrayList<>();
			List<HostedApiClient.TopTrumpsEvent> topTrumpsEvents = new ArrayList<>();
			long previousVersion = current.collectionVersion;
			for (int page = 0; page < 20; page++)
			{
				HostedApiClient.SyncResponse sync = api.sync(current, current.eventCursor,
					current.topTrumpsCursor, current.collectionVersion);
				if (sync.collection != null)
				{
					current.collectionVersion = sync.collection.version;
					if (sync.collection.unlocks != null)
					{
						for (String card : sync.collection.unlocks)
						{
							if (card != null && !card.trim().isEmpty())
							{
								unlocks.add(EntityCardCatalog.normalize(card));
							}
						}
					}
				}
				if (sync.events != null)
				{
					events.addAll(sync.events);
				}
				if (sync.topTrumpsEvents != null)
				{
					topTrumpsEvents.addAll(sync.topTrumpsEvents);
				}
				current.eventCursor = Math.max(current.eventCursor, sync.nextCursor);
				current.topTrumpsCursor = Math.max(current.topTrumpsCursor, sync.topTrumpsNextCursor);
				if (!sync.hasMore)
				{
					break;
				}
			}

			long now = System.currentTimeMillis();
			HostedCollectionSnapshot hostedSnapshot = null;
			if (lastMemberRefreshAt == 0L || now - lastMemberRefreshAt >= MEMBER_REFRESH_MILLIS
				|| current.collectionVersion != lastMemberVersion || current.collectionVersion != previousVersion)
			{
				hostedSnapshot = fetchCollections(current, current.groupName, unlocks);
				lastMemberRefreshAt = now;
				lastMemberVersion = current.collectionVersion;
			}
			else if (!unlocks.isEmpty())
			{
				hostedSnapshot = new HostedCollectionSnapshot(current.groupId, current.groupName,
					current.rsn, unlocks, Collections.emptyMap());
			}

			if (profile != current || !started)
			{
				return;
			}
			profileStore.save(current);
			profile = current;
			applyHostedResults(current, hostedSnapshot, events, topTrumpsEvents);
			status = statusFor(current, HostedSyncStatus.State.ONLINE,
				"Cloudflare synced " + lastMembers.size() + " member" + (lastMembers.size() == 1 ? "" : "s"),
				lastMembers, now);
			nextSyncAt = now + SYNC_INTERVAL_MILLIS;
		}
		catch (HostedApiException ex)
		{
			handleApiFailure(current, ex);
		}
		catch (Exception ex)
		{
			log.debug("Hosted Group TCG sync failed", ex);
			status = statusFor(current, HostedSyncStatus.State.ERROR,
				"Cloudflare unavailable; using cached unlocks", lastMembers, status.lastSyncedAt());
			nextSyncAt = System.currentTimeMillis() + RETRY_INTERVAL_MILLIS;
		}
		finally
		{
			busy.set(false);
		}
	}

	private void handleApiFailure(HostedProfile current, HostedApiException ex)
	{
		if (profile != current || !started)
		{
			return;
		}
		if ("approval_required".equals(ex.code()))
		{
			current.status = "pending";
			profileStore.save(current);
			status = statusFor(current, HostedSyncStatus.State.WAITING_APPROVAL,
				"Waiting for the owner to approve " + privateLabel(current),
				lastMembers, status.lastSyncedAt());
		}
		else if (ex.status() == 401)
		{
			status = statusFor(current, HostedSyncStatus.State.ERROR,
				"Server credentials were revoked; disconnect and join again", lastMembers, status.lastSyncedAt());
		}
		else
		{
			status = statusFor(current, HostedSyncStatus.State.ERROR,
				ex.getMessage(), lastMembers, status.lastSyncedAt());
		}
		nextSyncAt = System.currentTimeMillis() + RETRY_INTERVAL_MILLIS;
	}

	private void validateIdentity(HostedProfile current, HostedApiClient.GroupResponse group) throws IOException
	{
		if (group == null || group.group == null || group.currentMember == null
			|| !current.memberId.equals(group.currentMember.id))
		{
			throw new IOException("The hosted membership response did not match this profile");
		}
	}

	private void updateProfileFromGroup(HostedProfile current, HostedApiClient.GroupResponse group)
	{
		current.groupName = serverGroupName(current.groupId);
		current.memberLabel = group.currentMember.label;
		if (group.currentMember.playerName != null && !group.currentMember.playerName.trim().isEmpty())
		{
			current.rsn = group.currentMember.playerName.trim();
		}
		current.role = group.currentMember.role;
		current.status = group.currentMember.status;
		publishHostedContext();
	}

	private void uploadPendingPacks(HostedProfile current) throws IOException
	{
		while (true)
		{
			HostedApiClient.PackUpload pack;
			synchronized (pendingPacks)
			{
				pack = pendingPacks.peekFirst();
			}
			if (pack == null)
			{
				return;
			}
			api.uploadPack(current, pack);
			synchronized (pendingPacks)
			{
				if (pendingPacks.peekFirst() == pack)
				{
					pendingPacks.removeFirst();
				}
			}
		}
	}

	private void uploadCollection(HostedProfile current, List<LocalCollection.CardInstance> localInstances)
		throws IOException
	{
		List<HostedApiClient.CardInstanceUpload> instances = new ArrayList<>();
		for (LocalCollection.CardInstance instance : localInstances)
		{
			boolean debug = instance.pulledBy() != null
				&& instance.pulledBy().toUpperCase().startsWith("DEBUG_");
			instances.add(new HostedApiClient.CardInstanceUpload(
				PrivacyIdentifiers.collectionInstance(instance.id()), instance.displayName(),
				instance.foil(), debug, Math.max(0L, instance.pulledAt())));
		}
		String snapshotId = "snapshot_" + UUID.randomUUID();
		if (instances.isEmpty())
		{
			api.uploadMemberCollection(current, snapshotId, Collections.emptyList(), true,
				collectionModeValue());
			return;
		}
		for (int offset = 0; offset < instances.size(); offset += COLLECTION_CHUNK)
		{
			int end = Math.min(instances.size(), offset + COLLECTION_CHUNK);
			api.uploadMemberCollection(current, snapshotId, instances.subList(offset, end),
				end == instances.size(), collectionModeValue());
		}
	}

	private HostedCollectionSnapshot fetchCollections(HostedProfile current, String groupName,
		Set<String> unlocks) throws IOException
	{
		HostedApiClient.MemberCollectionsResponse summaries = api.getMemberCollections(current);
		Map<String, Map<String, HostedCollectionSnapshot.CardDetails>> members = new LinkedHashMap<>();
		Map<String, List<HostedCollectionSnapshot.RecentCard>> recentMembers = new LinkedHashMap<>();
		if (summaries.members == null)
		{
			return new HostedCollectionSnapshot(current.groupId, groupName, current.rsn, unlocks,
				members, recentMembers);
		}
		for (HostedApiClient.MemberSummary summary : summaries.members)
		{
			if (summary == null || summary.id == null || summary.label == null)
			{
				continue;
			}
			Map<String, MutableCard> accumulated = new HashMap<>();
			List<HostedApiClient.CardInstanceResult> recentInstances = new ArrayList<>();
			int offset = 0;
			for (int page = 0; page < 50; page++)
			{
				HostedApiClient.MemberCollectionResponse response = api.getMemberCollection(current, summary.id, offset);
				if (response.instances != null)
				{
					for (HostedApiClient.CardInstanceResult instance : response.instances)
					{
						if (instance == null || instance.cardName == null || instance.cardName.trim().isEmpty())
						{
							continue;
						}
						String key = EntityCardCatalog.normalize(instance.cardName);
						accumulated.computeIfAbsent(key, ignored -> new MutableCard(instance.cardName))
							.add(instance);
						if (instance.pulledAt > 0L && !"debug".equals(instance.acquisitionKind))
						{
							recentInstances.add(instance);
						}
					}
				}
				if (!response.hasMore || response.nextOffset <= offset)
				{
					break;
				}
				offset = response.nextOffset;
			}
			Map<String, HostedCollectionSnapshot.CardDetails> cards = new HashMap<>();
			for (Map.Entry<String, MutableCard> entry : accumulated.entrySet())
			{
				cards.put(entry.getKey(), entry.getValue().freeze());
			}
			String shownName = summary.playerName == null || summary.playerName.trim().isEmpty()
				? summary.label : summary.playerName.trim();
			members.put(shownName, cards);
			List<HostedCollectionSnapshot.RecentCard> recentCards = new ArrayList<>();
			for (HostedApiClient.CardInstanceResult instance : recentInstances)
			{
				recentCards.add(new HostedCollectionSnapshot.RecentCard(instance.sourceInstanceId,
					instance.cardName, instance.foil, instance.pulledAt, shownName));
			}
			recentMembers.put(shownName,
				HostedCollectionSnapshot.newestCards(recentCards, MAX_RECENT_CARDS));
		}
		return new HostedCollectionSnapshot(current.groupId, groupName, current.rsn, unlocks,
			members, recentMembers);
	}

	private void applyHostedResults(HostedProfile current, HostedCollectionSnapshot snapshot,
		List<HostedApiClient.PackEvent> events, List<HostedApiClient.TopTrumpsEvent> topTrumpsEvents)
	{
		clientThread.invokeLater(() ->
		{
			if (profile != current || !started)
			{
				return;
			}
			if (snapshot != null)
			{
				sharedCollection.hostedSnapshotReceived(snapshot);
			}
			for (HostedApiClient.PackEvent event : events)
			{
				if (event == null || event.member == null || current.memberId.equals(event.member.id)
					|| event.cards == null || event.cards.isEmpty())
				{
					continue;
				}
				List<GroupPackRevealService.Pull> pulls = new ArrayList<>();
				for (HostedApiClient.CardPullResult card : event.cards)
				{
					if (card != null && card.name != null)
					{
						pulls.add(new GroupPackRevealService.Pull(card.name, card.foil, card.isNew));
					}
				}
				String opener = event.member.playerName == null || event.member.playerName.trim().isEmpty()
					? event.member.label : event.member.playerName.trim();
				packRevealService.get().hostedReveal(event.eventId, opener, pulls,
					event.openedAt, event.receivedAt);
			}
			for (HostedApiClient.TopTrumpsEvent event : topTrumpsEvents)
			{
				if (event != null)
				{
					topTrumpsService.get().hostedEvent(event);
				}
			}
		});
	}

	private void updateContext()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			clearContext();
			return;
		}
		Player player = client.getLocalPlayer();
		if (player == null || player.getName() == null)
		{
			clearContext();
			return;
		}
		String rsn = player.getName().trim();
		if (!context.matches(rsn))
		{
			context = new PlayerContext(rsn);
		}
	}

	private void clearContext()
	{
		if (context.ready())
		{
			context = PlayerContext.empty();
		}
	}

	private void reloadProfile()
	{
		profile = profileStore.load();
		if (profile != null && (profile.serverUrl == null || profile.serverUrl.trim().isEmpty()))
		{
			// Never redirect an old bearer token to a newly configured server.
			profileStore.clear();
			profile = null;
		}
		// Re-request the grow-only union once per client/profile session so hosted state can rebuild a missing local cache.
		if (profile != null)
		{
			profile.groupName = serverGroupName(profile.groupId);
			profile.collectionVersion = 0L;
		}
		if (profile == null)
		{
			status = HostedSyncStatus.simple(HostedSyncStatus.State.NOT_LINKED, "Create or join a private group");
		}
		else
		{
			HostedSyncStatus.State state = profile.approved()
				? HostedSyncStatus.State.SYNCING : HostedSyncStatus.State.WAITING_APPROVAL;
			status = statusFor(profile, state, profile.approved() ? "Connecting to Cloudflare..."
				: "Waiting for owner approval", Collections.emptyList(), 0L);
		}
	}

	private void runAction(ActionCallback callback, BackgroundAction action)
	{
		if (!busy.compareAndSet(false, true))
		{
			complete(callback, "Another hosted action is still running.");
			return;
		}
		executor.execute(() ->
		{
			String error = null;
			try
			{
				action.run();
			}
			catch (HostedApiException ex)
			{
				error = ex.getMessage();
			}
			catch (Exception ex)
			{
				log.debug("Hosted Group TCG action failed", ex);
				error = "Cloudflare could not complete the request.";
			}
			finally
			{
				busy.set(false);
			}
			complete(callback, error);
		});
	}

	private void runConcurrentAction(ActionCallback callback, BackgroundAction action)
	{
		executor.execute(() ->
		{
			String error = null;
			try
			{
				action.run();
			}
			catch (HostedApiException ex)
			{
				error = ex.getMessage();
			}
			catch (Exception ex)
			{
				log.debug("Hosted Group TCG action failed", ex);
				error = "The private server could not complete the request.";
			}
			complete(callback, error);
		});
	}

	private static HostedProfile profileFrom(HostedApiClient.GroupRef group,
		HostedApiClient.MemberRef member, String localRsn)
	{
		HostedProfile result = new HostedProfile();
		result.groupId = group != null ? group.id : member.groupId;
		result.groupName = serverGroupName(result.groupId);
		result.memberId = member.id;
		result.memberLabel = member.label;
		result.rsn = localRsn;
		result.role = member.role;
		result.status = member.status;
		result.token = member.token;
		result.collectionVersion = group == null ? 0L : group.collectionVersion;
		return result;
	}

	private static List<HostedSyncStatus.Member> publicMembers(List<HostedApiClient.MemberRef> members)
	{
		List<HostedSyncStatus.Member> result = new ArrayList<>();
		if (members != null)
		{
			for (HostedApiClient.MemberRef member : members)
			{
				if (member != null)
				{
					result.add(new HostedSyncStatus.Member(member.id, member.label, member.playerName,
						member.collectionMode, member.role, member.status, member.revoked));
				}
			}
		}
		return Collections.unmodifiableList(result);
	}

	private static HostedSyncStatus statusFor(HostedProfile profile, HostedSyncStatus.State state,
		String detail, List<HostedSyncStatus.Member> members, long lastSyncedAt)
	{
		return new HostedSyncStatus(state, detail, profile.groupName, profile.groupId, profile.memberId,
			profile.memberLabel, profile.owner(),
			profile.inviteCode, profile.inviteExpiresAt, lastSyncedAt, members);
	}

	private static String privateLabel(HostedProfile profile)
	{
		return profile.memberLabel == null || profile.memberLabel.trim().isEmpty()
			? "this membership" : profile.memberLabel;
	}

	private void publishHostedContext()
	{
		HostedProfile current = profile;
		sharedCollection.hostedContextChanged(current == null ? null : current.groupId,
			current == null ? null : current.groupName, current == null ? null : current.rsn,
			current != null && current.approved());
	}

	private String collectionModeValue()
	{
		return config.collectionMode() == CollectionMode.SOLO ? "solo" : "shared";
	}

	private static String serverGroupName(String groupId)
	{
		if (groupId == null || groupId.trim().isEmpty())
		{
			return "Private server";
		}
		String clean = groupId.trim();
		return "Server " + clean.substring(0, Math.min(8, clean.length()));
	}

	private static String fingerprint(List<LocalCollection.CardInstance> instances)
	{
		try
		{
			List<LocalCollection.CardInstance> sorted = new ArrayList<>(instances);
			sorted.sort(Comparator.comparing(LocalCollection.CardInstance::id));
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (LocalCollection.CardInstance instance : sorted)
			{
				String row = instance.id() + '\u0000' + instance.normalizedName() + '\u0000'
					+ instance.foil() + '\u0000' + instance.pulledBy() + '\u0000' + instance.pulledAt() + '\n';
				digest.update(row.getBytes(StandardCharsets.UTF_8));
			}
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
		}
		catch (NoSuchAlgorithmException ex)
		{
			throw new IllegalStateException(ex);
		}
	}

	private static void complete(ActionCallback callback, String error)
	{
		if (callback != null)
		{
			SwingUtilities.invokeLater(() -> callback.finished(error));
		}
	}

	interface ActionCallback
	{
		void finished(String error);
	}

	private interface BackgroundAction
	{
		void run() throws Exception;
	}

	private static final class PlayerContext
	{
		private final String rsn;

		private PlayerContext(String rsn)
		{
			this.rsn = rsn;
		}

		private boolean ready()
		{
			return !rsn.isEmpty();
		}

		private boolean matches(String otherRsn)
		{
			return EntityCardCatalog.normalize(rsn).equals(EntityCardCatalog.normalize(otherRsn));
		}

		private static PlayerContext empty()
		{
			return new PlayerContext("");
		}
	}

	private static final class MutableCard
	{
		private final String cardName;
		private int copies;
		private int foils;
		private int debug;
		private long firstPulledAt;
		private long lastPulledAt;

		private MutableCard(String cardName)
		{
			this.cardName = cardName;
		}

		private MutableCard add(HostedApiClient.CardInstanceResult instance)
		{
			copies++;
			if (instance.foil)
			{
				foils++;
			}
			if ("debug".equals(instance.acquisitionKind))
			{
				debug++;
			}
			if (instance.pulledAt > 0L)
			{
				firstPulledAt = firstPulledAt == 0L ? instance.pulledAt : Math.min(firstPulledAt, instance.pulledAt);
				lastPulledAt = Math.max(lastPulledAt, instance.pulledAt);
			}
			return this;
		}

		private HostedCollectionSnapshot.CardDetails freeze()
		{
			return new HostedCollectionSnapshot.CardDetails(cardName, copies, foils, debug,
				firstPulledAt, lastPulledAt);
		}
	}
}
