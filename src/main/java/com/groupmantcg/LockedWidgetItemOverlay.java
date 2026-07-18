package com.groupmantcg;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

@Singleton
class LockedWidgetItemOverlay extends WidgetItemOverlay
{
	private final Client client;
	private final GroupmanTcgConfig config;
	private final LockedStateService lockedState;

	@Inject
	LockedWidgetItemOverlay(Client client, GroupmanTcgConfig config, LockedStateService lockedState)
	{
		this.client = client;
		this.config = config;
		this.lockedState = lockedState;
		showOnInventory();
		showOnBank();
		showOnEquipment();
		showOnInterfaces(InterfaceID.SHOPMAIN, InterfaceID.OMNISHOP_MAIN, InterfaceID.CHATBOX,
			InterfaceID.SKILL_GUIDE, InterfaceID.SKILL_GUIDE_V2);
	}

	static boolean isShopInterface(int interfaceGroup)
	{
		return interfaceGroup == InterfaceID.SHOPMAIN
			|| interfaceGroup == InterfaceID.OMNISHOP_MAIN;
	}

	static boolean isSkillGuideInterface(int interfaceGroup)
	{
		return interfaceGroup == InterfaceID.SKILL_GUIDE
			|| interfaceGroup == InterfaceID.SKILL_GUIDE_V2;
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		Widget widget = widgetItem.getWidget();
		int interfaceGroup = widget == null ? -1 : WidgetUtil.componentToInterface(widget.getId());
		if (!isItemVisualContext(interfaceGroup, isGrandExchangeOpen())
			|| lockedState.restrictionsBypassed() || !config.restrictItems()
			|| (!config.shadeLockedItems() && !config.showItemLockMarker())
			|| !lockedState.isItemLocked(itemId))
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
		{
			return;
		}

		Graphics2D copy = (Graphics2D) graphics.create();
		try
		{
			copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (config.shadeLockedItems())
			{
				copy.setColor(config.lockedVisualColor());
				copy.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 5, 5);
			}
			if (config.showItemLockMarker())
			{
				drawPadlock(copy, bounds);
			}
		}
		finally
		{
			copy.dispose();
		}
	}

	static boolean isItemVisualContext(int interfaceGroup, boolean grandExchangeOpen)
	{
		return interfaceGroup != InterfaceID.CHATBOX || grandExchangeOpen;
	}

	private boolean isGrandExchangeOpen()
	{
		return client.getWidget(InterfaceID.GE_OFFERS, 0) != null;
	}

	private static void drawPadlock(Graphics2D graphics, Rectangle bounds)
	{
		int size = Math.min(13, Math.max(10, Math.min(bounds.width, bounds.height) / 3));
		int x = bounds.x + bounds.width - size - 1;
		int y = bounds.y + 1;
		graphics.setColor(new Color(24, 24, 24, 220));
		graphics.fillRoundRect(x, y, size, size, 4, 4);

		graphics.setStroke(new BasicStroke(1.5f));
		graphics.setColor(new Color(225, 225, 225));
		int shackleWidth = Math.max(5, size - 6);
		graphics.drawArc(x + 3, y + 2, shackleWidth, 7, 0, 180);
		graphics.fillRoundRect(x + 3, y + 6, size - 6, size - 7, 2, 2);
	}
}
