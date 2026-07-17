package com.groupmantcg;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CollectionAlbumSnapshotTest
{
	@Test
	public void usesExactCopyAndFoilCountsWhenAvailable()
	{
		Map<String, CollectionAlbumSnapshot.Ownership> ownership = new HashMap<>();
		ownership.put("abyssal demon", new CollectionAlbumSnapshot.Ownership(2, 1));
		CollectionAlbumSnapshot snapshot = new CollectionAlbumSnapshot(
			"Alice", Set.of("abyssal demon"), ownership);

		assertEquals(3, snapshot.ownershipOf("Abyssal demon").totalCopies());
		assertEquals(1, snapshot.ownershipOf("ABYSSAL DEMON").foilCopies());
	}

	@Test
	public void cachedOwnedCardWithoutDetailsStillAppearsOwned()
	{
		CollectionAlbumSnapshot snapshot = new CollectionAlbumSnapshot(
			"Bob", Set.of("goblin"), java.util.Collections.emptyMap());

		assertEquals(1, snapshot.ownershipOf("Goblin").normalCopies());
		assertEquals(0, snapshot.ownershipOf("Cow").totalCopies());
	}

	@Test
	public void playerAlbumUsesRequestedTitle()
	{
		assertEquals("Alice's Collection", CollectionAlbumWindow.titleFor("Alice"));
		assertEquals("Shared Collection", CollectionAlbumWindow.titleFor("Shared collection"));
	}
}
