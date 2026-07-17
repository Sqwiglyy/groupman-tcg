package com.groupmantcg;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Non-blocking memory/disk cache for the OSRS Wiki artwork referenced by the card catalog. */
@Slf4j
@Singleton
class CardArtService
{
	private static final int MEMORY_ENTRIES = 64;
	private static final String USER_AGENT = "Groupman-TCG RuneLite plugin (maintainer: Sqwiglyy)";

	private final CardVisualCatalog catalog;
	private final GroupmanTcgConfig config;
	private final OkHttpClient http;
	private final Map<String, BufferedImage> memory = Collections.synchronizedMap(
		new LinkedHashMap<String, BufferedImage>(MEMORY_ENTRIES + 1, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest)
			{
				return size() > MEMORY_ENTRIES;
			}
		});
	private final Map<String, CompletableFuture<BufferedImage>> loading = new ConcurrentHashMap<>();
	private final Set<String> failed = ConcurrentHashMap.newKeySet();

	@Inject
	CardArtService(CardVisualCatalog catalog, GroupmanTcgConfig config, OkHttpClient http)
	{
		this.catalog = catalog;
		this.config = config;
		this.http = http;
	}

	BufferedImage getCached(String cardName)
	{
		CardVisualCatalog.CardVisual card = catalog.find(cardName);
		if (card == null || card.imageUrl().isEmpty())
		{
			return null;
		}
		String url = card.imageUrl();
		BufferedImage cached = memory.get(url);
		if (cached == null && config.downloadCardArt() && !failed.contains(url))
		{
			ensureLoad(url);
		}
		return cached;
	}

	void preload(Collection<String> cardNames)
	{
		if (cardNames != null)
		{
			for (String cardName : cardNames)
			{
				getCached(cardName);
			}
		}
	}

	private void ensureLoad(String url)
	{
		loading.computeIfAbsent(url, key -> CompletableFuture
			.supplyAsync(() -> load(key))
			.whenComplete((image, error) ->
			{
				if (image != null)
				{
					memory.put(key, image);
				}
				else
				{
					failed.add(key);
				}
				loading.remove(key);
			}));
	}

	private BufferedImage load(String url)
	{
		BufferedImage disk = readDisk(url);
		if (disk != null)
		{
			return disk;
		}
		if (!url.startsWith("https://oldschool.runescape.wiki/images/"))
		{
			return null;
		}
		try
		{
			Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();
			try (Response response = http.newCall(request).execute())
			{
				if (!response.isSuccessful() || response.body() == null)
				{
					return null;
				}
				try (InputStream input = response.body().byteStream())
				{
					BufferedImage image = ImageIO.read(input);
					if (image != null)
					{
						writeDisk(url, image);
					}
					return image;
				}
			}
		}
		catch (Exception ex)
		{
			log.debug("Unable to load optional card art", ex);
			return null;
		}
	}

	private BufferedImage readDisk(String url)
	{
		Path file = cacheFile(url);
		if (!Files.isRegularFile(file))
		{
			return null;
		}
		try (InputStream input = Files.newInputStream(file))
		{
			return ImageIO.read(input);
		}
		catch (Exception ex)
		{
			log.debug("Unable to read cached card art", ex);
			return null;
		}
	}

	private void writeDisk(String url, BufferedImage image)
	{
		Path target = cacheFile(url);
		Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
		try
		{
			Files.createDirectories(target.getParent());
			try (OutputStream output = Files.newOutputStream(temporary))
			{
				ImageIO.write(image, "png", output);
			}
			try
			{
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException ex)
			{
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (Exception ex)
		{
			log.debug("Unable to cache card art", ex);
		}
	}

	private static Path cacheFile(String url)
	{
		return Path.of(RuneLite.RUNELITE_DIR.getAbsolutePath(), "Groupman-TCG", "card-art-v1", sha256(url) + ".png");
	}

	private static String sha256(String value)
	{
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder output = new StringBuilder(digest.length * 2);
			for (byte part : digest)
			{
				output.append(String.format("%02x", part & 0xff));
			}
			return output.toString();
		}
		catch (Exception ex)
		{
			throw new IllegalStateException("Unable to hash card-art URL", ex);
		}
	}
}
