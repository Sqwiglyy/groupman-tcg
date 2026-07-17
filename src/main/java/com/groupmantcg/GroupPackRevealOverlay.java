package com.groupmantcg;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.ImageUtil;

@Singleton
class GroupPackRevealOverlay extends Overlay
{
	private static final int PADDING = 8;
	private static final int GAP = 6;
	private static final int HEADER_HEIGHT = 46;
	private static final int CARD_WIDTH = 96;
	private static final int CARD_HEIGHT = 139;
	private static final Color PANEL = new Color(17, 20, 24, 238);
	private static final Color PANEL_BORDER = new Color(184, 115, 51);
	private static final BufferedImage PACK_IMAGE = ImageUtil.loadImageResource(GroupPackRevealOverlay.class,
		"/osrs-tcg/Pack_Standard.png");

	private final GroupPackRevealService reveals;
	private final CardVisualCatalog visuals;
	private final CardArtService art;

	@Inject
	GroupPackRevealOverlay(GroupPackRevealService reveals, CardVisualCatalog visuals, CardArtService art)
	{
		this.reveals = reveals;
		this.visuals = visuals;
		this.art = art;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		GroupPackRevealService.RevealView reveal = reveals.currentReveal();
		if (reveal == null || reveal.pulls().isEmpty())
		{
			return null;
		}

		int count = reveal.pulls().size();
		int width = PADDING * 2 + CARD_WIDTH * count + GAP * Math.max(0, count - 1);
		int height = HEADER_HEIGHT + CARD_HEIGHT + PADDING + 3;
		Graphics2D copy = (Graphics2D) graphics.create();
		try
		{
			copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			copy.setColor(PANEL);
			copy.fillRoundRect(0, 0, width, height, 12, 12);
			copy.setColor(PANEL_BORDER);
			copy.setStroke(new BasicStroke(1.5f));
			copy.drawRoundRect(0, 0, width - 1, height - 1, 12, 12);

			drawHeader(copy, width, reveal.opener());
			for (int i = 0; i < count; i++)
			{
				int x = PADDING + i * (CARD_WIDTH + GAP);
				drawCard(copy, x, HEADER_HEIGHT, reveal.pulls().get(i));
			}

			int remaining = (int) Math.round((width - 2) * reveal.remainingFraction(System.currentTimeMillis()));
			copy.setColor(new Color(232, 185, 92));
			copy.fillRoundRect(1, height - 4, remaining, 3, 3, 3);
		}
		finally
		{
			copy.dispose();
		}
		return new Dimension(width, height);
	}

	private static void drawHeader(Graphics2D graphics, int width, String opener)
	{
		if (PACK_IMAGE != null)
		{
			drawContained(graphics, PACK_IMAGE, PADDING, 3, 17, 28);
		}
		Font font = FontManager.getRunescapeBoldFont();
		graphics.setFont(font);
		String title = "Pack opened by " + opener;
		FontMetrics metrics = graphics.getFontMetrics();
		title = ellipsize(title, metrics, width - PADDING * 2);
		int x = (width - metrics.stringWidth(title)) / 2;
		int y = 22;
		graphics.setColor(new Color(0, 0, 0, 190));
		graphics.drawString(title, x + 1, y + 1);
		graphics.setColor(Color.WHITE);
		graphics.drawString(title, x, y);
	}

	private void drawCard(Graphics2D graphics, int x, int y, GroupPackRevealService.Pull pull)
	{
		CardVisualCatalog.CardVisual visual = visuals.find(pull.cardName());
		if (visual == null)
		{
			graphics.setColor(new Color(35, 37, 41, 250));
			graphics.fillRoundRect(x, y, CARD_WIDTH, CARD_HEIGHT, 8, 8);
			drawPlaceholder(graphics, x, y, CARD_WIDTH, CARD_HEIGHT, pull.cardName());
			return;
		}

		OsrsTcgCardRenderer.drawCardFace(graphics, new Rectangle(x, y, CARD_WIDTH, CARD_HEIGHT), visual,
			pull.foil(), visual.rarityColor(), art.getCached(pull.cardName()));

		if (pull.newForCollection())
		{
			drawBadge(graphics, x + 4, y - 14, "NEW", new Color(45, 126, 72));
		}
		if (pull.foil())
		{
			drawBadgeRight(graphics, x + CARD_WIDTH - 4, y - 14, "FOIL", new Color(91, 93, 151));
		}

	}

	private static void drawContained(Graphics2D graphics, BufferedImage image, int x, int y, int width, int height)
	{
		double scale = Math.min((double) width / image.getWidth(), (double) height / image.getHeight());
		int drawnWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
		int drawnHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
		graphics.drawImage(image, x + (width - drawnWidth) / 2, y + (height - drawnHeight) / 2,
			drawnWidth, drawnHeight, null);
	}

	private static void drawPlaceholder(Graphics2D graphics, int x, int y, int width, int height, String name)
	{
		String initial = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
		graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(24f));
		FontMetrics metrics = graphics.getFontMetrics();
		graphics.setColor(new Color(110, 110, 110));
		graphics.drawString(initial, x + (width - metrics.stringWidth(initial)) / 2,
			y + (height + metrics.getAscent() - metrics.getDescent()) / 2);
	}

	private static void drawBadge(Graphics2D graphics, int x, int y, String text, Color background)
	{
		graphics.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD, 9f));
		FontMetrics metrics = graphics.getFontMetrics();
		int width = metrics.stringWidth(text) + 5;
		graphics.setColor(background);
		graphics.fillRoundRect(x, y, width, 12, 4, 4);
		graphics.setColor(Color.WHITE);
		graphics.drawString(text, x + 3, y + 10);
	}

	private static void drawBadgeRight(Graphics2D graphics, int right, int y, String text, Color background)
	{
		graphics.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD, 9f));
		FontMetrics metrics = graphics.getFontMetrics();
		int width = metrics.stringWidth(text) + 5;
		drawBadge(graphics, right - width, y, text, background);
	}

	private static String ellipsize(String text, FontMetrics metrics, int width)
	{
		if (text == null || metrics.stringWidth(text) <= width)
		{
			return text == null ? "" : text;
		}
		String suffix = "...";
		int length = text.length();
		while (length > 0 && metrics.stringWidth(text.substring(0, length) + suffix) > width)
		{
			length--;
		}
		return text.substring(0, length).trim() + suffix;
	}
}
