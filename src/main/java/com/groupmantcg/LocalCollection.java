package com.groupmantcg;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/** Read-only view of the active RuneScape profile's OSRS TCG collection. */
@Slf4j
@Singleton
class LocalCollection
{
	private static final long CACHE_MILLIS = 3_000L;
	private final ConfigManager configManager;
	private final Gson gson;

	private Set<String> cards = Collections.emptySet();
	private Snapshot snapshot = Snapshot.empty();
	private long refreshedAt;
	private boolean available;

	@Inject
	LocalCollection(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	synchronized Set<String> getCards()
	{
		if (System.currentTimeMillis() - refreshedAt >= CACHE_MILLIS)
		{
			refresh();
		}
		return cards;
	}

	synchronized boolean isAvailable()
	{
		getCards();
		return available;
	}

	synchronized Snapshot snapshot()
	{
		getCards();
		return snapshot;
	}

	synchronized CardSummary summary(String normalizedCardName)
	{
		Snapshot current = snapshot();
		int normalCopies = 0;
		int foilCopies = 0;
		int debugCopies = 0;
		long firstPulledAt = 0L;
		long lastPulledAt = 0L;
		Set<String> pulledBy = new LinkedHashSet<>();
		for (CardInstance instance : current.instances())
		{
			if (!instance.normalizedName().equals(normalizedCardName))
			{
				continue;
			}
			if (instance.foil())
			{
				foilCopies++;
			}
			else
			{
				normalCopies++;
			}
			if (!instance.pulledBy().isEmpty())
			{
				if (isDebugPull(instance.pulledBy()))
				{
					debugCopies++;
				}
				pulledBy.add(formatPulledBy(instance.pulledBy()));
			}
			if (instance.pulledAt() > 0L)
			{
				firstPulledAt = firstPulledAt == 0L
					? instance.pulledAt() : Math.min(firstPulledAt, instance.pulledAt());
				lastPulledAt = Math.max(lastPulledAt, instance.pulledAt());
			}
		}
		int copies = normalCopies + foilCopies;
		return copies == 0 ? null : new CardSummary(normalCopies, foilCopies, debugCopies, firstPulledAt,
			lastPulledAt, pulledBy);
	}

	synchronized void invalidate()
	{
		refreshedAt = 0L;
	}

	private void refresh()
	{
		refreshedAt = System.currentTimeMillis();
		try
		{
			String stored = configManager.getRSProfileConfiguration("osrstcg", "state");
			String json = StoredTcgStateDecoder.decode(stored);
			StoredTcgState state = json.isEmpty() ? null : gson.fromJson(json, StoredTcgState.class);
			if (state == null || state.cardInstances == null)
			{
				clear();
				return;
			}

			Set<String> names = new HashSet<>();
			List<CardInstance> instances = new ArrayList<>();
			for (StoredTcgState.CardInstance card : state.cardInstances)
			{
				if (card != null && card.cardName != null && !card.cardName.trim().isEmpty())
				{
					String displayName = card.cardName.trim();
					String normalizedName = displayName.toLowerCase(Locale.ROOT);
					names.add(normalizedName);
					if (card.id != null && !card.id.trim().isEmpty())
					{
						instances.add(new CardInstance(card.id.trim(), displayName, normalizedName,
							card.foil, card.pulledBy == null ? "" : card.pulledBy.trim(), card.pulledAt));
					}
				}
			}
			cards = Collections.unmodifiableSet(names);
			snapshot = new Snapshot(state.openedPacks, instances, true);
			available = true;
		}
		catch (Exception ex)
		{
			clear();
			log.debug("Unable to read OSRS TCG collection", ex);
		}
	}

	private void clear()
	{
		cards = Collections.emptySet();
		snapshot = Snapshot.empty();
		available = false;
	}

	private static String formatPulledBy(String pulledBy)
	{
		String clean = pulledBy.trim();
		return isDebugPull(clean) ? "Debug_" + clean.substring("DEBUG_".length()) : clean;
	}

	private static boolean isDebugPull(String pulledBy)
	{
		return pulledBy.regionMatches(true, 0, "DEBUG_", 0, "DEBUG_".length());
	}

	static final class Snapshot
	{
		private final long openedPacks;
		private final List<CardInstance> instances;
		private final boolean available;

		Snapshot(long openedPacks, List<CardInstance> instances, boolean available)
		{
			this.openedPacks = Math.max(0L, openedPacks);
			this.instances = Collections.unmodifiableList(new ArrayList<>(instances));
			this.available = available;
		}

		static Snapshot empty()
		{
			return new Snapshot(0L, Collections.emptyList(), false);
		}

		long openedPacks()
		{
			return openedPacks;
		}

		List<CardInstance> instances()
		{
			return instances;
		}

		boolean available()
		{
			return available;
		}
	}

	static final class CardInstance
	{
		private final String id;
		private final String displayName;
		private final String normalizedName;
		private final boolean foil;
		private final String pulledBy;
		private final long pulledAt;

		CardInstance(String id, String displayName, String normalizedName, boolean foil,
			String pulledBy, long pulledAt)
		{
			this.id = id;
			this.displayName = displayName;
			this.normalizedName = normalizedName;
			this.foil = foil;
			this.pulledBy = pulledBy;
			this.pulledAt = pulledAt;
		}

		String id() { return id; }
		String displayName() { return displayName; }
		String normalizedName() { return normalizedName; }
		boolean foil() { return foil; }
		String pulledBy() { return pulledBy; }
		long pulledAt() { return pulledAt; }
	}

	static final class CardSummary
	{
		private final int normalCopies;
		private final int foilCopies;
		private final int debugCopies;
		private final long firstPulledAt;
		private final long lastPulledAt;
		private final Set<String> pulledBy;

		private CardSummary(int normalCopies, int foilCopies, int debugCopies, long firstPulledAt,
			long lastPulledAt, Set<String> pulledBy)
		{
			this.normalCopies = normalCopies;
			this.foilCopies = foilCopies;
			this.debugCopies = debugCopies;
			this.firstPulledAt = firstPulledAt;
			this.lastPulledAt = lastPulledAt;
			this.pulledBy = Collections.unmodifiableSet(new LinkedHashSet<>(pulledBy));
		}

		int normalCopies() { return normalCopies; }
		int foilCopies() { return foilCopies; }
		int debugCopies() { return debugCopies; }
		int copies() { return normalCopies + foilCopies; }
		long firstPulledAt() { return firstPulledAt; }
		long lastPulledAt() { return lastPulledAt; }
		Set<String> pulledBy() { return pulledBy; }
	}
}
