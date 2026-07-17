package com.groupmantcg;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StoredTcgStateDecoderTest
{
	private static final byte[] SALT = {
		0x52, 0x4c, 0x54, 0x43, 0x47, 0x7c, 0x6f, 0x73,
		0x72, 0x73, 0x2d, 0x74, 0x63, 0x67, 0x21
	};

	@Test
	public void decodesVersionTwoState() throws Exception
	{
		String json = "{\"cardInstances\":[{\"cardName\":\"Goblin\"}]}";
		assertEquals(json, StoredTcgStateDecoder.decode(encode(json)));
	}

	@Test
	public void rejectsMissingOrMalformedState()
	{
		assertEquals("", StoredTcgStateDecoder.decode(null));
		assertEquals("", StoredTcgStateDecoder.decode("wrong-format"));
		assertEquals("", StoredTcgStateDecoder.decode("RLTCG_v2:not-base64"));
	}

	private static String encode(String json) throws Exception
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(output))
		{
			gzip.write(json.getBytes(StandardCharsets.UTF_8));
		}
		byte[] bytes = output.toByteArray();
		for (int i = 0; i < bytes.length; i++)
		{
			bytes[i] ^= SALT[i % SALT.length];
		}
		return "RLTCG_v2:" + Base64.getEncoder().encodeToString(bytes);
	}
}

