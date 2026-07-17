package com.groupmantcg;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.party.messages.PartyMemberMessage;

@Data
@EqualsAndHashCode(callSuper = false)
public class TopTrumpsResponseMessage extends PartyMemberMessage
{
	private int protocol;
	private String groupKey;
	private String challengeId;
	private long challengerMemberId;
	private boolean accepted;
}

