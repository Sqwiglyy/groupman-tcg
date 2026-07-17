package com.groupmantcg;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.runelite.client.party.messages.PartyMemberMessage;

@Data
@EqualsAndHashCode(callSuper = false)
public class GroupPackRevealMessage extends PartyMemberMessage
{
	private int protocol;
	private String groupKey;
	private List<CardPull> pulls;

	@Data
	public static class CardPull
	{
		private String cardName;
		private boolean foil;
		private boolean newForCollection;
	}
}
