package com.groupmantcg;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/** Decodes OSRS TCG's public RLTCG_v2 profile-storage representation. */
final class StoredTcgStateDecoder
{
	private static final String PREFIX = "RLTCG_v2:";
	private static final byte[] XOR_SALT = {
		0x52, 0x4c, 0x54, 0x43, 0x47, 0x7c, 0x6f, 0x73,
		0x72, 0x73, 0x2d, 0x74, 0x63, 0x67, 0x21
	};

	private StoredTcgStateDecoder()
	{
	}

	static String decode(String stored)
	{
		if (stored == null || !stored.startsWith(PREFIX))
		{
			return "";
		}
		try
		{
			byte[] compressed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
			for (int i = 0; i < compressed.length; i++)
			{
				compressed[i] ^= XOR_SALT[i % XOR_SALT.length];
			}
			try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed)))
			{
				return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
			}
		}
		catch (IllegalArgumentException | IOException ex)
		{
			return "";
		}
	}
}

