package com.groupmantcg;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Immutable ownership data used by the read-only full collection album. */
final class CollectionAlbumSnapshot
{
	private final String displayName;
	private final Set<String> ownedCards;
	private final Map<String, Ownership> ownership;

	CollectionAlbumSnapshot(String displayName, Set<String> ownedCards,
		Map<String, Ownership> ownership)
	{
		this.displayName = displayName == null || displayName.trim().isEmpty()
			? "Shared collection" : displayName.trim();
		this.ownedCards = Collections.unmodifiableSet(new HashSet<>(ownedCards));
		this.ownership = Collections.unmodifiableMap(new HashMap<>(ownership));
	}

	String displayName()
	{
		return displayName;
	}

	Set<String> ownedCards()
	{
		return ownedCards;
	}

	Ownership ownershipOf(String cardName)
	{
		String normalized = EntityCardCatalog.normalize(cardName);
		Ownership exact = ownership.get(normalized);
		if (exact != null)
		{
			return exact;
		}
		return ownedCards.contains(normalized) ? Ownership.ONE_NORMAL : Ownership.NONE;
	}

	static final class Ownership
	{
		private static final Ownership NONE = new Ownership(0, 0);
		private static final Ownership ONE_NORMAL = new Ownership(1, 0);

		private final int normalCopies;
		private final int foilCopies;

		Ownership(int normalCopies, int foilCopies)
		{
			this.normalCopies = Math.max(0, normalCopies);
			this.foilCopies = Math.max(0, foilCopies);
		}

		int normalCopies()
		{
			return normalCopies;
		}

		int foilCopies()
		{
			return foilCopies;
		}

		int totalCopies()
		{
			return normalCopies + foilCopies;
		}

		Ownership plus(Ownership other)
		{
			return other == null ? this
				: new Ownership(normalCopies + other.normalCopies, foilCopies + other.foilCopies);
		}
	}
}
