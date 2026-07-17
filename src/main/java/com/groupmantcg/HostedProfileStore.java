package com.groupmantcg;

import com.google.gson.Gson;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/** Keeps the bearer token out of visible plugin configuration and scopes it to the active RS profile. */
@Singleton
class HostedProfileStore
{
	private static final String KEY = "hostedProfileV1";
	private final ConfigManager configManager;
	private final Gson gson;

	@Inject
	HostedProfileStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	synchronized HostedProfile load()
	{
		try
		{
			String json = configManager.getRSProfileConfiguration(GroupmanTcgConfig.GROUP, KEY);
			HostedProfile profile = json == null ? null : gson.fromJson(json, HostedProfile.class);
			return profile != null && profile.valid() ? profile : null;
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

	synchronized void save(HostedProfile profile)
	{
		if (profile == null || !profile.valid())
		{
			throw new IllegalArgumentException("A complete hosted profile is required");
		}
		configManager.setRSProfileConfiguration(GroupmanTcgConfig.GROUP, KEY, gson.toJson(profile));
	}

	synchronized void clear()
	{
		configManager.unsetRSProfileConfiguration(GroupmanTcgConfig.GROUP, KEY);
	}
}
