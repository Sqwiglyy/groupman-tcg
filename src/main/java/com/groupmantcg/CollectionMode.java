package com.groupmantcg;

public enum CollectionMode
{
	SOLO("Solo collection"),
	GROUP_IRONMAN("Shared GIM collection");

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

