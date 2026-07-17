package com.groupmantcg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class HostedSyncStatus
{
	enum State
	{
		DISABLED,
		NOT_LINKED,
		WAITING_APPROVAL,
		SYNCING,
		ONLINE,
		WRONG_PROFILE,
		ERROR
	}

	private final State state;
	private final String detail;
	private final String groupName;
	private final String groupId;
	private final boolean owner;
	private final String inviteCode;
	private final long inviteExpiresAt;
	private final long lastSyncedAt;
	private final List<Member> members;

	HostedSyncStatus(State state, String detail, String groupName, String groupId, boolean owner,
		String inviteCode, long inviteExpiresAt, long lastSyncedAt, List<Member> members)
	{
		this.state = state;
		this.detail = detail == null ? "" : detail;
		this.groupName = groupName == null ? "" : groupName;
		this.groupId = groupId == null ? "" : groupId;
		this.owner = owner;
		this.inviteCode = inviteCode == null ? "" : inviteCode;
		this.inviteExpiresAt = inviteExpiresAt;
		this.lastSyncedAt = lastSyncedAt;
		this.members = Collections.unmodifiableList(new ArrayList<>(members));
	}

	static HostedSyncStatus simple(State state, String detail)
	{
		return new HostedSyncStatus(state, detail, "", "", false, "", 0L, 0L,
			Collections.emptyList());
	}

	State state() { return state; }
	String detail() { return detail; }
	String groupName() { return groupName; }
	String groupId() { return groupId; }
	boolean owner() { return owner; }
	String inviteCode() { return inviteCode; }
	long inviteExpiresAt() { return inviteExpiresAt; }
	long lastSyncedAt() { return lastSyncedAt; }
	List<Member> members() { return members; }
	boolean linked() { return !groupId.isEmpty(); }

	static final class Member
	{
		private final String id;
		private final String rsn;
		private final String role;
		private final String status;
		private final boolean revoked;

		Member(String id, String rsn, String role, String status, boolean revoked)
		{
			this.id = id;
			this.rsn = rsn;
			this.role = role;
			this.status = status;
			this.revoked = revoked;
		}

		String id() { return id; }
		String rsn() { return rsn; }
		String role() { return role; }
		String status() { return status; }
		boolean revoked() { return revoked; }
		boolean pending() { return "pending".equals(status) && !revoked; }
	}
}
