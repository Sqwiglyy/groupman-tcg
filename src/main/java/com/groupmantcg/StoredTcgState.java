package com.groupmantcg;

import java.util.List;

class StoredTcgState
{
	long openedPacks;
	List<CardInstance> cardInstances;

	static class CardInstance
	{
		String id;
		String cardName;
		boolean foil;
		String pulledBy;
		long pulledAt;
	}
}
