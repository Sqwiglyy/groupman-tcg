package com.groupmantcg;

final class GroupSyncStatus
{
	private final boolean groupMode;
	private final boolean active;
	private final boolean inParty;
	private final String groupName;
	private final int cards;
	private final int syncedMembers;
	private final int rosterMembers;
	private final String detail;

	GroupSyncStatus(boolean groupMode, boolean active, boolean inParty, String groupName,
		int cards, int syncedMembers, int rosterMembers, String detail)
	{
		this.groupMode = groupMode;
		this.active = active;
		this.inParty = inParty;
		this.groupName = groupName;
		this.cards = cards;
		this.syncedMembers = syncedMembers;
		this.rosterMembers = rosterMembers;
		this.detail = detail;
	}

	boolean isGroupMode()
	{
		return groupMode;
	}

	boolean isActive()
	{
		return active;
	}

	boolean isInParty()
	{
		return inParty;
	}

	String getGroupName()
	{
		return groupName;
	}

	int getCards()
	{
		return cards;
	}

	int getSyncedMembers()
	{
		return syncedMembers;
	}

	int getRosterMembers()
	{
		return rosterMembers;
	}

	String getDetail()
	{
		return detail;
	}
}

