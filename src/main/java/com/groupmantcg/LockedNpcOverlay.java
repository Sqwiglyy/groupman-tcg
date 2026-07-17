package com.groupmantcg;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

@Singleton
class LockedNpcOverlay extends Overlay
{
	private final Client client;
	private final GroupmanTcgConfig config;
	private final LockedStateService lockedState;
	private final ModelOutlineRenderer outlineRenderer;

	@Inject
	LockedNpcOverlay(Client client, GroupmanTcgConfig config, LockedStateService lockedState,
		ModelOutlineRenderer outlineRenderer)
	{
		this.client = client;
		this.config = config;
		this.lockedState = lockedState;
		this.outlineRenderer = outlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (lockedState.restrictionsBypassed() || !config.outlineLockedNpcs()
			|| (!config.restrictCombat() && !config.restrictAllNpcInteractions()))
		{
			return null;
		}
		Color configured = config.lockedVisualColor();
		Color outline = new Color(configured.getRed(), configured.getGreen(), configured.getBlue());
		for (NPC npc : client.getNpcs())
		{
			if (lockedState.isNpcLocked(npc))
			{
				outlineRenderer.drawOutline(npc, config.outlineWidth(),
					outline, config.outlineFeather());
			}
		}
		return null;
	}
}
