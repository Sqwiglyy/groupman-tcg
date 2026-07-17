package com.groupmantcg;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CollectionLeaderboardTest
{
	@Test
	public void ranksServerMembersByUniqueCardPointsThenCollectionSizeAndName()
	{
		CardVisualCatalog visuals = new CardVisualCatalog(new Gson());
		Map<String, Set<String>> members = new LinkedHashMap<>();
		members.put("Charlie", Set.of("Goblin"));
		members.put("Bob", Set.of("Goblin"));
		members.put("Alice", Set.of("Goblin", "Cow"));

		List<CollectionLeaderboard.Entry> leaderboard = CollectionLeaderboard.rank(members, visuals);

		assertEquals(3, leaderboard.size());
		assertEquals("Alice", leaderboard.get(0).playerName());
		assertEquals(2, leaderboard.get(0).uniqueCards());
		assertEquals(Math.round(visuals.find("Goblin").score() + visuals.find("Cow").score()),
			leaderboard.get(0).points());
		assertEquals("Bob", leaderboard.get(1).playerName());
		assertEquals("Charlie", leaderboard.get(2).playerName());
	}

	@Test
	public void keepsUnscoredCardsInTheDisplayedCollectionSize()
	{
		CardVisualCatalog visuals = new CardVisualCatalog(new Gson());
		List<CollectionLeaderboard.Entry> leaderboard = CollectionLeaderboard.rank(
			Map.of("Player", Set.of("Not in the OSRS TCG catalogue")), visuals);

		assertEquals(1, leaderboard.size());
		assertEquals(0L, leaderboard.get(0).points());
		assertEquals(1, leaderboard.get(0).uniqueCards());
	}
}
