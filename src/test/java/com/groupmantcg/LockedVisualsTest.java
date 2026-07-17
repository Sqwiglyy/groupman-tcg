package com.groupmantcg;

import java.awt.Color;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LockedVisualsTest
{
	@Test
	public void coreLockRequiresAnyMatchingCard()
	{
		Set<String> alternatives = Set.of("cow", "cow (hard)");
		assertTrue(LockedStateService.isLocked(alternatives, Collections.emptySet()));
		assertFalse(LockedStateService.isLocked(alternatives, Set.of("cow (hard)")));
		assertFalse(LockedStateService.isLocked(Collections.emptySet(), Collections.emptySet()));
	}

	@Test
	public void visualDefaultsAreVisibleButTranslucent()
	{
		GroupmanTcgConfig config = new GroupmanTcgConfig() { };
		Color color = config.lockedVisualColor();
		assertTrue(config.outlineLockedNpcs());
		assertTrue(config.outlineGroundItems());
		assertTrue(config.shadeLockedItems());
		assertTrue(config.showItemLockMarker());
		assertEquals(128, color.getRed());
		assertEquals(150, color.getAlpha());
		assertEquals(2, config.outlineWidth());
	}
}
