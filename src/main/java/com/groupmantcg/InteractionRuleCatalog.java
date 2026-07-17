package com.groupmantcg;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** Data-driven rules for gathering nodes and specialist NPC/object interactions. */
@Slf4j
@Singleton
class InteractionRuleCatalog
{
	static final String OBJECT = "object";
	static final String NPC = "npc";
	static final String ITEM_ON_OBJECT = "item-on-object";
	static final String INVENTORY = "inventory";
	static final String INTERFACE = "interface";
	static final String ANY_OPTION = "*";

	private Map<String, Rule> rules = Collections.emptyMap();
	private List<String> masterFarmerSeeds = Collections.emptyList();

	@Inject
	InteractionRuleCatalog(Gson gson)
	{
		load(gson);
	}

	Rule find(String kind, String name, String option)
	{
		if (kind == null || name == null || option == null)
		{
			return null;
		}
		return rules.get(key(normalize(kind), normalizeDose(name), normalize(option)));
	}

	List<String> masterFarmerSeeds()
	{
		return masterFarmerSeeds;
	}

	int size()
	{
		return rules.size();
	}

	private void load(Gson gson)
	{
		try (InputStream input = getClass().getResourceAsStream("/resource_nodes.json"))
		{
			if (input == null)
			{
				log.warn("Missing resource_nodes.json; specialist skill restrictions are unavailable");
				return;
			}
			Snapshot snapshot = gson.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot == null || snapshot.nodes == null)
			{
				return;
			}
			Map<String, Rule> loaded = new HashMap<>();
			for (Node node : snapshot.nodes)
			{
				if (node == null || node.kind == null || node.name == null || node.options == null)
				{
					continue;
				}
				List<CardRequirement> groups = groups(node);
				if (groups.isEmpty())
				{
					continue;
				}
				Rule rule = new Rule(normalize(node.category == null ? "" : node.category), groups);
				for (String option : node.options)
				{
					if (option != null && !option.trim().isEmpty())
					{
						loaded.put(key(normalize(node.kind), normalizeDose(node.name), normalize(option)), rule);
					}
				}
			}
			rules = Collections.unmodifiableMap(loaded);
			if (snapshot.masterFarmerSeedCards != null)
			{
				masterFarmerSeeds = Collections.unmodifiableList(new ArrayList<>(snapshot.masterFarmerSeedCards));
			}
			log.info("Loaded {} specialist interaction rules", rules.size());
		}
		catch (Exception ex)
		{
			log.warn("Unable to load specialist interaction rules", ex);
		}
	}

	private static List<CardRequirement> groups(Node node)
	{
		List<CardRequirement> result = new ArrayList<>();
		if (node.requiredCardGroups != null)
		{
			for (int i = 0; i < node.requiredCardGroups.size(); i++)
			{
				String role = node.groupRoles != null && i < node.groupRoles.size() ? node.groupRoles.get(i) : null;
				CardRequirement group = CardRequirement.create(node.requiredCardGroups.get(i), role);
				if (group != null)
				{
					result.add(group);
				}
			}
		}
		else if (node.requiredCards != null)
		{
			if (node.requireAll)
			{
				for (String card : node.requiredCards)
				{
					CardRequirement group = CardRequirement.create(Collections.singletonList(card), null);
					if (group != null)
					{
						result.add(group);
					}
				}
			}
			else
			{
				CardRequirement group = CardRequirement.create(node.requiredCards, null);
				if (group != null)
				{
					result.add(group);
				}
			}
		}
		return result;
	}

	private static String key(String kind, String name, String option)
	{
		return kind + '|' + name + '|' + option;
	}

	private static String normalize(String value)
	{
		return EntityCardCatalog.normalize(value);
	}

	private static String normalizeDose(String value)
	{
		return normalize(value).replaceFirst("\\([1-4]\\)$", "");
	}

	static final class Rule
	{
		private final String category;
		private final List<CardRequirement> groups;

		private Rule(String category, List<CardRequirement> groups)
		{
			this.category = category;
			this.groups = Collections.unmodifiableList(groups);
		}

		String category()
		{
			return category;
		}

		List<String> missing(Set<String> owned, Set<String> excludedRoles, boolean requireEveryAlternative)
		{
			List<String> missing = new ArrayList<>();
			for (CardRequirement group : groups)
			{
				if (group.role() != null && excludedRoles.contains(group.role()))
				{
					continue;
				}
				if (requireEveryAlternative)
				{
					group.addEveryMissing(owned, missing);
				}
				else if (!group.isSatisfied(owned))
				{
					missing.add(group.display());
				}
			}
			return missing;
		}
	}

	private static class Snapshot
	{
		List<Node> nodes;
		List<String> masterFarmerSeedCards;
	}

	private static class Node
	{
		String category;
		String kind;
		String name;
		List<String> options;
		List<String> requiredCards;
		List<List<String>> requiredCardGroups;
		List<String> groupRoles;
		boolean requireAll;
	}
}

