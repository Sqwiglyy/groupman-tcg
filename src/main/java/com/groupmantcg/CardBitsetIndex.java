package com.groupmantcg;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Deterministic compact representation of the bundled card catalog. */
@Singleton
class CardBitsetIndex
{
	private final List<String> cards;
	private final Map<String, Integer> indexes;
	private final String fingerprint;

	@Inject
	CardBitsetIndex(MonsterCardCatalog monsters, ItemCardCatalog items)
	{
		TreeSet<String> sorted = new TreeSet<>();
		monsters.entries().values().forEach(sorted::addAll);
		items.entries().values().forEach(sorted::addAll);
		cards = Collections.unmodifiableList(new ArrayList<>(sorted));

		Map<String, Integer> builtIndexes = new HashMap<>();
		for (int i = 0; i < cards.size(); i++)
		{
			builtIndexes.put(cards.get(i), i);
		}
		indexes = Collections.unmodifiableMap(builtIndexes);
		fingerprint = digest(String.join("\n", cards));
	}

	String encode(Set<String> owned)
	{
		BitSet bits = new BitSet(cards.size());
		for (String card : owned)
		{
			Integer index = indexes.get(card);
			if (index != null)
			{
				bits.set(index);
			}
		}
		return Base64.getEncoder().encodeToString(bits.toByteArray());
	}

	Set<String> decode(String encoded)
	{
		if (encoded == null)
		{
			throw new IllegalArgumentException("Missing bitset");
		}
		byte[] bytes = Base64.getDecoder().decode(encoded);
		if (bytes.length > (cards.size() + 7) / 8)
		{
			throw new IllegalArgumentException("Bitset exceeds catalog size");
		}
		BitSet bits = BitSet.valueOf(bytes);
		if (bits.length() > cards.size())
		{
			throw new IllegalArgumentException("Bitset contains an unknown index");
		}
		Set<String> decoded = new HashSet<>();
		for (int bit = bits.nextSetBit(0); bit >= 0; bit = bits.nextSetBit(bit + 1))
		{
			decoded.add(cards.get(bit));
		}
		return Collections.unmodifiableSet(decoded);
	}

	String fingerprint()
	{
		return fingerprint;
	}

	int size()
	{
		return cards.size();
	}

	private static String digest(String value)
	{
		try
		{
			byte[] bytes = MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder result = new StringBuilder(bytes.length * 2);
			for (byte valueByte : bytes)
			{
				result.append(String.format("%02x", valueByte & 0xff));
			}
			return result.toString();
		}
		catch (Exception ex)
		{
			throw new IllegalStateException("Unable to fingerprint card catalog", ex);
		}
	}
}

