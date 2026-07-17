package com.groupmantcg;

/** Private, RuneScape-profile-scoped credentials and sync cursors. */
final class HostedProfile
{
	String groupId;
	String groupName;
	String memberId;
	String memberLabel;
	String rsn;
	String role;
	String status;
	String token;
	String serverUrl;
	String inviteCode;
	long inviteExpiresAt;
	long eventCursor;
	long topTrumpsCursor;
	long collectionVersion;
	String lastUploadedFingerprint;

	boolean valid()
	{
		return notBlank(groupId) && notBlank(memberId) && notBlank(rsn) && notBlank(token);
	}

	boolean owner()
	{
		return "owner".equals(role);
	}

	boolean approved()
	{
		return "approved".equals(status);
	}

	private static boolean notBlank(String value)
	{
		return value != null && !value.trim().isEmpty();
	}
}
