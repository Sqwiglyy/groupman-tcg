package com.groupmantcg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Detects local pack transactions and presents verified remote reveals. */
@Singleton
class GroupPackRevealService
{
	private static final int MAX_PULLS = 5;
	private static final int MAX_QUEUED_REVEALS = 4;
	private static final long COLLECTION_SETTLE_MILLIS = 2_000L;
	private static final long ART_WARMUP_MILLIS = 750L;
	static final long REMOTE_REVEAL_DELAY_MILLIS = 15_000L;
	private static final long MAX_RECENT_PULL_AGE_MILLIS = 120_000L;

	private final GroupmanTcgConfig config;
	private final LocalCollection localCollection;
	private final HostedSyncService hostedSync;
	private final CardVisualCatalog visualCatalog;
	private final CardArtService cardArt;

	private final Deque<QueuedReveal> queue = new ArrayDeque<>();
	private final LinkedHashMap<String, Boolean> hostedEvents = new LinkedHashMap<>();
	private Map<String, LocalCollection.CardInstance> previousInstances = Collections.emptyMap();
	private long previousOpenedPacks;
	private long pendingReadAt = -1L;
	private boolean rebaseline;
	private boolean started;
	private RevealView active;

	@Inject
	GroupPackRevealService(GroupmanTcgConfig config, LocalCollection localCollection,
		HostedSyncService hostedSync, CardVisualCatalog visualCatalog, CardArtService cardArt)
	{
		this.config = config;
		this.localCollection = localCollection;
		this.hostedSync = hostedSync;
		this.visualCatalog = visualCatalog;
		this.cardArt = cardArt;
	}

	void start()
	{
		started = true;
		prime(localCollection.snapshot());
	}

	synchronized void stop()
	{
		started = false;
		queue.clear();
		hostedEvents.clear();
		active = null;
		pendingReadAt = -1L;
		previousInstances = Collections.emptyMap();
		previousOpenedPacks = 0L;
	}

	synchronized void localStateChanged()
	{
		if (started)
		{
			pendingReadAt = System.currentTimeMillis() + COLLECTION_SETTLE_MILLIS;
		}
	}

	synchronized void profileChanged()
	{
		queue.clear();
		hostedEvents.clear();
		active = null;
		rebaseline = true;
		pendingReadAt = System.currentTimeMillis() + 500L;
	}

	synchronized void onTick()
	{
		if (!started)
		{
			return;
		}
		long now = System.currentTimeMillis();
		if (pendingReadAt >= 0L && now >= pendingReadAt)
		{
			pendingReadAt = -1L;
			LocalCollection.Snapshot snapshot = localCollection.snapshot();
			if (rebaseline)
			{
				rebaseline = false;
				prime(snapshot);
			}
			else
			{
				detectAndBroadcast(snapshot, now);
			}
		}
		advance(now);
	}

	synchronized RevealView currentReveal()
	{
		if (!config.showPackReveals())
		{
			queue.clear();
			active = null;
			return null;
		}
		long now = System.currentTimeMillis();
		advance(now);
		return active != null && now >= active.visibleAt() ? active : null;
	}

	private void detectAndBroadcast(LocalCollection.Snapshot snapshot, long now)
	{
		if (!snapshot.available())
		{
			return;
		}
		Map<String, LocalCollection.CardInstance> current = index(snapshot.instances());
		for (List<Pull> pulls : detectRecentPacks(previousOpenedPacks, previousInstances, snapshot, now))
		{
			if (!pulls.isEmpty() && pulls.size() <= MAX_PULLS)
			{
				if (config.broadcastPackReveals())
				{
					hostedSync.queueLocalPack(pulls);
				}
			}
		}
		previousOpenedPacks = snapshot.openedPacks();
		previousInstances = current;
	}

	static List<List<Pull>> detectRecentPacks(long previousOpenedPacks,
		Map<String, LocalCollection.CardInstance> previousInstances,
		LocalCollection.Snapshot snapshot, long now)
	{
		if (snapshot == null || !snapshot.available() || snapshot.openedPacks() <= previousOpenedPacks)
		{
			return Collections.emptyList();
		}
		Set<String> namesBeforePack = new HashSet<>();
		for (LocalCollection.CardInstance old : previousInstances.values())
		{
			namesBeforePack.add(old.normalizedName());
		}

		Map<Long, List<LocalCollection.CardInstance>> instancesByTime = new LinkedHashMap<>();
		for (LocalCollection.CardInstance instance : snapshot.instances())
		{
			if (previousInstances.containsKey(instance.id()) || instance.pulledAt() <= 0L
				|| now - instance.pulledAt() > MAX_RECENT_PULL_AGE_MILLIS
				|| instance.pulledAt() - now > 5_000L)
			{
				continue;
			}
			instancesByTime.computeIfAbsent(instance.pulledAt(), ignored -> new ArrayList<>()).add(instance);
		}

		List<List<Pull>> packs = new ArrayList<>();
		for (List<LocalCollection.CardInstance> instances : instancesByTime.values())
		{
			List<Pull> pulls = new ArrayList<>(instances.size());
			for (LocalCollection.CardInstance instance : instances)
			{
				pulls.add(new Pull(instance.displayName(), instance.foil(),
					!namesBeforePack.contains(instance.normalizedName()), instance.id(), instance.pulledAt()));
			}
			for (LocalCollection.CardInstance instance : instances)
			{
				namesBeforePack.add(instance.normalizedName());
			}
			packs.add(pulls);
		}
		return packs;
	}

	synchronized void hostedReveal(String eventId, String opener, List<Pull> pulls,
		long openedAt, long receivedAt)
	{
		if (!started || !config.showPackReveals() || eventId == null || eventId.trim().isEmpty()
			|| opener == null || opener.trim().isEmpty() || pulls == null || pulls.isEmpty()
			|| pulls.size() > MAX_PULLS || hostedEvents.containsKey(eventId))
		{
			return;
		}
		List<Pull> safePulls = new ArrayList<>(pulls.size());
		List<String> artNames = new ArrayList<>(pulls.size());
		for (Pull pull : pulls)
		{
			if (pull == null || pull.cardName() == null)
			{
				return;
			}
			CardVisualCatalog.CardVisual card = visualCatalog.find(pull.cardName());
			if (card == null)
			{
				return;
			}
			safePulls.add(new Pull(card.displayName(), pull.foil(), pull.newForCollection()));
			artNames.add(card.displayName());
		}
		hostedEvents.put(eventId, Boolean.TRUE);
		while (hostedEvents.size() > 256)
		{
			hostedEvents.remove(hostedEvents.keySet().iterator().next());
		}
		enqueue(opener.trim(), safePulls, artNames,
			remoteRevealNotBefore(openedAt, receivedAt, System.currentTimeMillis()));
	}

	static long remoteRevealNotBefore(long openedAt, long receivedAt, long now)
	{
		long reliableEventTime = receivedAt > 0L ? receivedAt : (openedAt > 0L ? openedAt : now);
		return Math.max(reliableEventTime, openedAt) + REMOTE_REVEAL_DELAY_MILLIS;
	}

	private void enqueue(String opener, List<Pull> safePulls, List<String> artNames, long notBefore)
	{
		cardArt.preload(artNames);
		if (queue.size() >= MAX_QUEUED_REVEALS)
		{
			queue.removeFirst();
		}
		queue.addLast(new QueuedReveal(opener, safePulls, notBefore));
		if (active == null)
		{
			activateNext(System.currentTimeMillis());
		}
	}

	private void advance(long now)
	{
		if (active != null && now >= active.expiresAt())
		{
			active = null;
		}
		if (active == null)
		{
			activateNext(now);
		}
	}

	private void activateNext(long now)
	{
		QueuedReveal next = queue.peekFirst();
		if (next == null)
		{
			return;
		}
		if (now < next.notBefore)
		{
			return;
		}
		queue.removeFirst();
		long visibleAt = Math.max(next.notBefore, now + ART_WARMUP_MILLIS);
		long durationMillis = Math.max(3, Math.min(15, config.packRevealDuration())) * 1_000L;
		active = new RevealView(next.opener, next.pulls, visibleAt, visibleAt + durationMillis);
	}

	private void prime(LocalCollection.Snapshot snapshot)
	{
		previousOpenedPacks = snapshot.openedPacks();
		previousInstances = index(snapshot.instances());
	}

	private static Map<String, LocalCollection.CardInstance> index(List<LocalCollection.CardInstance> instances)
	{
		Map<String, LocalCollection.CardInstance> indexed = new HashMap<>();
		for (LocalCollection.CardInstance instance : instances)
		{
			indexed.put(instance.id(), instance);
		}
		return Collections.unmodifiableMap(indexed);
	}

	static final class Pull
	{
		private final String cardName;
		private final boolean foil;
		private final boolean newForCollection;
		private final String sourceInstanceId;
		private final long pulledAt;

		Pull(String cardName, boolean foil, boolean newForCollection)
		{
			this(cardName, foil, newForCollection, "", 0L);
		}

		Pull(String cardName, boolean foil, boolean newForCollection, String sourceInstanceId, long pulledAt)
		{
			this.cardName = cardName;
			this.foil = foil;
			this.newForCollection = newForCollection;
			this.sourceInstanceId = sourceInstanceId == null ? "" : sourceInstanceId;
			this.pulledAt = pulledAt;
		}

		String cardName() { return cardName; }
		boolean foil() { return foil; }
		boolean newForCollection() { return newForCollection; }
		String sourceInstanceId() { return sourceInstanceId; }
		long pulledAt() { return pulledAt; }
	}

	static final class RevealView
	{
		private final String opener;
		private final List<Pull> pulls;
		private final long visibleAt;
		private final long expiresAt;

		private RevealView(String opener, List<Pull> pulls, long visibleAt, long expiresAt)
		{
			this.opener = opener;
			this.pulls = Collections.unmodifiableList(new ArrayList<>(pulls));
			this.visibleAt = visibleAt;
			this.expiresAt = expiresAt;
		}

		String opener() { return opener; }
		List<Pull> pulls() { return pulls; }
		long visibleAt() { return visibleAt; }
		long expiresAt() { return expiresAt; }

		double remainingFraction(long now)
		{
			long duration = expiresAt - visibleAt;
			return duration <= 0L ? 0d : Math.max(0d, Math.min(1d, (double) (expiresAt - now) / duration));
		}
	}

	private static final class QueuedReveal
	{
		private final String opener;
		private final List<Pull> pulls;
		private final long notBefore;

		private QueuedReveal(String opener, List<Pull> pulls, long notBefore)
		{
			this.opener = opener;
			this.pulls = new ArrayList<>(pulls);
			this.notBefore = notBefore;
		}
	}
}
