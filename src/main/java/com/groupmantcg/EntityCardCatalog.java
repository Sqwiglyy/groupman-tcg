package com.groupmantcg;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/** Immutable entity-name to OSRS TCG card-name index. */
@Slf4j
abstract class EntityCardCatalog
{
	private Map<String, Set<String>> cardsByEntity = Collections.emptyMap();

	EntityCardCatalog(Gson gson, String resource)
	{
		try (InputStream input = getClass().getResourceAsStream(resource))
		{
			if (input == null)
			{
				log.warn("Missing card catalog {}", resource);
				return;
			}
			Snapshot snapshot = gson.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), Snapshot.class);
			if (snapshot == null || snapshot.entityToCards == null)
			{
				return;
			}

			Map<String, Set<String>> loaded = new HashMap<>();
			for (Map.Entry<String, List<String>> entry : snapshot.entityToCards.entrySet())
			{
				if (entry.getKey() == null || entry.getValue() == null)
				{
					continue;
				}
				Set<String> variants = new HashSet<>();
				for (String card : entry.getValue())
				{
					if (card != null && !card.trim().isEmpty())
					{
						variants.add(normalize(card));
					}
				}
				if (!variants.isEmpty())
				{
					loaded.put(normalize(entry.getKey()), Collections.unmodifiableSet(variants));
				}
			}
			cardsByEntity = Collections.unmodifiableMap(loaded);
		}
		catch (Exception ex)
		{
			log.warn("Unable to load card catalog {}", resource, ex);
		}
	}

	Set<String> cardsFor(String entityName)
	{
		if (entityName == null)
		{
			return Collections.emptySet();
		}
		String normalized = normalize(entityName);
		Set<String> exact = cardsByEntity.get(normalized);
		if (exact != null)
		{
			return exact;
		}
		return cardsByEntity.getOrDefault(normalized.replaceFirst("\\([1-4]\\)$", ""), Collections.emptySet());
	}

	Map<String, Set<String>> entries()
	{
		return cardsByEntity;
	}

	int size()
	{
		return cardsByEntity.size();
	}

	static String normalize(String value)
	{
		return value.replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT);
	}

	private static class Snapshot
	{
		Map<String, List<String>> entityToCards;
	}
}

