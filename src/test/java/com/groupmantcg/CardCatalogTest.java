package com.groupmantcg;

import com.google.gson.Gson;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CardCatalogTest
{
	@Test
	public void resolvesTrackedEntitiesAndPotionDoses()
	{
		ItemCardCatalog items = new ItemCardCatalog(new Gson());
		MonsterCardCatalog monsters = new MonsterCardCatalog(new Gson());

		assertTrue(monsters.cardsFor("Abyssal demon").contains("abyssal demon"));
		assertTrue(items.cardsFor("Attack potion(3)").contains("attack potion"));
		assertTrue(items.cardsFor("Bronze med helm").contains("bronze med helm"));
		assertTrue(items.cardsFor("Definitely not an item").isEmpty());
	}

	@Test
	public void fullCollectionSnapshotIsCompactAndRoundTrips()
	{
		Gson gson = new Gson();
		ItemCardCatalog items = new ItemCardCatalog(gson);
		MonsterCardCatalog monsters = new MonsterCardCatalog(gson);
		CardBitsetIndex index = new CardBitsetIndex(monsters, items);
		Set<String> allCards = new HashSet<>();
		items.entries().values().forEach(allCards::addAll);
		monsters.entries().values().forEach(allCards::addAll);

		String encoded = index.encode(allCards);

		assertTrue(encoded.length() < 1_200);
		assertEquals(allCards, index.decode(encoded));
		assertFalse(index.fingerprint().isEmpty());
	}

	@Test
	public void visualCatalogExposesEveryUniqueCardForTheAlbum()
	{
		CardVisualCatalog visuals = new CardVisualCatalog(new Gson());

		assertEquals(visuals.size(), visuals.all().size());
		assertTrue(visuals.all().size() > 6_000);
	}
}
