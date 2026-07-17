package com.groupmantcg;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** Data-driven processing recipes matched against item and production-interface clicks. */
@Slf4j
@Singleton
class RecipeRuleCatalog
{
	static final String ITEM_ON_ITEM = "item-on-item";
	static final String ITEM_ON_OBJECT = "item-on-object";
	static final String INTERFACE = "interface";
	static final String SPELL_ON_ITEM = "spell-on-item";
	private static final String ANY_TARGET = "*";
	private static final Set<String> COLOURED_LOGS = new HashSet<>(Arrays.asList(
		"blue logs", "green logs", "red logs", "purple logs", "white logs"));

	private Map<String, Rule> rules = Collections.emptyMap();

	@Inject
	RecipeRuleCatalog(Gson gson)
	{
		load(gson);
	}

	Rule find(String kind, String name, String target)
	{
		if (kind == null || name == null)
		{
			return null;
		}
		String cleanKind = normalize(kind);
		String cleanName = normalizeDose(name);
		String cleanTarget = target == null ? ANY_TARGET : normalizeDose(target);
		Rule rule = rules.get(key(cleanKind, cleanName, cleanTarget));
		return rule != null || ANY_TARGET.equals(cleanTarget)
			? rule : rules.get(key(cleanKind, cleanName, ANY_TARGET));
	}

	int size()
	{
		return rules.size();
	}

	private void load(Gson gson)
	{
		try (InputStream input = getClass().getResourceAsStream("/recipe_nodes.json"))
		{
			if (input == null)
			{
				log.warn("Missing recipe_nodes.json; processing restrictions are unavailable");
				return;
			}
			Snapshot snapshot = gson.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot == null || snapshot.recipes == null)
			{
				return;
			}
			Map<String, Rule> loaded = new HashMap<>();
			for (Recipe recipe : snapshot.recipes)
			{
				if (recipe == null || recipe.category == null || recipe.trigger == null
					|| recipe.trigger.kind == null || recipe.trigger.name == null)
				{
					continue;
				}
				List<CardRequirement> inputs = new ArrayList<>();
				if (recipe.inputs != null)
				{
					for (List<String> inputGroup : recipe.inputs)
					{
						CardRequirement requirement = CardRequirement.create(inputGroup, null);
						if (requirement != null)
						{
							inputs.add(requirement);
						}
					}
				}
				String kind = normalize(recipe.trigger.kind);
				String name = normalizeDose(recipe.trigger.name);
				List<String> targets = recipe.trigger.targets == null || recipe.trigger.targets.isEmpty()
					? Collections.singletonList(ANY_TARGET) : recipe.trigger.targets;
				for (String target : targets)
				{
					String cleanTarget = target == null ? ANY_TARGET : normalizeDose(target);
					Rule rule = new Rule(normalize(recipe.category), inputs, recipe.output,
						"firemaking".equals(normalize(recipe.category)) && COLOURED_LOGS.contains(cleanTarget),
						recipe.crushable);
					loaded.put(key(kind, name, cleanTarget), rule);
					if (SPELL_ON_ITEM.equals(kind))
					{
						loaded.put(key(kind, cleanTarget, ANY_TARGET), rule);
					}
					if (INTERFACE.equals(kind))
					{
						loaded.put(key(kind, name, ANY_TARGET), rule);
					}
				}
			}
			rules = Collections.unmodifiableMap(loaded);
			log.info("Loaded {} processing recipe rules", rules.size());
		}
		catch (Exception ex)
		{
			log.warn("Unable to load processing recipe rules", ex);
		}
	}

	private static String key(String kind, String name, String target)
	{
		return kind + '|' + name + '|' + target;
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
		private final List<CardRequirement> inputs;
		private final String outputDisplay;
		private final String outputNormalized;
		private final boolean colouredEventLog;
		private final boolean crushable;

		private Rule(String category, List<CardRequirement> inputs, String output,
			boolean colouredEventLog, boolean crushable)
		{
			this.category = category;
			this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
			this.outputDisplay = output == null ? null : output.trim();
			this.outputNormalized = output == null ? null : output.trim().toLowerCase(Locale.ROOT);
			this.colouredEventLog = colouredEventLog;
			this.crushable = crushable;
		}

		String category()
		{
			return category;
		}

		boolean isColouredEventLog()
		{
			return colouredEventLog;
		}

		boolean isCrushable()
		{
			return crushable;
		}

		List<String> missing(Set<String> owned, boolean requireInputs, boolean requireOutput,
			boolean skipTinderbox)
		{
			List<String> missing = new ArrayList<>();
			if (requireInputs)
			{
				for (CardRequirement input : inputs)
				{
					if (skipTinderbox && input.contains("tinderbox"))
					{
						continue;
					}
					if (!input.isSatisfied(owned))
					{
						missing.add(input.display());
					}
				}
			}
			if (requireOutput && outputNormalized != null && !owned.contains(outputNormalized))
			{
				missing.add(outputDisplay);
			}
			return missing;
		}
	}

	private static class Snapshot
	{
		List<Recipe> recipes;
	}

	private static class Recipe
	{
		String category;
		List<List<String>> inputs;
		String output;
		boolean crushable;
		Trigger trigger;
	}

	private static class Trigger
	{
		String kind;
		String name;
		List<String> targets;
	}
}

