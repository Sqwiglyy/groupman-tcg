package com.groupmantcg;

import java.awt.Color;
import java.util.Collections;
import java.util.Set;
import net.runelite.api.gameval.InterfaceID;
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

	@Test
	public void lockedItemVisualsRecognizeShopInterfaces()
	{
		assertTrue(LockedWidgetItemOverlay.isShopInterface(InterfaceID.SHOPMAIN));
		assertTrue(LockedWidgetItemOverlay.isShopInterface(InterfaceID.OMNISHOP_MAIN));
		assertFalse(LockedWidgetItemOverlay.isShopInterface(InterfaceID.INVENTORY));
	}

	@Test
	public void chatboxItemVisualsRequireAnOpenGrandExchange()
	{
		assertTrue(LockedWidgetItemOverlay.isItemVisualContext(InterfaceID.CHATBOX, true));
		assertFalse(LockedWidgetItemOverlay.isItemVisualContext(InterfaceID.CHATBOX, false));
		assertTrue(LockedWidgetItemOverlay.isItemVisualContext(InterfaceID.INVENTORY, false));
	}

	@Test
	public void lockedItemVisualsRecognizeBothSkillGuides()
	{
		assertTrue(LockedWidgetItemOverlay.isSkillGuideInterface(InterfaceID.SKILL_GUIDE));
		assertTrue(LockedWidgetItemOverlay.isSkillGuideInterface(InterfaceID.SKILL_GUIDE_V2));
		assertFalse(LockedWidgetItemOverlay.isSkillGuideInterface(InterfaceID.INVENTORY));
	}
}
