package com.groupmantcg;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TopTrumpsRulesTest
{
	@Test
	public void mirrorsOsrsTcgPowerFormula()
	{
		assertEquals(6d, CardVisualCatalog.calculateScore(null, 2, null, true), 0.001d);
		assertEquals(100d, CardVisualCatalog.calculateScore(100L, 2, null, true), 0.001d);
		assertEquals(500d, CardVisualCatalog.calculateScore(100L, 2, 500L, true), 0.001d);
	}

	@Test
	public void drawsTwoDifferentSharedCardsAndFindsWinner()
	{
		CardVisualCatalog catalog = new CardVisualCatalog(new Gson());
		TopTrumpsRules.Match match = TopTrumpsRules.draw(
			new HashSet<>(Arrays.asList("goblin", "great olm")), catalog, new Random(7L), "duel-1");

		assertNotNull(match);
		assertFalse(match.challengerCard().displayName().equals(match.challengedCard().displayName()));
		String winner = match.winner() == 0
			? match.challengerCard().displayName() : match.challengedCard().displayName();
		assertEquals("Great Olm", winner);
		assertFalse(match.tieBreak());
		assertTrue(catalog.find("Great Olm").power() > catalog.find("Goblin").power());
	}

	@Test
	public void equalPowerAlwaysProducesTheSameTieWinner()
	{
		int first = TopTrumpsRules.winner(1d, 1d, "same-duel", "Card A", "Card B");
		int second = TopTrumpsRules.winner(1d, 1d, "same-duel", "Card A", "Card B");
		assertEquals(first, second);
		assertTrue(first == 0 || first == 1);
	}
}
