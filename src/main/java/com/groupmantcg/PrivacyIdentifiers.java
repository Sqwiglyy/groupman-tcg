package com.groupmantcg;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** Replaces OSRS TCG copy IDs before they are sent to a group server. */
final class PrivacyIdentifiers
{
	private PrivacyIdentifiers()
	{
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
