package com.groupmantcg;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(GroupmanTcgConfig.GROUP)
public interface GroupmanTcgConfig extends Config
{
	String GROUP = "groupmantcg";

	@ConfigSection(
		name = "Collection",
		description = "Choose whose OSRS TCG cards provide unlocks.",
		position = 0
	)
	String collectionSection = "collectionSection";

	@ConfigSection(
		name = "Restrictions",
		description = "Choose which interactions require cards.",
		position = 1
	)
	String restrictionSection = "restrictionSection";

	@ConfigSection(
		name = "Visuals",
		description = "Make locked NPCs and items immediately recognisable.",
		position = 2
	)
	String visualSection = "visualSection";

	@ConfigSection(name = "Gathering", description = "Woodcutting, Mining, Fishing and Runecrafting rules.", position = 3)
	String gatheringSection = "gatheringSection";

	@ConfigSection(name = "Processing", description = "Cooking, Firemaking, Smithing, Crafting, Enchanting, Fletching and Herblore.", position = 4)
	String processingSection = "processingSection";

	@ConfigSection(name = "Farming", description = "Patch, planting and compost rules.", position = 5)
	String farmingSection = "farmingSection";

	@ConfigSection(name = "Hunter", description = "Creature, trap and rumour-master rules.", position = 6)
	String hunterSection = "hunterSection";

	@ConfigSection(name = "Slayer & Thieving", description = "Slayer masters, monsters, pickpockets and stalls.", position = 7)
	String slayerThievingSection = "slayerThievingSection";

	@ConfigSection(name = "Sailing", description = "Ship upgrades and salvage rules.", position = 8)
	String sailingSection = "sailingSection";

	@ConfigSection(name = "Top Trumps", description = "Consent-based card duels with verified group members.", position = 9)
	String topTrumpsSection = "topTrumpsSection";

	@ConfigItem(
		keyName = "collectionMode",
		name = "Collection mode",
		description = "Solo uses only this account. Shared GIM permanently combines official group members' cards through RuneLite Party.",
		section = collectionSection,
		position = 0
	)
	default CollectionMode collectionMode()
	{
		return CollectionMode.GROUP_IRONMAN;
	}

	@ConfigItem(
		keyName = "syncMessages",
		name = "Show sync messages",
		description = "Show a chat message when another group member adds unlocks.",
		section = collectionSection,
		position = 1
	)
	default boolean syncMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "broadcastPackReveals",
		name = "Share pack openings",
		description = "Send card names, new-card status, and foil status to verified group members in the current RuneLite Party.",
		section = collectionSection,
		position = 2
	)
	default boolean broadcastPackReveals()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPackReveals",
		name = "Show group pack popups",
		description = "Show a miniature card window when another verified group member opens a pack.",
		section = collectionSection,
		position = 3
	)
	default boolean showPackReveals()
	{
		return true;
	}

	@Range(min = 3, max = 15)
	@ConfigItem(
		keyName = "packRevealDuration",
		name = "Pack popup duration",
		description = "Seconds each group pack reveal remains on screen.",
		section = collectionSection,
		position = 4
	)
	default int packRevealDuration()
	{
		return 8;
	}

	@ConfigItem(
		keyName = "topTrumpsEnabled",
		name = "Enable Top Trumps",
		description = "Add a Top Trumps option when right-clicking an online, verified group member and accept incoming challenges.",
		section = topTrumpsSection,
		position = 0
	)
	default boolean topTrumpsEnabled()
	{
		return true;
	}

	@Range(min = 5, max = 20)
	@ConfigItem(
		keyName = "topTrumpsDuration",
		name = "Result duration",
		description = "Seconds an accepted Top Trumps result remains on screen.",
		section = topTrumpsSection,
		position = 1
	)
	default int topTrumpsDuration()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "restrictCombat",
		name = "Lock combat",
		description = "Block attacks, spell casts, and item use on tracked NPCs until their card is unlocked.",
		section = restrictionSection,
		position = 0
	)
	default boolean restrictCombat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "restrictAllNpcInteractions",
		name = "Lock all NPC interactions",
		description = "Also block non-combat interactions with locked tracked NPCs. Examine remains available.",
		section = restrictionSection,
		position = 1
	)
	default boolean restrictAllNpcInteractions()
	{
		return false;
	}

	@ConfigItem(
		keyName = "restrictItems",
		name = "Lock item interactions",
		description = "Block acquiring, withdrawing, equipping, buying, consuming, or using tracked items until their card is unlocked. Examine, Drop, and Destroy remain available.",
		section = restrictionSection,
		position = 2
	)
	default boolean restrictItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "allowBankDeposits",
		name = "Allow bank deposits",
		description = "Allow locked items to be deposited, while withdrawals remain blocked.",
		section = restrictionSection,
		position = 3
	)
	default boolean allowBankDeposits()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chatFeedback",
		name = "Explain blocked actions",
		description = "Show the card needed when an interaction is blocked.",
		section = restrictionSection,
		position = 4
	)
	default boolean chatFeedback()
	{
		return true;
	}

	@ConfigItem(
		keyName = "itemExemptions",
		name = "Item exemptions",
		description = "Comma-separated item names ignored by the general item lock. Specialist skill requirements still apply.",
		section = restrictionSection,
		position = 5
	)
	default String itemExemptions()
	{
		return "Coins";
	}

	@ConfigItem(
		keyName = "allowLms",
		name = "Allow Last Man Standing",
		description = "Suspend restrictions inside a live LMS match where temporary equipment is supplied.",
		section = restrictionSection,
		position = 6
	)
	default boolean allowLms()
	{
		return true;
	}

	@ConfigItem(
		keyName = "outlineLockedNpcs",
		name = "Outline locked NPCs",
		description = "Draw a grey outline around card-locked NPCs.",
		section = visualSection,
		position = 0
	)
	default boolean outlineLockedNpcs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shadeLockedItems",
		name = "Shade locked items",
		description = "Place a translucent grey wash over locked inventory, bank, and equipment items.",
		section = visualSection,
		position = 1
	)
	default boolean shadeLockedItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showItemLockMarker",
		name = "Show item padlocks",
		description = "Draw a small padlock on locked inventory, bank, and equipment items.",
		section = visualSection,
		position = 2
	)
	default boolean showItemLockMarker()
	{
		return true;
	}

	@ConfigItem(
		keyName = "outlineGroundItems",
		name = "Outline ground items",
		description = "Draw a grey model outline around locked items on the ground.",
		section = visualSection,
		position = 3
	)
	default boolean outlineGroundItems()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "lockedVisualColor",
		name = "Locked colour",
		description = "Colour used for locked outlines and item shading. Alpha controls item-shade opacity.",
		section = visualSection,
		position = 4
	)
	default Color lockedVisualColor()
	{
		return new Color(128, 128, 128, 150);
	}

	@Range(min = 1, max = 4)
	@ConfigItem(
		keyName = "outlineWidth",
		name = "Outline width",
		description = "Width of locked NPC and ground-item outlines.",
		section = visualSection,
		position = 5
	)
	default int outlineWidth()
	{
		return 2;
	}

	@Range(min = 0, max = 4)
	@ConfigItem(
		keyName = "outlineFeather",
		name = "Outline softness",
		description = "Softens the edge of locked NPC and ground-item outlines.",
		section = visualSection,
		position = 6
	)
	default int outlineFeather()
	{
		return 1;
	}

	@ConfigItem(keyName = "woodcutting", name = "Woodcutting", description = "Require the log card yielded by a tree.", section = gatheringSection, position = 0)
	default boolean woodcutting() { return true; }

	@ConfigItem(keyName = "mining", name = "Mining", description = "Require the ore or resource card yielded by a rock.", section = gatheringSection, position = 1)
	default boolean mining() { return true; }

	@ConfigItem(keyName = "fishing", name = "Fishing", description = "Require any or every possible fish card for a spot and option.", section = gatheringSection, position = 2)
	default SkillModes.AnyAll fishing() { return SkillModes.AnyAll.ANY; }

	@ConfigItem(keyName = "runecrafting", name = "Runecrafting", description = "Require a talisman/tiara and optionally the crafted rune card.", section = gatheringSection, position = 3)
	default SkillModes.Runecrafting runecrafting() { return SkillModes.Runecrafting.TALISMAN_AND_RUNES; }

	@ConfigItem(keyName = "cooking", name = "Cooking", description = "Require cooked-food cards and optionally burnt variants.", section = processingSection, position = 0)
	default SkillModes.Cooking cooking() { return SkillModes.Cooking.COOKED; }

	@ConfigItem(keyName = "firemaking", name = "Firemaking", description = "Require log cards and optionally Tinderbox.", section = processingSection, position = 1)
	default SkillModes.Firemaking firemaking() { return SkillModes.Firemaking.LOGS; }

	@ConfigItem(keyName = "eventLogs", name = "Include coloured event logs", description = "Apply Firemaking rules to coloured event logs.", section = processingSection, position = 2)
	default boolean eventLogs() { return false; }

	@ConfigItem(keyName = "smelting", name = "Smelting", description = "Choose whether ores, bars, or both require cards.", section = processingSection, position = 3)
	default SkillModes.Requirements smelting() { return SkillModes.Requirements.BOTH; }

	@ConfigItem(keyName = "smithing", name = "Smithing", description = "Choose whether bars, finished items, or both require cards.", section = processingSection, position = 4)
	default SkillModes.Requirements smithing() { return SkillModes.Requirements.BOTH; }

	@ConfigItem(keyName = "crafting", name = "Crafting", description = "Choose input/output card requirements for Crafting recipes.", section = processingSection, position = 5)
	default SkillModes.Requirements crafting() { return SkillModes.Requirements.BOTH; }

	@ConfigItem(keyName = "crushedGem", name = "Require Crushed gem", description = "Require the Crushed gem card for gems that can shatter.", section = processingSection, position = 6)
	default boolean crushedGem() { return false; }

	@ConfigItem(keyName = "enchanting", name = "Enchanting", description = "Choose input/output card requirements for jewellery enchantment.", section = processingSection, position = 7)
	default SkillModes.Requirements enchanting() { return SkillModes.Requirements.BOTH; }

	@ConfigItem(keyName = "fletching", name = "Fletching", description = "Choose input/output card requirements for Fletching recipes.", section = processingSection, position = 8)
	default SkillModes.Requirements fletching() { return SkillModes.Requirements.BOTH; }

	@ConfigItem(keyName = "herblore", name = "Herblore", description = "Choose input/output card requirements for Herblore recipes.", section = processingSection, position = 9)
	default SkillModes.Requirements herblore() { return SkillModes.Requirements.BOTH; }

	@ConfigItem(keyName = "farmingRake", name = "Raking", description = "Require tools and optionally the Weeds card.", section = farmingSection, position = 0)
	default SkillModes.FarmingRake farmingRake() { return SkillModes.FarmingRake.TOOLS_AND_WEEDS; }

	@ConfigItem(keyName = "farmingPlant", name = "Planting", description = "Require tools, seeds, and optionally produce cards.", section = farmingSection, position = 1)
	default SkillModes.FarmingPlant farmingPlant() { return SkillModes.FarmingPlant.TOOLS_AND_SEEDS; }

	@ConfigItem(keyName = "compost", name = "Compost bins", description = "Require the applicable compost card.", section = farmingSection, position = 2)
	default boolean compost() { return true; }

	@ConfigItem(keyName = "hunterBirds", name = "Birds & butterflies", description = "Require gear and optionally creature/drop cards.", section = hunterSection, position = 0)
	default SkillModes.HunterGear hunterBirds() { return SkillModes.HunterGear.GEAR; }

	@ConfigItem(keyName = "implings", name = "Implings", description = "Require a butterfly net and optionally an impling jar.", section = hunterSection, position = 1)
	default SkillModes.Implings implings() { return SkillModes.Implings.NET_AND_JAR; }

	@ConfigItem(keyName = "salamanders", name = "Salamanders", description = "Require rope/net and optionally the salamander card.", section = hunterSection, position = 2)
	default SkillModes.HunterGear salamanders() { return SkillModes.HunterGear.GEAR; }

	@ConfigItem(keyName = "pitfalls", name = "Pitfalls", description = "Require tools and optionally the creature card.", section = hunterSection, position = 3)
	default SkillModes.HunterGear pitfalls() { return SkillModes.HunterGear.GEAR; }

	@ConfigItem(keyName = "chinchompas", name = "Chinchompas", description = "Require the relevant box trap and chinchompa cards.", section = hunterSection, position = 4)
	default boolean chinchompas() { return true; }

	@ConfigItem(keyName = "hunterRumours", name = "Rumour masters", description = "Require every creature card a rumour master can assign.", section = hunterSection, position = 5)
	default boolean hunterRumours() { return false; }

	@ConfigItem(keyName = "slayerMasters", name = "Slayer master cards", description = "Require each Slayer master's own card.", section = slayerThievingSection, position = 0)
	default boolean slayerMasters() { return true; }

	@ConfigItem(keyName = "slayerMonsters", name = "Assigned monster cards", description = "Require all monster cards a Slayer master can assign.", section = slayerThievingSection, position = 1)
	default boolean slayerMonsters() { return false; }

	@ConfigItem(keyName = "slayerSuperiors", name = "Include superior monsters", description = "Also require superior variants when assigned-monster cards are enabled.", section = slayerThievingSection, position = 2)
	default boolean slayerSuperiors() { return false; }

	@ConfigItem(keyName = "pickpocketing", name = "Pickpocketing", description = "Require loot cards or both the NPC and loot cards.", section = slayerThievingSection, position = 3)
	default SkillModes.Thieving pickpocketing() { return SkillModes.Thieving.LOOT; }

	@ConfigItem(keyName = "masterFarmer", name = "Master Farmer", description = "Choose the special Master Farmer requirement.", section = slayerThievingSection, position = 4)
	default SkillModes.MasterFarmer masterFarmer() { return SkillModes.MasterFarmer.BASIC; }

	@ConfigItem(keyName = "stalls", name = "Market stalls", description = "Require any or every card-backed item on a stall's loot table.", section = slayerThievingSection, position = 5)
	default SkillModes.AnyAll stalls() { return SkillModes.AnyAll.ANY; }

	@ConfigItem(keyName = "sailingUpgrades", name = "Ship upgrades", description = "Require ship parts, materials, and optionally large-part cards.", section = sailingSection, position = 0)
	default SkillModes.SailingUpgrades sailingUpgrades() { return SkillModes.SailingUpgrades.PARTS_AND_MATERIALS; }

	@ConfigItem(keyName = "salvaging", name = "Salvaging", description = "Require the salvage card for each shipwreck tier.", section = sailingSection, position = 1)
	default boolean salvaging() { return true; }
}
