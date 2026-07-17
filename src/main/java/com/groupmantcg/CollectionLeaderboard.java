package com.groupmantcg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ranks server members by the total score of their unique OSRS TCG cards. */
final class CollectionLeaderboard
{
	private CollectionLeaderboard()
	{
	}

	static List<Entry> rank(Map<String, Set<String>> memberCollections, CardVisualCatalog visuals)
	{
		if (memberCollections == null || memberCollections.isEmpty())
		{
			return Collections.emptyList();
		}

		List<Entry> entries = new ArrayList<>();
		for (Map.Entry<String, Set<String>> member : memberCollections.entrySet())
		{
			String playerName = member.getKey() == null ? "" : member.getKey().trim();
			if (playerName.isEmpty())
			{
				continue;
			}
			Set<String> cards = member.getValue() == null ? Collections.emptySet() : member.getValue();
			double score = 0.0d;
			for (String cardName : cards)
			{
				CardVisualCatalog.CardVisual card = visuals.find(cardName);
				if (card != null)
				{
					score += card.score();
				}
			}
			entries.add(new Entry(playerName, Math.round(score), cards.size()));
		}

		entries.sort(Comparator.comparingLong(Entry::points).reversed()
			.thenComparing(Comparator.comparingInt(Entry::uniqueCards).reversed())
			.thenComparing(Entry::playerName, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(Entry::playerName));
		return Collections.unmodifiableList(entries);
	}

	static final class Entry
	{
		private final String playerName;
		private final long points;
		private final int uniqueCards;

		private Entry(String playerName, long points, int uniqueCards)
		{
			this.playerName = playerName;
			this.points = points;
			this.uniqueCards = uniqueCards;
		}

		String playerName()
		{
			return playerName;
		}

		long points()
		{
			return points;
		}

		int uniqueCards()
		{
			return uniqueCards;
		}
	}
}
