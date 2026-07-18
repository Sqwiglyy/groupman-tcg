package com.groupmantcg;

import java.util.Locale;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MultiplayerPrivacyNoticeTest
{
	@Test
	public void warningStatesTrustIpVpnAndSoloBoundaries()
	{
		String warning = String.join(" ", MultiplayerPrivacyNotice.TITLE,
			MultiplayerPrivacyNotice.DESIGN,
			MultiplayerPrivacyNotice.TRUST, MultiplayerPrivacyNotice.EXPOSURE,
			MultiplayerPrivacyNotice.RESPONSIBILITY,
			MultiplayerPrivacyNotice.PROTECTION).toLowerCase(Locale.ROOT);
		assertTrue(warning.contains("privacy warning"));
		assertTrue(warning.contains("no official public"));
		assertTrue(warning.contains("friends you trust"));
		assertTrue(warning.contains("ip address"));
		assertTrue(warning.contains("does not operate, verify, endorse"));
		assertTrue(warning.contains("responsibility"));
		assertTrue(warning.contains("vpn"));
		assertTrue(warning.contains("solo play"));
	}
}
