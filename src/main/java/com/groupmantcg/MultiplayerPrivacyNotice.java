package com.groupmantcg;

/** User-facing multiplayer privacy language shared by configuration and connection flows. */
final class MultiplayerPrivacyNotice
{
	static final String TITLE = "MULTIPLAYER PRIVACY WARNING";
	static final String DESIGN = "There is no official public Group TCG server by design. "
		+ "Multiplayer uses only a private server chosen by your group.";
	static final String TRUST = "Only connect to private servers run by friends you trust.";
	static final String EXPOSURE = "The server host controls the endpoint and may see or record "
		+ "your IP address and synced Group TCG data.";
	static final String RESPONSIBILITY = "Private servers are operated independently. The Group TCG "
		+ "creator does not operate, verify, endorse, or accept responsibility for servers hosted by others.";
	static final String PROTECTION = "Use a VPN if you need to hide your IP address. "
		+ "Solo play does not contact a group server.";
	static final String CONFIG_DESCRIPTION = "<html><b>MULTIPLAYER PRIVACY WARNING:</b> "
		+ DESIGN + " " + TRUST + " " + EXPOSURE + " " + RESPONSIBILITY + " "
		+ PROTECTION + "</html>";

	private MultiplayerPrivacyNotice()
	{
	}
}
