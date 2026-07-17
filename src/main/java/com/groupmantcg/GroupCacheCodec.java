package com.groupmantcg;

import com.google.gson.Gson;
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

	String encode(String groupKey, String fingerprint, String bits)
	{
		Cache cache = new Cache();
		cache.schema = 1;
		cache.groupKey = groupKey;
		cache.catalogFingerprint = fingerprint;
		cache.unlockBits = bits;
		return gson.toJson(cache);
	}

	Cache decode(String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		Cache cache = gson.fromJson(json, Cache.class);
		return cache != null && cache.schema == 1 ? cache : null;
	}

	static class Cache
	{
		int schema;
		String groupKey;
		String catalogFingerprint;
		String unlockBits;
	}
}

