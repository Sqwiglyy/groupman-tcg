package com.groupmantcg;

import com.google.gson.Gson;
import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** Minimal display metadata from OSRS TCG's public card catalog. */
@Slf4j
@Singleton
class CardVisualCatalog
{
	private final Map<String, CardVisual> cards;

	@Inject
	CardVisualCatalog(Gson gson)
	{
		Map<String, CardVisual> loaded = new HashMap<>();
		List<CardVisual> catalog = new ArrayList<>();
		try (InputStream input = getClass().getResourceAsStream("/osrs_tcg_cards.json"))
		{
			if (input != null)
			{
				SourceCard[] source = gson.fromJson(
					new InputStreamReader(input, StandardCharsets.UTF_8), SourceCard[].class);
				if (source != null)
				{
					for (SourceCard card : source)
					{
						if (card == null || card.name == null || card.name.trim().isEmpty())
						{
							continue;
						}
						String name = card.name.trim();
						boolean monster = isMonster(card.category);
						catalog.add(new CardVisual(name,
							card.imageUrl == null ? "" : card.imageUrl.trim(), monster,
							calculateScore(card.value, card.level, card.overrideScore, monster),
							card.examine == null ? "" : card.examine.trim(), card.value,
							primaryCategory(card.category)));
					}
					Map<String, RarityTier> tiers = displayTiers(catalog);
					for (CardVisual card : catalog)
					{
						card.rarity = tiers.getOrDefault(EntityCardCatalog.normalize(card.displayName),
							RarityTier.COMMON);
						loaded.put(EntityCardCatalog.normalize(card.displayName), card);
					}
				}
			}
		}
		catch (Exception ex)
		{
			log.warn("Unable to load OSRS TCG card visuals", ex);
		}
		cards = Collections.unmodifiableMap(loaded);
	}

	CardVisual find(String name)
	{
		return name == null ? null : cards.get(EntityCardCatalog.normalize(name));
	}

	int size()
	{
		return cards.size();
	}

	static double calculateScore(Long value, Integer level, Long overrideScore, boolean monster)
	{
		double valueScore = value == null ? 0d : value.doubleValue();
		double levelScore;
		if (overrideScore != null)
		{
			levelScore = Math.max(0d, overrideScore.doubleValue());
		}
		else if (level == null)
		{
			levelScore = 0d;
		}
		else
		{
			levelScore = Math.pow(level.doubleValue(), 2d) * (monster ? 1.5d : 1d);
		}
		return Math.max(valueScore, levelScore);
	}

	private static boolean isMonster(List<String> categories)
	{
		if (categories == null)
		{
			return false;
		}
		for (String category : categories)
		{
			if ("monster".equalsIgnoreCase(category))
			{
				return true;
			}
		}
		return false;
	}

	private static String primaryCategory(List<String> categories)
	{
		if (categories == null || categories.isEmpty() || categories.get(0) == null)
		{
			return "unknown";
		}
		String[] parts = categories.get(0).split("&");
		String primary = parts.length == 0 ? "" : parts[0].trim();
		return primary.isEmpty() ? "unknown" : EntityCardCatalog.normalize(primary);
	}

	/**
	 * OSRS TCG's display-tier algorithm: percentiles per primary category,
	 * followed by equal-value/score unification and a global exact-value lift.
	 */
	private static Map<String, RarityTier> displayTiers(List<CardVisual> cards)
	{
		Map<String, RarityTier> result = new HashMap<>();
		Map<String, List<CardVisual>> byCategory = new HashMap<>();
		for (CardVisual card : cards)
		{
			byCategory.computeIfAbsent(card.primaryCategory, ignored -> new ArrayList<>()).add(card);
		}

		for (List<CardVisual> category : byCategory.values())
		{
			List<CardVisual> tiering = new ArrayList<>();
			for (CardVisual card : category)
			{
				if (lowValueExempt(card.value))
				{
					result.put(EntityCardCatalog.normalize(card.displayName), RarityTier.COMMON);
				}
				else
				{
					tiering.add(card);
				}
			}
			tiering.sort((left, right) -> Double.compare(left.score, right.score));
			Map<CardVisual, RarityTier> assigned = new HashMap<>();
			for (int i = 0; i < tiering.size(); i++)
			{
				double percentile = tiering.size() == 1 ? 1.0d : (double) i / (double) (tiering.size() - 1);
				assigned.put(tiering.get(i), tierForPercentile(percentile));
			}
			unifyCategoryTies(tiering, assigned);
			for (CardVisual card : tiering)
			{
				result.put(EntityCardCatalog.normalize(card.displayName),
					assigned.getOrDefault(card, RarityTier.COMMON));
			}
		}

		Map<Long, RarityTier> bestByValue = new HashMap<>();
		for (CardVisual card : cards)
		{
			if (card.value != null)
			{
				bestByValue.merge(card.value,
					result.getOrDefault(EntityCardCatalog.normalize(card.displayName), RarityTier.COMMON),
					CardVisualCatalog::bestTier);
			}
		}
		for (CardVisual card : cards)
		{
			String key = EntityCardCatalog.normalize(card.displayName);
			if (lowValueExempt(card.value))
			{
				result.put(key, RarityTier.COMMON);
			}
			else if (card.value != null)
			{
				result.put(key, bestByValue.getOrDefault(card.value, RarityTier.COMMON));
			}
		}
		return result;
	}

	private static void unifyCategoryTies(List<CardVisual> sorted, Map<CardVisual, RarityTier> assigned)
	{
		Map<Long, RarityTier> bestByValue = new HashMap<>();
		for (CardVisual card : sorted)
		{
			if (card.value != null)
			{
				bestByValue.merge(card.value, assigned.get(card), CardVisualCatalog::bestTier);
			}
		}
		for (CardVisual card : sorted)
		{
			if (card.value != null)
			{
				assigned.put(card, bestByValue.get(card.value));
			}
		}

		int index = 0;
		while (index < sorted.size())
		{
			int end = index + 1;
			long scoreKey = Math.round(sorted.get(index).score);
			while (end < sorted.size() && Math.round(sorted.get(end).score) == scoreKey)
			{
				end++;
			}
			RarityTier best = RarityTier.COMMON;
			for (int i = index; i < end; i++)
			{
				best = bestTier(best, assigned.get(sorted.get(i)));
			}
			for (int i = index; i < end; i++)
			{
				assigned.put(sorted.get(i), best);
			}
			index = end;
		}
	}

	private static boolean lowValueExempt(Long value)
	{
		return value != null && (value == 0L || value == 1L);
	}

	private static RarityTier tierForPercentile(double percentile)
	{
		if (percentile >= 0.98d) return RarityTier.GODLY;
		if (percentile >= 0.95d) return RarityTier.MYTHIC;
		if (percentile >= 0.90d) return RarityTier.LEGENDARY;
		if (percentile >= 0.75d) return RarityTier.EPIC;
		if (percentile >= 0.50d) return RarityTier.RARE;
		if (percentile >= 0.25d) return RarityTier.UNCOMMON;
		return RarityTier.COMMON;
	}

	private static RarityTier bestTier(RarityTier left, RarityTier right)
	{
		if (left == null) return right == null ? RarityTier.COMMON : right;
		if (right == null) return left;
		return left.ordinal() >= right.ordinal() ? left : right;
	}

	private enum RarityTier
	{
		COMMON("Common", new Color(0xFFFFFF)),
		UNCOMMON("Uncommon", new Color(0x2ECC71)),
		RARE("Rare", new Color(0x3498DB)),
		EPIC("Epic", new Color(0x9B59B6)),
		LEGENDARY("Legendary", new Color(0xE74C3C)),
		MYTHIC("Mythic", new Color(0xFF6EC7)),
		GODLY("Godly", new Color(0xF2C94C));

		private final String label;
		private final Color color;

		RarityTier(String label, Color color)
		{
			this.label = label;
			this.color = color;
		}
	}

	static final class CardVisual
	{
		private final String displayName;
		private final String imageUrl;
		private final boolean monster;
		private final double score;
		private final String examine;
		private final Long value;
		private final String primaryCategory;
		private RarityTier rarity = RarityTier.COMMON;

		private CardVisual(String displayName, String imageUrl, boolean monster, double score,
			String examine, Long value, String primaryCategory)
		{
			this.displayName = displayName;
			this.imageUrl = imageUrl;
			this.monster = monster;
			this.score = score;
			this.examine = examine;
			this.value = value;
			this.primaryCategory = primaryCategory;
		}

		String displayName() { return displayName; }
		String imageUrl() { return imageUrl; }
		boolean monster() { return monster; }
		double score() { return score; }
		String examine() { return examine; }
		Color rarityColor() { return rarity.color; }
		String rarityLabel() { return rarity.label; }
		long power() { return Math.round(score); }
	}

	private static final class SourceCard
	{
		private String name;
		private List<String> category;
		private String imageUrl;
		private Integer level;
		private Long value;
		private Long overrideScore;
		private String examine;
	}
}
