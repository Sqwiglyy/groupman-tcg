package com.groupmantcg;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
						loaded.put(EntityCardCatalog.normalize(name), new CardVisual(name,
							card.imageUrl == null ? "" : card.imageUrl.trim(), isMonster(card.category),
							calculateScore(card.value, card.level, card.overrideScore, isMonster(card.category))));
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

	static final class CardVisual
	{
		private final String displayName;
		private final String imageUrl;
		private final boolean monster;
		private final double score;

		private CardVisual(String displayName, String imageUrl, boolean monster, double score)
		{
			this.displayName = displayName;
			this.imageUrl = imageUrl;
			this.monster = monster;
			this.score = score;
		}

		String displayName() { return displayName; }
		String imageUrl() { return imageUrl; }
		boolean monster() { return monster; }
		double score() { return score; }
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
	}
}
