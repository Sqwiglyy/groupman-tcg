package com.groupmantcg;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class GroupCacheCodec
{
	private final Gson gson;

	@Inject
	GroupCacheCodec(Gson gson)
	{
		this.gson = gson;
	}

	String encode(String groupKey, String fingerprint, String bits, Map<String, String> memberBits)
	{
		Cache cache = new Cache();
		cache.schema = 2;
		cache.groupKey = groupKey;
		cache.catalogFingerprint = fingerprint;
		cache.unlockBits = bits;
		cache.memberUnlockBits = memberBits == null
			? Collections.emptyMap() : new HashMap<>(memberBits);
		return gson.toJson(cache);
	}

	Cache decode(String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		Cache cache = gson.fromJson(json, Cache.class);
		if (cache == null || (cache.schema != 1 && cache.schema != 2))
		{
			return null;
		}
		if (cache.memberUnlockBits == null)
		{
			cache.memberUnlockBits = Collections.emptyMap();
		}
		return cache;
	}

	static class Cache
	{
		int schema;
		String groupKey;
		String catalogFingerprint;
		String unlockBits;
		Map<String, String> memberUnlockBits;
	}
}
