package com.groupmantcg;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;

/** Resolves the core collection locks shared by enforcement and visual overlays. */
@Singleton
class LockedStateService
{
	private static final Set<Integer> LMS_REGIONS = Set.of(
		13658, 13659, 13660, 13914, 13915, 13916, 13918, 13919, 13920,
		14174, 14175, 14176, 14430, 14431, 14432);

	private final Client client;
	private final GroupmanTcgConfig config;
	private final SharedCollectionService collection;
	private final MonsterCardCatalog monsters;
	private final ItemCardCatalog items;
	private final ItemManager itemManager;

	private String exemptionSetting;
	private Set<String> exemptions = Collections.emptySet();

	@Inject
	LockedStateService(Client client, GroupmanTcgConfig config, SharedCollectionService collection,
		MonsterCardCatalog monsters, ItemCardCatalog items, ItemManager itemManager)
	{
		this.client = client;
		this.config = config;
		this.collection = collection;
		this.monsters = monsters;
		this.items = items;
		this.itemManager = itemManager;
	}

	Requirement npcLock(NPC npc)
	{
		String name = npcName(npc);
		return unresolved(name, monsters.cardsFor(name));
	}

	Requirement itemLock(int itemId)
	{
		if (itemId <= 0)
		{
			return null;
		}
		ItemComposition item = itemManager.getItemComposition(itemManager.canonicalize(itemId));
		return item == null ? null : itemLock(item.getName());
	}

	Requirement itemLock(String itemName)
	{
		if (itemName == null || itemName.trim().isEmpty() || isExempt(itemName))
		{
			return null;
		}
		return unresolved(itemName, items.cardsFor(itemName));
	}

	boolean isNpcLocked(NPC npc)
	{
		return npcLock(npc) != null;
	}

	boolean isItemLocked(int itemId)
	{
		return itemLock(itemId) != null;
	}

	boolean restrictionsBypassed()
	{
		if (!config.allowLms())
		{
			return false;
		}
		if (client.getVarbitValue(VarbitID.BR_INGAME) == 1)
		{
			return true;
		}
		for (int region : client.getMapRegions())
		{
			if (LMS_REGIONS.contains(region))
			{
				return true;
			}
		}
		return false;
	}

	private Requirement unresolved(String name, Set<String> required)
	{
		if (!isLocked(required, collection.cards()))
		{
			return null;
		}
		return new Requirement(name, required);
	}

	static boolean isLocked(Set<String> required, Set<String> owned)
	{
		if (required == null || required.isEmpty())
		{
			return false;
		}
		for (String card : required)
		{
			if (owned.contains(card))
			{
				return false;
			}
		}
		return true;
	}

	private boolean isExempt(String itemName)
	{
		String configured = config.itemExemptions() == null ? "" : config.itemExemptions();
		if (!configured.equals(exemptionSetting))
		{
			Set<String> parsed = new HashSet<>();
			for (String value : configured.split(","))
			{
				if (!value.trim().isEmpty())
				{
					parsed.add(value.trim().toLowerCase(Locale.ROOT));
				}
			}
			exemptions = Collections.unmodifiableSet(parsed);
			exemptionSetting = configured;
		}
		return exemptions.contains(itemName.trim().toLowerCase(Locale.ROOT));
	}

	private static String npcName(NPC npc)
	{
		if (npc == null)
		{
			return null;
		}
		NPCComposition transformed = npc.getTransformedComposition();
		if (transformed != null && transformed.getName() != null)
		{
			return transformed.getName();
		}
		return npc.getName();
	}

	static final class Requirement
	{
		private final String target;
		private final Set<String> cards;

		private Requirement(String target, Set<String> cards)
		{
			this.target = target;
			this.cards = cards;
		}

		String target()
		{
			return target;
		}

		Set<String> cards()
		{
			return cards;
		}
	}
}
