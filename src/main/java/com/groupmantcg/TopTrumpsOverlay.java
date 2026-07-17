package com.groupmantcg;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
class TopTrumpsOverlay extends Overlay
{
	private static final int WIDTH = 330;
	private static final int HEIGHT = 202;
	private static final int CARD_WIDTH = 140;
	private static final int CARD_HEIGHT = 145;
	private static final int CARD_Y = 32;
	private static final Color PANEL = new Color(16, 19, 23, 242);
	private static final Color BRONZE = new Color(184, 115, 51);
	private static final Color WINNER = new Color(75, 184, 100);
	private static final Color LOSER = new Color(113, 116, 122);

	private final TopTrumpsService service;
	private final CardArtService art;

	@Inject
	TopTrumpsOverlay(TopTrumpsService service, CardArtService art)
	{
		this.service = service;
		this.art = art;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGH);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		TopTrumpsService.ResultView result = service.currentResult();
		if (result == null)
		{
			return null;
		}

		Graphics2D copy = (Graphics2D) graphics.create();
		try
		{
			copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			copy.setColor(PANEL);
			copy.fillRoundRect(0, 0, WIDTH, HEIGHT, 12, 12);
			copy.setColor(BRONZE);
			copy.setStroke(new BasicStroke(1.5f));
			copy.drawRoundRect(0, 0, WIDTH - 1, HEIGHT - 1, 12, 12);

			drawHeader(copy, result);
			drawCard(copy, 12, CARD_Y, result.challengerName(), result.challengerCard(), result.winner() == 0);
			drawCard(copy, WIDTH - 12 - CARD_WIDTH, CARD_Y, result.challengedName(),
				result.challengedCard(), result.winner() == 1);
			drawVersus(copy);
			drawWinner(copy, result);
		}
		finally
		{
			copy.dispose();
		}
		return new Dimension(WIDTH, HEIGHT);
	}

	private static void drawHeader(Graphics2D graphics, TopTrumpsService.ResultView result)
	{
		graphics.setFont(FontManager.getRunescapeBoldFont());
		String title = "TOP TRUMPS  •  " + result.challengerName() + " vs " + result.challengedName();
		FontMetrics metrics = graphics.getFontMetrics();
		int x = Math.max(8, (WIDTH - metrics.stringWidth(title)) / 2);
		graphics.setColor(Color.BLACK);
		graphics.drawString(title, x + 1, 21);
		graphics.setColor(Color.WHITE);
		graphics.drawString(title, x, 20);
	}

	private void drawCard(Graphics2D graphics, int x, int y, String player,
		CardVisualCatalog.CardVisual card, boolean winner)
	{
		graphics.setColor(new Color(31, 34, 39, 252));
		graphics.fillRoundRect(x, y, CARD_WIDTH, CARD_HEIGHT, 9, 9);
		graphics.setColor(winner ? WINNER : LOSER);
		graphics.setStroke(new BasicStroke(winner ? 2.4f : 1.4f));
		graphics.drawRoundRect(x, y, CARD_WIDTH - 1, CARD_HEIGHT - 1, 9, 9);

		graphics.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		drawCentered(graphics, player, x, y + 14, CARD_WIDTH, winner ? WINNER : Color.LIGHT_GRAY);

		int artX = x + 8;
		int artY = y + 20;
		int artWidth = CARD_WIDTH - 16;
		int artHeight = 76;
		graphics.setColor(new Color(19, 21, 24));
		graphics.fillRoundRect(artX, artY, artWidth, artHeight, 5, 5);
		BufferedImage image = art.getCached(card.displayName());
		if (image == null)
		{
			graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(28f));
			drawCentered(graphics, card.displayName().substring(0, 1).toUpperCase(Locale.ROOT),
				artX, artY + 48, artWidth, new Color(120, 120, 120));
		}
		else
		{
			drawContained(graphics, image, artX + 3, artY + 3, artWidth - 6, artHeight - 6);
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = graphics.getFontMetrics();
		List<String> nameLines = GroupPackRevealOverlay.wrap(card.displayName(), metrics, CARD_WIDTH - 12, 2);
		int nameY = y + 109;
		for (String line : nameLines)
		{
			drawCentered(graphics, line, x, nameY, CARD_WIDTH, Color.WHITE);
			nameY += 11;
		}

		graphics.setFont(FontManager.getRunescapeBoldFont());
		String power = "POWER  " + String.format(Locale.US, "%,d", card.power());
		drawCentered(graphics, power, x, y + CARD_HEIGHT - 8, CARD_WIDTH, winner ? WINNER : new Color(210, 210, 210));
	}

	private static void drawVersus(Graphics2D graphics)
	{
		graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(17f));
		drawCentered(graphics, "VS", 0, 109, WIDTH, BRONZE);
	}

	private static void drawWinner(Graphics2D graphics, TopTrumpsService.ResultView result)
	{
		graphics.setFont(FontManager.getRunescapeBoldFont());
		String text = result.winnerName() + " WINS" + (result.tieBreak() ? " • TIE-BREAK" : "");
		drawCentered(graphics, text, 0, HEIGHT - 9, WIDTH, WINNER);
	}

	private static void drawCentered(Graphics2D graphics, String text, int x, int baseline, int width, Color color)
	{
		FontMetrics metrics = graphics.getFontMetrics();
		String shown = text == null ? "" : text;
		int textX = x + (width - metrics.stringWidth(shown)) / 2;
		graphics.setColor(new Color(0, 0, 0, 190));
		graphics.drawString(shown, textX + 1, baseline + 1);
		graphics.setColor(color);
		graphics.drawString(shown, textX, baseline);
	}

	private static void drawContained(Graphics2D graphics, BufferedImage image, int x, int y, int width, int height)
	{
		double scale = Math.min((double) width / image.getWidth(), (double) height / image.getHeight());
		int drawnWidth = Math.max(1, (int) Math.round(image.getWidth() * scale));
		int drawnHeight = Math.max(1, (int) Math.round(image.getHeight() * scale));
		graphics.drawImage(image, x + (width - drawnWidth) / 2, y + (height - drawnHeight) / 2,
			drawnWidth, drawnHeight, null);
	}
}
