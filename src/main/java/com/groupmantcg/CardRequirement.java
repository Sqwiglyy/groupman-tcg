package com.groupmantcg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** A requirement satisfied by owning any card in the group. */
final class CardRequirement
{
	private final List<String> displayNames;
	private final List<String> normalizedNames;
	private final String role;

	private CardRequirement(List<String> displayNames, List<String> normalizedNames, String role)
	{
		this.displayNames = Collections.unmodifiableList(displayNames);
		this.normalizedNames = Collections.unmodifiableList(normalizedNames);
		this.role = role;
	}

	static CardRequirement create(List<String> cards, String role)
	{
		if (cards == null)
		{
			return null;
		}
		List<String> display = new ArrayList<>();
		List<String> normalized = new ArrayList<>();
		for (String card : cards)
		{
			if (card != null && !card.trim().isEmpty())
			{
				display.add(card.trim());
				normalized.add(card.trim().toLowerCase(Locale.ROOT));
			}
		}
		if (display.isEmpty())
		{
			return null;
		}
		String cleanRole = role == null || role.trim().isEmpty()
			? null : role.trim().toLowerCase(Locale.ROOT);
		return new CardRequirement(display, normalized, cleanRole);
	}

	boolean isSatisfied(Set<String> owned)
	{
		for (String card : normalizedNames)
		{
			if (owned.contains(card))
			{
				return true;
			}
		}
		return false;
	}

	void addEveryMissing(Set<String> owned, List<String> missing)
	{
		for (int i = 0; i < normalizedNames.size(); i++)
		{
			if (!owned.contains(normalizedNames.get(i)))
			{
				missing.add(displayNames.get(i));
			}
		}
	}

	String display()
	{
		return String.join(" / ", displayNames);
	}

	String role()
	{
		return role;
	}

	boolean contains(String cardName)
	{
		return normalizedNames.contains(cardName.toLowerCase(Locale.ROOT));
	}
}

