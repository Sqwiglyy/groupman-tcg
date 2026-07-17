package com.groupmantcg;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HostedCollectionSnapshotTest
{
	@Test
	public void keepsTwentyNewestIndividualPulls()
	{
		List<HostedCollectionSnapshot.RecentCard> pulls = new ArrayList<>();
		for (int i = 1; i <= 22; i++)
		{
			pulls.add(new HostedCollectionSnapshot.RecentCard("instance-" + i,
				i >= 21 ? "Duplicate card" : "Card " + i, i % 2 == 0, i, "Player"));
		}

		List<HostedCollectionSnapshot.RecentCard> recent =
			HostedCollectionSnapshot.newestCards(pulls, 20);

		assertEquals(20, recent.size());
		assertEquals(22L, recent.get(0).pulledAt());
		assertEquals(21L, recent.get(1).pulledAt());
		assertEquals("Duplicate card", recent.get(0).cardName());
		assertEquals("Duplicate card", recent.get(1).cardName());
		assertEquals(3L, recent.get(19).pulledAt());
	}
}
