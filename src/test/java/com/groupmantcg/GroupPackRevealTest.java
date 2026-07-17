package com.groupmantcg;

import com.google.gson.Gson;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GroupPackRevealTest
{
	@Test
	public void detectsOnePackIncludingDuplicatesAndFoils()
	{
		long now = 10_000L;
		LocalCollection.CardInstance old = card("old", "Cow", false, 1_000L);
		List<LocalCollection.CardInstance> current = new ArrayList<>();
		current.add(old);
		current.add(card("1", "Goblin", false, now));
		current.add(card("2", "Goblin", true, now));
		current.add(card("3", "Coins", false, now));
		current.add(card("4", "Lobster", false, now));
		current.add(card("5", "Abyssal demon", true, now));

		LocalCollection.Snapshot snapshot = new LocalCollection.Snapshot(8L, current, true);
		List<List<GroupPackRevealService.Pull>> packs = GroupPackRevealService.detectRecentPacks(
			7L, Map.of(old.id(), old), snapshot, now + 2_000L);

		assertEquals(1, packs.size());
		assertEquals(5, packs.get(0).size());
		assertTrue(packs.get(0).get(0).newForCollection());
		assertTrue(packs.get(0).get(1).newForCollection());
		assertTrue(packs.get(0).get(1).foil());
	}

	@Test
	public void ignoresChangesThatDidNotOpenAPack()
	{
		LocalCollection.CardInstance gift = card("gift", "Goblin", false, 9_000L);
		LocalCollection.Snapshot snapshot = new LocalCollection.Snapshot(4L, List.of(gift), true);
		assertTrue(GroupPackRevealService.detectRecentPacks(
			4L, Collections.emptyMap(), snapshot, 10_000L).isEmpty());
	}

	@Test
	public void loadsOfficialCardArtworkMetadata()
	{
		CardVisualCatalog catalog = new CardVisualCatalog(new Gson());
		CardVisualCatalog.CardVisual goblin = catalog.find("Goblin");
		assertEquals(6_376, catalog.size());
		assertNotNull(goblin);
		assertTrue(goblin.monster());
		assertTrue(goblin.imageUrl().contains("oldschool.runescape.wiki/images/"));
		assertFalse(goblin.examine().isEmpty());
		assertFalse(goblin.rarityLabel().isEmpty());
	}

	@Test
	public void bundlesAndRendersThePluginHubOsrsTcgCardDesign() throws Exception
	{
		try (InputStream cardBackStream = getClass().getResourceAsStream("/osrs-tcg/Cardback.png");
			 InputStream packStream = getClass().getResourceAsStream("/osrs-tcg/Pack_Standard.png");
			 InputStream noticesStream = getClass().getResourceAsStream("/THIRD_PARTY_NOTICES.md"))
		{
			assertNotNull(cardBackStream);
			assertNotNull(packStream);
			assertNotNull(noticesStream);
			BufferedImage cardBack = ImageIO.read(cardBackStream);
			BufferedImage pack = ImageIO.read(packStream);
			assertTrue(cardBack.getWidth() > 400);
			assertTrue(cardBack.getHeight() > 500);
			assertTrue(pack.getWidth() > 300);
			assertTrue(pack.getHeight() > 600);
		}

		CardVisualCatalog catalog = new CardVisualCatalog(new Gson());
		CardVisualCatalog.CardVisual goblin = catalog.find("Goblin");
		BufferedImage rendered = new BufferedImage(180, 260, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = rendered.createGraphics();
		try
		{
			OsrsTcgCardRenderer.drawCardFace(graphics, new Rectangle(0, 0, 180, 260), goblin,
				false, goblin.rarityColor(), null);
		}
		finally
		{
			graphics.dispose();
		}
		assertTrue((rendered.getRGB(90, 130) >>> 24) > 0);
	}

	@Test
	public void liveRevealsDefaultOnWhileExternalNetworkFeaturesRequireOptIn()
	{
		GroupmanTcgConfig config = new GroupmanTcgConfig() { };
		assertTrue(config.broadcastPackReveals());
		assertTrue(config.showPackReveals());
		assertFalse(config.hostedSyncEnabled());
		assertFalse(config.downloadCardArt());
		assertEquals(8, config.packRevealDuration());
	}

	private static LocalCollection.CardInstance card(String id, String name, boolean foil, long pulledAt)
	{
		return new LocalCollection.CardInstance(id, name, EntityCardCatalog.normalize(name), foil,
			"Example Player", pulledAt);
	}
}
