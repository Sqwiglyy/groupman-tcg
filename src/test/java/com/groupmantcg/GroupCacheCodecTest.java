package com.groupmantcg;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GroupCacheCodecTest
{
	private final GroupCacheCodec codec = new GroupCacheCodec(new Gson());

	@Test
	public void roundTripsCurrentSchema()
	{
		GroupCacheCodec.Cache cache = codec.decode(codec.encode("group", "catalog", "bits"));
		assertEquals("group", cache.groupKey);
		assertEquals("catalog", cache.catalogFingerprint);
		assertEquals("bits", cache.unlockBits);
	}

	@Test
	public void ignoresUnknownSchema()
	{
		assertNull(codec.decode("{\"schema\":2}"));
	}
}

