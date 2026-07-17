package com.groupmantcg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Authenticated server view of the grow-only union and current per-member ownership. */
final class HostedCollectionSnapshot
{
	private final String groupId;
	private final String groupName;
	private final String localPlayerName;
	private final Set<String> unlocks;
	private final Map<String, Map<String, CardDetails>> members;
	private final Map<String, List<RecentCard>> recentCards;

	HostedCollectionSnapshot(String groupId, String groupName, String localPlayerName, Set<String> unlocks,
		Map<String, Map<String, CardDetails>> members)
	{
		this(groupId, groupName, localPlayerName, unlocks, members, Collections.emptyMap());
	}

	HostedCollectionSnapshot(String groupId, String groupName, String localPlayerName, Set<String> unlocks,
		Map<String, Map<String, CardDetails>> members, Map<String, List<RecentCard>> recentCards)
	{
		this.groupId = groupId == null ? "" : groupId.trim();
		this.groupName = groupName == null ? "" : groupName.trim();
		this.localPlayerName = localPlayerName == null ? "" : localPlayerName.trim();
		this.unlocks = Collections.unmodifiableSet(new HashSet<>(unlocks));
		Map<String, Map<String, CardDetails>> safeMembers = new HashMap<>();
		for (Map.Entry<String, Map<String, CardDetails>> entry : members.entrySet())
		{
			safeMembers.put(entry.getKey(), Collections.unmodifiableMap(new HashMap<>(entry.getValue())));
		}
		this.members = Collections.unmodifiableMap(safeMembers);
		Map<String, List<RecentCard>> safeRecentCards = new HashMap<>();
		for (Map.Entry<String, List<RecentCard>> entry : recentCards.entrySet())
		{
			safeRecentCards.put(entry.getKey(), newestCards(entry.getValue(), entry.getValue().size()));
		}
		this.recentCards = Collections.unmodifiableMap(safeRecentCards);
	}

	String groupId()
	{
		return groupId;
	}

	String groupName()
	{
		return groupName;
	}

	String localPlayerName()
	{
		return localPlayerName;
	}

	Set<String> unlocks()
	{
		return unlocks;
	}

	Map<String, Map<String, CardDetails>> members()
	{
		return members;
	}

	Map<String, List<RecentCard>> recentCards()
	{
		return recentCards;
	}

	static List<RecentCard> newestCards(List<RecentCard> cards, int limit)
	{
		if (cards == null || cards.isEmpty() || limit <= 0)
		{
			return Collections.emptyList();
		}
		List<RecentCard> sorted = new ArrayList<>(cards);
		sorted.sort(Comparator.comparingLong(RecentCard::pulledAt).reversed()
			.thenComparing(RecentCard::owner, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(RecentCard::cardName, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(RecentCard::sourceInstanceId));
		if (sorted.size() > limit)
		{
			sorted = new ArrayList<>(sorted.subList(0, limit));
		}
		return Collections.unmodifiableList(sorted);
	}

	static final class RecentCard
	{
		private final String sourceInstanceId;
		private final String cardName;
		private final boolean foil;
		private final long pulledAt;
		private final String owner;

		RecentCard(String sourceInstanceId, String cardName, boolean foil, long pulledAt, String owner)
		{
			this.sourceInstanceId = sourceInstanceId == null ? "" : sourceInstanceId;
			this.cardName = cardName == null ? "" : cardName.trim();
			this.foil = foil;
			this.pulledAt = pulledAt;
			this.owner = owner == null || owner.trim().isEmpty() ? "You" : owner.trim();
		}

		String sourceInstanceId() { return sourceInstanceId; }
		String cardName() { return cardName; }
		boolean foil() { return foil; }
		long pulledAt() { return pulledAt; }
		String owner() { return owner; }

		@Override
		public boolean equals(Object other)
		{
			if (this == other)
			{
				return true;
			}
			if (!(other instanceof RecentCard))
			{
				return false;
			}
			RecentCard that = (RecentCard) other;
			return foil == that.foil && pulledAt == that.pulledAt
				&& Objects.equals(sourceInstanceId, that.sourceInstanceId)
				&& Objects.equals(cardName, that.cardName) && Objects.equals(owner, that.owner);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(sourceInstanceId, cardName, foil, pulledAt, owner);
		}
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
