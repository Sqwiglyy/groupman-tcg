package com.groupmantcg;

import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ItemRestrictionRoutingTest
{
	@Test
	public void routesGroundItemTakeAndTargetedAcquisition()
	{
		assertTrue(GroupmanTcgPlugin.isGroundItemAcquisition(
			MenuAction.GROUND_ITEM_THIRD_OPTION, "take"));
		assertTrue(GroupmanTcgPlugin.isGroundItemAcquisition(
			MenuAction.WIDGET_TARGET_ON_GROUND_ITEM, "cast"));
		assertFalse(GroupmanTcgPlugin.isGroundItemAcquisition(
			MenuAction.GROUND_ITEM_THIRD_OPTION, "examine"));
		assertFalse(GroupmanTcgPlugin.isGroundItemAcquisition(MenuAction.WALK, "take"));
	}

	@Test
	public void routesOnlyGrandExchangeSearchWidgetOperations()
	{
		assertTrue(GroupmanTcgPlugin.isGrandExchangeSearchOperation(
			MenuAction.CC_OP, InterfaceID.CHATBOX, true));
		assertTrue(GroupmanTcgPlugin.isGrandExchangeSearchOperation(
			MenuAction.CC_OP_LOW_PRIORITY, InterfaceID.CHATBOX, true));
		assertFalse(GroupmanTcgPlugin.isGrandExchangeSearchOperation(
			MenuAction.CC_OP, InterfaceID.CHATBOX, false));
		assertFalse(GroupmanTcgPlugin.isGrandExchangeSearchOperation(
			MenuAction.CC_OP, InterfaceID.INVENTORY, true));
	}

	@Test
	public void routesShopPurchasesAcrossShopInterfaces()
	{
		assertTrue(GroupmanTcgPlugin.isShopPurchase(
			MenuAction.CC_OP, InterfaceID.SHOPMAIN, "buy 1"));
		assertTrue(GroupmanTcgPlugin.isShopPurchase(
			MenuAction.CC_OP_LOW_PRIORITY, InterfaceID.OMNISHOP_MAIN, "buy 50"));
		assertFalse(GroupmanTcgPlugin.isShopPurchase(
			MenuAction.CC_OP, InterfaceID.SHOPMAIN, "value"));
		assertFalse(GroupmanTcgPlugin.isShopPurchase(
			MenuAction.CC_OP, InterfaceID.INVENTORY, "buy 1"));
	}
}
