package com.groupmantcg;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Authenticated server view of the grow-only union and current per-member ownership. */
final class HostedCollectionSnapshot
{
	private final String groupName;
	private final Set<String> unlocks;
	private final Map<String, Map<String, CardDetails>> members;

	HostedCollectionSnapshot(String groupName, Set<String> unlocks,
		Map<String, Map<String, CardDetails>> members)
	{
		this.groupName = groupName == null ? "" : groupName.trim();
		this.unlocks = Collections.unmodifiableSet(new HashSet<>(unlocks));
		Map<String, Map<String, CardDetails>> safeMembers = new HashMap<>();
		for (Map.Entry<String, Map<String, CardDetails>> entry : members.entrySet())
		{
			safeMembers.put(entry.getKey(), Collections.unmodifiableMap(new HashMap<>(entry.getValue())));
		}
		this.members = Collections.unmodifiableMap(safeMembers);
	}

	String groupName()
	{
		return groupName;
	}

	Set<String> unlocks()
	{
		return unlocks;
	}

	Map<String, Map<String, CardDetails>> members()
	{
		return members;
	}

	static final class CardDetails
	{
		private final String cardName;
		private final int copies;
		private final int foilCopies;
		private final int debugCopies;
		private final long firstPulledAt;
		private final long lastPulledAt;

		CardDetails(String cardName, int copies, int foilCopies, int debugCopies,
			long firstPulledAt, long lastPulledAt)
		{
			this.cardName = cardName;
			this.copies = copies;
			this.foilCopies = foilCopies;
			this.debugCopies = debugCopies;
			this.firstPulledAt = firstPulledAt;
			this.lastPulledAt = lastPulledAt;
		}

		String cardName() { return cardName; }
		int copies() { return copies; }
		int foilCopies() { return foilCopies; }
		int debugCopies() { return debugCopies; }
		long firstPulledAt() { return firstPulledAt; }
		long lastPulledAt() { return lastPulledAt; }

		@Override
		public boolean equals(Object other)
		{
			if (this == other)
			{
				return true;
			}
			if (!(other instanceof CardDetails))
			{
				return false;
			}
			CardDetails that = (CardDetails) other;
			return copies == that.copies && foilCopies == that.foilCopies
				&& debugCopies == that.debugCopies && firstPulledAt == that.firstPulledAt
				&& lastPulledAt == that.lastPulledAt && Objects.equals(cardName, that.cardName);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(cardName, copies, foilCopies, debugCopies,
				firstPulledAt, lastPulledAt);
		}
	}
}
