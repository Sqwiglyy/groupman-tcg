package com.groupmantcg;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class PrivacyIdentifiersTest
{
	@Test
	public void replacesSourceInstanceIdsBeforeHostedUpload()
	{
		String id = PrivacyIdentifiers.collectionInstance("possible-player-data");
		assertFalse(id.contains("possible-player-data"));
		org.junit.Assert.assertEquals(45, id.length());
	}
}
