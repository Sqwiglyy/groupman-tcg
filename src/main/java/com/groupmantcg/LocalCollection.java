package com.groupmantcg;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
}
