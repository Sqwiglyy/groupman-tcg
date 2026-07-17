package com.groupmantcg;

import com.google.gson.Gson;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GroupCacheCodecTest
{
	private final GroupCacheCodec codec = new GroupCacheCodec(new Gson());

	@Test
	public void roundTripsCurrentSchema()
	{
		GroupCacheCodec.Cache cache = codec.decode(codec.encode("group", "catalog", "bits",
			Map.of("Example Player", "personal-bits")));
		assertEquals("group", cache.groupKey);
		assertEquals("catalog", cache.catalogFingerprint);
		assertEquals("bits", cache.unlockBits);
		assertEquals("personal-bits", cache.memberUnlockBits.get("Example Player"));
	}

	@Test
	public void readsLegacyUnionWithoutInventingMemberOwnership()
	{
		GroupCacheCodec.Cache cache = codec.decode(
			"{\"schema\":1,\"groupKey\":\"group\",\"catalogFingerprint\":\"catalog\",\"unlockBits\":\"bits\"}");
		assertEquals("bits", cache.unlockBits);
		assertEquals(Map.of(), cache.memberUnlockBits);
	}

	@Test
	public void ignoresUnknownSchema()
	{
		assertNull(codec.decode("{\"schema\":3}"));
	}
}
