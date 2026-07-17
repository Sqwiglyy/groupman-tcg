package com.groupmantcg;

public final class SkillModes
{
	private SkillModes()
	{
	}

	public enum AnyAll
	{
		OFF("Off"), ANY("Any listed card"), ALL("Every listed card");
		private final String label;
		AnyAll(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	public enum Requirements
	{
		OFF("Off", false, false), INPUTS("Inputs", true, false),
		OUTPUT("Output", false, true), BOTH("Inputs + output", true, true);
		private final String label;
		private final boolean inputs;
		private final boolean output;
		Requirements(String label, boolean inputs, boolean output)
		{
			this.label = label;
			this.inputs = inputs;
			this.output = output;
		}
		public boolean inputs() { return inputs; }
		public boolean output() { return output; }
		@Override public String toString() { return label; }
	}

	public enum Firemaking
	{
		OFF("Off"), LOGS("Logs"), LOGS_AND_TINDERBOX("Logs + Tinderbox");
		private final String label;
		Firemaking(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	public enum Cooking
	{
		OFF("Off"), COOKED("Cooked food"), COOKED_AND_BURNT("Cooked + burnt food");
		private final String label;
		Cooking(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	public enum Thieving
	{
		OFF("Off"), LOOT("Loot cards"), NPC_AND_LOOT("NPC + loot cards");
		private final String label;
		Thieving(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	public enum MasterFarmer
	{
		OFF("Off"), BASIC("Coins + Coin pouch"), ALL_SEEDS("Every seed card");
		private final String label;
		MasterFarmer(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	public enum FarmingRake
	{
		OFF("Off"), TOOLS("Tools"), TOOLS_AND_WEEDS("Tools + Weeds");
		private final String label;
		FarmingRake(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	public enum FarmingPlant
	{
		OFF("Off"), TOOLS("Tools"), TOOLS_AND_SEEDS("Tools + seeds"), ALL("Tools + seeds + produce");
		private final String label;
		FarmingPlant(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	public enum Runecrafting
	{
		OFF("Off"), TALISMAN("Talisman/tiara"), TALISMAN_AND_RUNES("Talisman/tiara + rune");
		private final String label;
		Runecrafting(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	public enum HunterGear
	{
		OFF("Off"), GEAR("Gear"), GEAR_AND_CREATURE("Gear + creature/drop cards");
		private final String label;
		HunterGear(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	public enum Implings
	{
		OFF("Off"), NET("Butterfly net"), NET_AND_JAR("Net + impling jar");
		private final String label;
		Implings(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}

	public enum SailingUpgrades
	{
		OFF("Off"), PARTS("Parts"), PARTS_AND_MATERIALS("Parts + materials"), ALL("Parts + materials + large parts");
		private final String label;
		SailingUpgrades(String label) { this.label = label; }
		@Override public String toString() { return label; }
	}
}

