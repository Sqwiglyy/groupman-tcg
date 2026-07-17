package com.groupmantcg;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/** The same seven-by-three card-face layout used by OSRS TCG's collection album. */
final class CollectionAlbumGridPanel extends JPanel
{
	static final int PAGE_SIZE = 21;
	private static final int COLS = 7;
	private static final int ROWS = 3;
	private static final int GAP = 5;
	private static final int QTY_LABEL_RESERVE_PX = 18;

	private final CardArtService art;
	private List<Slot> slots = Collections.emptyList();
	private List<Rectangle> lastCardBounds = Collections.emptyList();

	CollectionAlbumGridPanel(CardArtService art)
	{
		this.art = art;
		setOpaque(true);
		setBackground(new Color(0x1E1E1E));
		setMinimumSize(new Dimension(620, 340));
		setPreferredSize(new Dimension(980, 520));
		setToolTipText("");
	}

	void setSlots(List<Slot> next)
	{
		slots = next == null ? Collections.emptyList() : new ArrayList<>(next);
		repaint();
	}

	boolean hasVisibleFoilCards()
	{
		for (Slot slot : slots)
		{
			if (slot.ownership.foilCopies() > 0)
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public String getToolTipText(java.awt.event.MouseEvent event)
	{
		if (event == null)
		{
			return null;
		}
		for (int i = 0; i < lastCardBounds.size() && i < slots.size(); i++)
		{
			Rectangle bounds = lastCardBounds.get(i);
			if (bounds.contains(event.getPoint()))
			{
				Slot slot = slots.get(i);
				CollectionAlbumSnapshot.Ownership owned = slot.ownership;
				if (owned.totalCopies() == 0)
				{
					return slot.card.displayName() + " - Missing";
				}
				return slot.card.displayName() + " - " + quantityDescription(owned);
			}
		}
		return null;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);
		Graphics2D g2 = (Graphics2D) graphics.create();
		List<Rectangle> paintedBounds = new ArrayList<>();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			Insets insets = getInsets();
			int innerWidth = Math.max(0, getWidth() - insets.left - insets.right);
			int innerHeight = Math.max(0, getHeight() - insets.top - insets.bottom);
			if (innerWidth <= 0 || innerHeight <= 0)
			{
				return;
			}
			if (slots.isEmpty())
			{
				g2.setColor(new Color(0xAAAAAA));
				g2.drawString("No cards match the current filters.", insets.left + 16, insets.top + 24);
				return;
			}

			int cellWidth = (innerWidth - (COLS - 1) * GAP) / COLS;
			int cellHeight = (innerHeight - (ROWS - 1) * GAP) / ROWS;
			int contentHeight = Math.max(1, cellHeight - QTY_LABEL_RESERVE_PX);
			double scale = Math.min(
				cellWidth / (double) OsrsTcgCardRenderer.DEFAULT_CARD_WIDTH,
				contentHeight / (double) OsrsTcgCardRenderer.DEFAULT_CARD_HEIGHT) * 0.94d;
			int cardWidth = Math.max(1, (int) Math.round(OsrsTcgCardRenderer.DEFAULT_CARD_WIDTH * scale));
			int cardHeight = Math.max(1, (int) Math.round(OsrsTcgCardRenderer.DEFAULT_CARD_HEIGHT * scale));

			for (int i = 0; i < slots.size() && i < PAGE_SIZE; i++)
			{
				int column = i % COLS;
				int row = i / COLS;
				int cellX = column * (cellWidth + GAP);
				int cellY = row * (cellHeight + GAP);
				int offsetX = cellX + (cellWidth - cardWidth) / 2;
				int offsetY = cellY + (contentHeight - cardHeight) / 2;
				Rectangle bounds = new Rectangle(insets.left + offsetX, insets.top + offsetY,
					cardWidth, cardHeight);
				paintedBounds.add(bounds);

				Slot slot = slots.get(i);
				boolean owned = slot.ownership.totalCopies() > 0;
				boolean foil = slot.ownership.foilCopies() > 0;
				BufferedImage image = art.getCached(slot.card.displayName());
				if (!owned)
				{
					g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
				}
				OsrsTcgCardRenderer.drawCardFace(g2, bounds, slot.card, foil,
					slot.card.rarityColor(), image, 0L, foil);
				if (!owned)
				{
					g2.setComposite(AlphaComposite.SrcOver);
				}

				String quantity = quantityLabel(slot.ownership);
				if (!quantity.isEmpty())
				{
					g2.setColor(new Color(0xDDDDDD));
					g2.setFont(FontManager.getRunescapeSmallFont());
					int textWidth = g2.getFontMetrics().stringWidth(quantity);
					int textX = insets.left + offsetX + (cardWidth - textWidth) / 2;
					int textY = insets.top + offsetY + cardHeight
						+ g2.getFontMetrics().getAscent() + 2;
					g2.drawString(quantity, textX, textY);
				}
			}
		}
		finally
		{
			g2.dispose();
			lastCardBounds = paintedBounds;
		}
	}

	private static String quantityLabel(CollectionAlbumSnapshot.Ownership owned)
	{
		return owned.totalCopies() > 1 ? quantityDescription(owned) : "";
	}

	private static String quantityDescription(CollectionAlbumSnapshot.Ownership owned)
	{
		if (owned.normalCopies() > 0 && owned.foilCopies() > 0)
		{
			return owned.foilCopies() + "x foil, " + owned.normalCopies() + "x normal";
		}
		if (owned.foilCopies() > 0)
		{
			return owned.foilCopies() + "x foil";
		}
		return owned.normalCopies() + "x normal";
	}

	static final class Slot
	{
		private final CardVisualCatalog.CardVisual card;
		private final CollectionAlbumSnapshot.Ownership ownership;

		Slot(CardVisualCatalog.CardVisual card, CollectionAlbumSnapshot.Ownership ownership)
		{
			this.card = card;
			this.ownership = ownership;
		}
	}
}
