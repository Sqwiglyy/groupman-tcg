package com.groupmantcg;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Produces opaque identifiers for transports; raw RuneScape and GIM names stay local. */
final class PrivacyIdentifiers
{
	private PrivacyIdentifiers()
	{
	}

	static String groupKey(String groupName)
	{
		String normalized = EntityCardCatalog.normalize(groupName).replaceAll("\\s+", " ");
		return digest("group\u0000" + normalized);
	}

	static String collectionInstance(String instanceId)
	{
		return "i_" + digest("instance\u0000" + (instanceId == null ? "" : instanceId));
	}

	private static String digest(String value)
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		}
		catch (NoSuchAlgorithmException ex)
		{
			throw new IllegalStateException(ex);
		}
	}
}
