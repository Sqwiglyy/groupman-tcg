package com.groupmantcg;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SkillRuleCatalogTest
{
	private final InteractionRuleCatalog interactions = new InteractionRuleCatalog(new Gson());
	private final RecipeRuleCatalog recipes = new RecipeRuleCatalog(new Gson());

	@Test
	public void woodcuttingRequiresTheYieldCard()
	{
		InteractionRuleCatalog.Rule rule = interactions.find("object", "Dead tree", "Chop down");
		assertNotNull(rule);
		assertEquals("woodcutting", rule.category());
		assertEquals(Collections.singletonList("Logs"),
			rule.missing(Collections.emptySet(), Collections.emptySet(), false));
		assertTrue(rule.missing(Collections.singleton("logs"), Collections.emptySet(), false).isEmpty());
	}

	@Test
	public void fishingSupportsAnyAndAllDifficulty()
	{
		InteractionRuleCatalog.Rule rule = interactions.find("npc", "Fishing spot", "Bait");
		Set<String> oneFish = Collections.singleton("raw sardine");

		assertTrue(rule.missing(oneFish, Collections.emptySet(), false).isEmpty());
		assertFalse(rule.missing(oneFish, Collections.emptySet(), true).isEmpty());
	}

	@Test
	public void roleExclusionsImplementFarmingAndSlayerModes()
	{
		InteractionRuleCatalog.Rule farming = interactions.find(
			"item-on-object", "Asgarnian seed", "Hops patch");
		Set<String> toolsAndSeed = new HashSet<>();
		toolsAndSeed.add("seed dibber");
		toolsAndSeed.add("asgarnian seed");
		assertTrue(farming.missing(toolsAndSeed, Collections.singleton("produce"), false).isEmpty());
		assertFalse(farming.missing(toolsAndSeed, Collections.emptySet(), false).isEmpty());

		InteractionRuleCatalog.Rule slayer = interactions.find("npc", "Achtryn", "Assignment");
		Set<String> masterOnly = Collections.singleton("achtryn");
		Set<String> excluded = new HashSet<>();
		excluded.add("monsters");
		excluded.add("superiors");
		assertTrue(slayer.missing(masterOnly, excluded, false).isEmpty());
		assertFalse(slayer.missing(masterOnly, Collections.emptySet(), false).isEmpty());
	}

	@Test
	public void recipeScopesAndTinderboxExceptionAreEvaluated()
	{
		RecipeRuleCatalog.Rule smelting = recipes.find("item-on-object", "Copper ore", "Furnace");
		assertNotNull(smelting);
		Set<String> ores = new HashSet<>();
		ores.add("copper ore");
		ores.add("tin ore");
		assertTrue(smelting.missing(ores, true, false, false).isEmpty());
		assertEquals(Collections.singletonList("Bronze bar"), smelting.missing(ores, false, true, false));

		RecipeRuleCatalog.Rule firemaking = recipes.find("item-on-item", "Tinderbox", "Oak logs");
		List<String> strict = firemaking.missing(Collections.singleton("oak logs"), true, false, false);
		assertTrue(strict.contains("Tinderbox"));
		assertTrue(firemaking.missing(Collections.singleton("oak logs"), true, false, true).isEmpty());
	}

	@Test
	public void allSpecialistDatasetsLoad()
	{
		assertTrue(interactions.size() > 400);
		assertTrue(recipes.size() > 300);
		assertEquals(45, interactions.masterFarmerSeeds().size());
	}
}

