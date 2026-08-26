package com.spoon.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * How rare each thing is, by the monster that drops it.
 * <p>
 * Bundled rather than fetched, so the plugin never calls out to the wiki while someone is playing and
 * a drop can always be scored the instant it lands. The file is Dink's, used under its BSD licence and
 * credited in NOTICE.md — see there for why sharing its data matters rather than rebuilding it.
 */
@Slf4j
@Singleton
public class DropRates
{
	/** One item as the dataset stores it: {@code i} the item id, {@code d} the "1 in d". */
	private static class Entry
	{
		int i;
		double d;
	}

	private final Map<String, List<Entry>> bySource;

	@Inject
	private DropRates(Gson gson)
	{
		this.bySource = load(gson);
		log.debug("Loaded drop rates for {} sources", bySource.size());
	}

	private static Map<String, List<Entry>> load(Gson gson)
	{
		Type type = new TypeToken<Map<String, List<Entry>>>()
		{
		}.getType();

		try (InputStream in = DropRates.class.getResourceAsStream("/com/spoon/npc-drops.json"))
		{
			if (in == null)
			{
				log.warn("The drop rate data is missing from the plugin");
				return Collections.emptyMap();
			}

			Map<String, List<Entry>> loaded =
				gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);

			return loaded == null ? Collections.emptyMap() : loaded;
		}
		catch (Exception e)
		{
			// Never fatal. Without rates, drops are still recorded — they simply cannot be scored, which
			// is a worse plugin rather than a broken one.
			log.warn("Could not read the drop rate data", e);
			return Collections.emptyMap();
		}
	}

	/** One thing a monster drops: which item, and the "1 in N" of getting it. */
	public static final class Drop
	{
		public final int itemId;
		public final double denominator;

		Drop(int itemId, double denominator)
		{
			this.itemId = itemId;
			this.denominator = denominator;
		}
	}

	/**
	 * Everything a monster drops.
	 * <p>
	 * Exposed so a drop can be identified by name against this list rather than by asking RuneLite to
	 * look the name up. {@code ItemManager.search} answers from the price API, which only knows
	 * tradeable items — so it finds nothing for pets, and pets are the entire point of a plugin about
	 * being spooned. Matching against a monster's own drop list has no such gap, and it is a few dozen
	 * items rather than the whole game.
	 */
	public List<Drop> dropsFrom(String source)
	{
		if (source == null)
		{
			return Collections.emptyList();
		}

		List<Entry> entries = bySource.get(source);
		if (entries == null)
		{
			entries = findIgnoringCase(source);
		}

		if (entries == null)
		{
			return Collections.emptyList();
		}

		List<Drop> drops = new java.util.ArrayList<>(entries.size());
		for (Entry entry : entries)
		{
			drops.add(new Drop(entry.i, entry.d));
		}

		return drops;
	}

	/**
	 * The "1 in N" for an item from a given monster.
	 *
	 * @return the denominator, or -1 when this source is not in the data or never drops that item
	 */
	public double denominatorFor(String source, int itemId)
	{
		if (source == null)
		{
			return -1;
		}

		List<Entry> entries = bySource.get(source);
		if (entries == null)
		{
			// Names come from the game and the dataset was built from the wiki, so they mostly agree but
			// not always. A case-insensitive sweep costs one pass and rescues the ones that differ only
			// in capitalisation.
			entries = findIgnoringCase(source);
		}

		if (entries == null)
		{
			return -1;
		}

		for (Entry entry : entries)
		{
			if (entry.i == itemId)
			{
				return entry.d;
			}
		}

		return -1;
	}

	private List<Entry> findIgnoringCase(String source)
	{
		String wanted = source.toLowerCase(Locale.ROOT);
		for (Map.Entry<String, List<Entry>> candidate : bySource.entrySet())
		{
			if (candidate.getKey().toLowerCase(Locale.ROOT).equals(wanted))
			{
				return candidate.getValue();
			}
		}

		return null;
	}

	/** Every monster the data knows, so the whole set of dropped items can be walked once. */
	public java.util.Set<String> sources()
	{
		return bySource.keySet();
	}

	public boolean isEmpty()
	{
		return bySource.isEmpty();
	}
}
