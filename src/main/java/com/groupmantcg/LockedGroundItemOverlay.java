package com.groupmantcg;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemLayer;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

@Singleton
class LockedGroundItemOverlay extends Overlay
{
	private final Client client;
	private final GroupmanTcgConfig config;
	private final LockedStateService lockedState;
	private final ModelOutlineRenderer outlineRenderer;

	@Inject
	LockedGroundItemOverlay(Client client, GroupmanTcgConfig config, LockedStateService lockedState,
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
		if (lockedState.restrictionsBypassed() || !config.restrictItems() || !config.outlineGroundItems())
		{
			return null;
		}
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return null;
		}
		Scene scene = worldView.getScene();
		Tile[][][] tiles = scene == null ? null : scene.getTiles();
		int plane = worldView.getPlane();
		if (tiles == null || plane < 0 || plane >= tiles.length)
		{
			return null;
		}
		Color configured = config.lockedVisualColor();
		Color outline = new Color(configured.getRed(), configured.getGreen(), configured.getBlue());
		for (Tile[] row : tiles[plane])
		{
			if (row == null)
			{
				continue;
			}
			for (Tile tile : row)
			{
				if (tile == null)
				{
					continue;
				}
				ItemLayer itemLayer = tile.getItemLayer();
				List<TileItem> groundItems = tile.getGroundItems();
				if (itemLayer == null || groundItems == null)
				{
					continue;
				}
				for (TileItem item : groundItems)
				{
					if (lockedState.isItemLocked(item.getId()))
					{
						outlineRenderer.drawOutline(itemLayer, item, config.outlineWidth(),
							outline, config.outlineFeather());
					}
				}
			}
		}
		return null;
	}
}
