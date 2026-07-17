package com.groupmantcg;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

@Singleton
class TopTrumpsOverlay extends Overlay
{
	private static final int WIDTH = 400;
	private static final int HEIGHT = 304;
	private static final int CARD_WIDTH = 150;
	private static final int CARD_HEIGHT = 217;
	private static final int CARD_Y = 50;
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
			drawCard(copy, 20, CARD_Y, result.challengerName(), result.challengerCard(), result.winner() == 0);
			drawCard(copy, WIDTH - 20 - CARD_WIDTH, CARD_Y, result.challengedName(),
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
		String title = "OSRS TCG  •  TOP TRUMPS";
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
		graphics.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
		drawCentered(graphics, player, x, y - 7, CARD_WIDTH, winner ? WINNER : Color.LIGHT_GRAY);
		OsrsTcgCardRenderer.drawCardFace(graphics, new Rectangle(x, y, CARD_WIDTH, CARD_HEIGHT), card,
			false, card.rarityColor(), art.getCached(card.displayName()));
		graphics.setColor(winner ? WINNER : LOSER);
		graphics.setStroke(new BasicStroke(winner ? 2.4f : 1.2f));
		graphics.drawRoundRect(x - 3, y - 3, CARD_WIDTH + 5, CARD_HEIGHT + 5, 11, 11);
	}

	private static void drawVersus(Graphics2D graphics)
	{
		graphics.setFont(FontManager.getRunescapeBoldFont().deriveFont(17f));
		drawCentered(graphics, "VS", 0, 160, WIDTH, BRONZE);
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

}
