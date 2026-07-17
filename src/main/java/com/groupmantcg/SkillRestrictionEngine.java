package com.groupmantcg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;

/** Routes menu interactions into the gathering and processing rule catalogs. */
@Singleton
class SkillRestrictionEngine
{
	enum Decision
	{
		NOT_APPLICABLE,
		ALLOWED,
		BLOCKED
	}

	private static final String SEPARATOR = " -> ";
	private static final Set<String> MAKE_VERBS = new HashSet<>(Arrays.asList(
		"smelt", "make", "craft", "smith", "string", "mix", "cook", "bake",
		"fletch", "spin", "fire", "create", "build", "upgrade"));

	private final Client client;
	private final ItemManager itemManager;
	private final GroupmanTcgConfig config;
	private final SharedCollectionService collection;
	private final InteractionRuleCatalog interactions;
	private final RecipeRuleCatalog recipes;
	private final FeedbackService feedback;

	@Inject
	SkillRestrictionEngine(Client client, ItemManager itemManager, GroupmanTcgConfig config,
		SharedCollectionService collection, InteractionRuleCatalog interactions,
		RecipeRuleCatalog recipes, FeedbackService feedback)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.config = config;
		this.collection = collection;
		this.interactions = interactions;
		this.recipes = recipes;
		this.feedback = feedback;
	}

	Decision handle(MenuOptionClicked event)
	{
		MenuEntry entry = event.getMenuEntry();
		NPC npc = entry.getNpc();
		if (npc != null)
		{
			return handleNpc(event, npc);
		}

		MenuAction action = event.getMenuAction();
		if (action == null)
		{
			return Decision.NOT_APPLICABLE;
		}
		switch (action)
		{
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
				return checkInteraction(event, InteractionRuleCatalog.OBJECT,
					clean(event.getMenuTarget()), clean(event.getMenuOption()));
			case ITEM_USE_ON_GAME_OBJECT:
			case WIDGET_TARGET_ON_GAME_OBJECT:
				return handleItemOnObject(event);
			case CC_OP:
			case CC_OP_LOW_PRIORITY:
				return handleWidgetOperation(event);
			case ITEM_USE_ON_ITEM:
			case WIDGET_USE_ON_ITEM:
			case WIDGET_TARGET_ON_WIDGET:
				return handleItemOnItem(event);
			default:
				return Decision.NOT_APPLICABLE;
		}
	}

	private Decision handleNpc(MenuOptionClicked event, NPC npc)
	{
		String name = npcName(npc);
		String option = clean(event.getMenuOption());
		if ("master farmer".equals(EntityCardCatalog.normalize(name)) && "pickpocket".equals(option))
		{
			return checkMasterFarmer(event);
		}
		return checkInteraction(event, InteractionRuleCatalog.NPC, name, option);
	}

	private Decision handleItemOnObject(MenuOptionClicked event)
	{
		String[] parts = splitTarget(event.getMenuTarget());
		if (parts == null)
		{
			return Decision.NOT_APPLICABLE;
		}
		Decision interaction = checkInteraction(event, InteractionRuleCatalog.ITEM_ON_OBJECT, parts[0], parts[1]);
		return interaction != Decision.NOT_APPLICABLE
			? interaction : checkRecipe(event, RecipeRuleCatalog.ITEM_ON_OBJECT, parts[0], parts[1]);
	}

	private Decision handleWidgetOperation(MenuOptionClicked event)
	{
		MenuEntry entry = event.getMenuEntry();
		String option = clean(event.getMenuOption());
		int group = WidgetUtil.componentToInterface(entry.getParam1());
		if (group == InterfaceID.INVENTORY)
		{
			String itemName = itemName(event, entry);
			Decision interaction = checkInteraction(event, InteractionRuleCatalog.INVENTORY, itemName, option);
			if (interaction != Decision.NOT_APPLICABLE)
			{
				return interaction;
			}
			return "light".equals(option)
				? checkRecipe(event, RecipeRuleCatalog.ITEM_ON_ITEM, "Tinderbox", itemName)
				: Decision.NOT_APPLICABLE;
		}

		String product = clean(event.getMenuTarget());
		if (product.isEmpty())
		{
			return Decision.NOT_APPLICABLE;
		}
		if (group == InterfaceID.SKILLMULTI || group == InterfaceID.SMITHING || isMakeVerb(option))
		{
			Decision interaction = checkInteraction(event, InteractionRuleCatalog.INTERFACE,
				product, InteractionRuleCatalog.ANY_OPTION);
			return interaction != Decision.NOT_APPLICABLE
				? interaction : checkRecipe(event, RecipeRuleCatalog.INTERFACE, product, null);
		}
		return Decision.NOT_APPLICABLE;
	}

	private Decision handleItemOnItem(MenuOptionClicked event)
	{
		String[] parts = splitTarget(event.getMenuTarget());
		if (parts == null)
		{
			return Decision.NOT_APPLICABLE;
		}
		if (parts[0].toLowerCase(Locale.ROOT).startsWith("cast ") || selectedWidgetIsSpell())
		{
			return checkRecipe(event, RecipeRuleCatalog.SPELL_ON_ITEM, parts[1], null);
		}
		Decision forwards = checkRecipe(event, RecipeRuleCatalog.ITEM_ON_ITEM, parts[0], parts[1]);
		return forwards != Decision.NOT_APPLICABLE
			? forwards : checkRecipe(event, RecipeRuleCatalog.ITEM_ON_ITEM, parts[1], parts[0]);
	}

	private Decision checkMasterFarmer(MenuOptionClicked event)
	{
		List<String> required;
		switch (config.masterFarmer())
		{
			case BASIC:
				required = Arrays.asList("Coins", "Coin pouch");
				break;
			case ALL_SEEDS:
				required = interactions.masterFarmerSeeds();
				if (required.isEmpty())
				{
					required = Arrays.asList("Coins", "Coin pouch");
				}
				break;
			default:
				return Decision.NOT_APPLICABLE;
		}
		List<String> missing = missingCards(required);
		return consumeIfMissing(event, missing);
	}

	private Decision checkInteraction(MenuOptionClicked event, String kind, String name, String option)
	{
		InteractionRuleCatalog.Rule rule = interactions.find(kind, name, option);
		if (rule == null)
		{
			return Decision.NOT_APPLICABLE;
		}
		Evaluation evaluation = evaluation(rule.category());
		if (evaluation == null)
		{
			return Decision.ALLOWED;
		}
		List<String> missing = rule.missing(collection.cards(), evaluation.excludedRoles, evaluation.requireAll);
		return consumeIfMissing(event, missing);
	}

	private Evaluation evaluation(String category)
	{
		switch (category)
		{
			case "woodcutting": return config.woodcutting() ? Evaluation.full() : null;
			case "mining": return config.mining() ? Evaluation.full() : null;
			case "fishing": return anyAll(config.fishing());
			case "thieving-stalls": return anyAll(config.stalls());
			case "pickpocketing":
				if (config.pickpocketing() == SkillModes.Thieving.LOOT) return Evaluation.excluding("npc");
				return config.pickpocketing() == SkillModes.Thieving.NPC_AND_LOOT ? Evaluation.full() : null;
			case "cooking":
				if (config.cooking() == SkillModes.Cooking.COOKED) return Evaluation.excluding("burnt");
				return config.cooking() == SkillModes.Cooking.COOKED_AND_BURNT ? Evaluation.full() : null;
			case "runecrafting":
				if (config.runecrafting() == SkillModes.Runecrafting.TALISMAN) return Evaluation.excluding("rune");
				return config.runecrafting() == SkillModes.Runecrafting.TALISMAN_AND_RUNES ? Evaluation.full() : null;
			case "farming-rake":
				if (config.farmingRake() == SkillModes.FarmingRake.TOOLS) return Evaluation.excluding("weeds");
				return config.farmingRake() == SkillModes.FarmingRake.TOOLS_AND_WEEDS ? Evaluation.full() : null;
			case "farming-plant":
				switch (config.farmingPlant())
				{
					case TOOLS: return Evaluation.excluding("seed", "produce");
					case TOOLS_AND_SEEDS: return Evaluation.excluding("produce");
					case ALL: return Evaluation.full();
					default: return null;
				}
			case "farming-compost": return config.compost() ? Evaluation.full() : null;
			case "hunter-birds":
			case "hunter-butterflies": return hunterGear(config.hunterBirds(), "creature", "extra");
			case "hunter-implings":
				if (config.implings() == SkillModes.Implings.NET) return Evaluation.excluding("extra");
				return config.implings() == SkillModes.Implings.NET_AND_JAR ? Evaluation.full() : null;
			case "hunter-salamanders": return hunterGear(config.salamanders(), "creature");
			case "hunter-pitfalls": return hunterGear(config.pitfalls(), "creature");
			case "hunter-chins": return config.chinchompas() ? Evaluation.full() : null;
			case "hunter-rumours": return config.hunterRumours() ? Evaluation.full() : null;
			case "slayer": return slayerEvaluation();
			case "sailing-upgrades": return sailingEvaluation();
			case "sailing-salvage": return config.salvaging() ? Evaluation.full() : null;
			default: return Evaluation.full();
		}
	}

	private Evaluation slayerEvaluation()
	{
		Set<String> excluded = new HashSet<>();
		if (!config.slayerMasters()) excluded.add("master");
		if (!config.slayerMonsters()) excluded.add("monsters");
		if (!config.slayerMonsters() || !config.slayerSuperiors()) excluded.add("superiors");
		return excluded.contains("master") && excluded.contains("monsters") ? null : new Evaluation(excluded, false);
	}

	private Evaluation sailingEvaluation()
	{
		switch (config.sailingUpgrades())
		{
			case PARTS: return Evaluation.excluding("material", "large");
			case PARTS_AND_MATERIALS: return Evaluation.excluding("large");
			case ALL: return Evaluation.full();
			default: return null;
		}
	}

	private static Evaluation anyAll(SkillModes.AnyAll mode)
	{
		return mode == SkillModes.AnyAll.OFF ? null : new Evaluation(Collections.emptySet(), mode == SkillModes.AnyAll.ALL);
	}

	private static Evaluation hunterGear(SkillModes.HunterGear mode, String... optionalRoles)
	{
		if (mode == SkillModes.HunterGear.OFF) return null;
		return mode == SkillModes.HunterGear.GEAR ? Evaluation.excluding(optionalRoles) : Evaluation.full();
	}

	private Decision checkRecipe(MenuOptionClicked event, String kind, String name, String target)
	{
		RecipeRuleCatalog.Rule rule = recipes.find(kind, name, target);
		if (rule == null)
		{
			return Decision.NOT_APPLICABLE;
		}
		SkillModes.Requirements mode;
		boolean skipTinderbox = false;
		switch (rule.category())
		{
			case "firemaking":
				if (config.firemaking() == SkillModes.Firemaking.OFF
					|| (rule.isColouredEventLog() && !config.eventLogs())) return Decision.ALLOWED;
				mode = SkillModes.Requirements.INPUTS;
				skipTinderbox = config.firemaking() == SkillModes.Firemaking.LOGS;
				break;
			case "smithing-smelt": mode = config.smelting(); break;
			case "smithing-forge": mode = config.smithing(); break;
			case "crafting": mode = config.crafting(); break;
			case "enchanting": mode = config.enchanting(); break;
			case "fletching": mode = config.fletching(); break;
			case "herblore": mode = config.herblore(); break;
			default: mode = SkillModes.Requirements.BOTH;
		}
		if (mode == SkillModes.Requirements.OFF)
		{
			return Decision.ALLOWED;
		}
		List<String> missing = rule.missing(collection.cards(), mode.inputs(), mode.output(), skipTinderbox);
		if (rule.isCrushable() && config.crushedGem() && !collection.cards().contains("crushed gem"))
		{
			missing.add("Crushed gem");
		}
		return consumeIfMissing(event, missing);
	}

	private Decision consumeIfMissing(MenuOptionClicked event, List<String> missing)
	{
		if (missing.isEmpty())
		{
			return Decision.ALLOWED;
		}
		event.consume();
		feedback.missing(missing);
		return Decision.BLOCKED;
	}

	private List<String> missingCards(List<String> cards)
	{
		Set<String> owned = collection.cards();
		List<String> missing = new ArrayList<>();
		for (String card : cards)
		{
			if (card != null && !owned.contains(card.toLowerCase(Locale.ROOT)))
			{
				missing.add(card);
			}
		}
		return missing;
	}

	private String itemName(MenuOptionClicked event, MenuEntry entry)
	{
		if (entry.isItemOp() && event.getItemId() > 0)
		{
			ItemComposition item = itemManager.getItemComposition(itemManager.canonicalize(event.getItemId()));
			return item == null ? "" : item.getName();
		}
		return clean(event.getMenuTarget());
	}

	private boolean selectedWidgetIsSpell()
	{
		return client.isWidgetSelected() && client.getSelectedWidget() != null
			&& client.getSelectedWidget().getItemId() <= 0;
	}

	private static boolean isMakeVerb(String option)
	{
		for (String verb : MAKE_VERBS)
		{
			if (option.startsWith(verb)) return true;
		}
		return false;
	}

	private static String[] splitTarget(String target)
	{
		String clean = Text.removeTags(target == null ? "" : target);
		int split = clean.lastIndexOf(SEPARATOR);
		return split < 0 ? null : new String[]{clean.substring(0, split).trim(), clean.substring(split + SEPARATOR.length()).trim()};
	}

	private static String npcName(NPC npc)
	{
		NPCComposition transformed = npc.getTransformedComposition();
		return transformed != null && transformed.getName() != null ? transformed.getName() : npc.getName();
	}

	private static String clean(String value)
	{
		return Text.removeTags(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
	}

	private static final class Evaluation
	{
		private final Set<String> excludedRoles;
		private final boolean requireAll;

		private Evaluation(Set<String> excludedRoles, boolean requireAll)
		{
			this.excludedRoles = excludedRoles;
			this.requireAll = requireAll;
		}

		private static Evaluation full()
		{
			return new Evaluation(Collections.emptySet(), false);
		}

		private static Evaluation excluding(String... roles)
		{
			return new Evaluation(new HashSet<>(Arrays.asList(roles)), false);
		}
	}
}
