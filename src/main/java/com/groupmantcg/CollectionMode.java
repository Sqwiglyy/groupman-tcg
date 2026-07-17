package com.groupmantcg;

public enum CollectionMode
{
	SOLO("Solo collection"),
	// Kept as the stored enum name so existing RuneLite profiles upgrade without losing their choice.
	GROUP_IRONMAN("Shared server collection");

	private final String label;

	CollectionMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
