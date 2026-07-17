package com.groupmantcg;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PrivacyIdentifiersTest
{
	@Test
	public void replacesGroupNamesWithStableOpaquePartyKeys()
	{
		String key = PrivacyIdentifiers.groupKey("Sqwiglyy's HCGIM");
		assertEquals(key, PrivacyIdentifiers.groupKey("  sqwiglyy's   hcgim "));
		assertFalse(key.toLowerCase().contains("sqwiglyy"));
		assertEquals(43, key.length());
	}

	@Test
	public void replacesSourceInstanceIdsBeforeHostedUpload()
	{
		String id = PrivacyIdentifiers.collectionInstance("possible-player-data");
		assertFalse(id.contains("possible-player-data"));
		assertEquals(45, id.length());
	}
}
