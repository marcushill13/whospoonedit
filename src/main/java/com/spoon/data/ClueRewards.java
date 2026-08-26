package com.spoon.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * How rare each clue reward is, by the tier of casket it comes out of.
 * <p>
 * Separate from the monster drops because a clue is not a monster. It has no kill count in the
 * ordinary sense, but the game does keep a count of caskets opened per tier, which is the same thing
 * for this purpose: how many goes it took.
 * <p>
 * Built from the wiki at build time by {@code scripts/generate-clue-rewards.mjs}, so nothing is
 * fetched while somebody is playing.
 */
@Slf4j
@Singleton
public class ClueRewards
{
	/** Tier to lower-cased item name to the "1 in N". */
	private final Map<String, Map<String, Double>> byTier;

	@Inject
	private ClueRewards(Gson gson)
	{
		this.byTier = load(gson);
		log.debug("Loaded clue rewards for {} tiers", byTier.size());
	}

	private static Map<String, Map<String, Double>> load(Gson gson)
	{
		Type type = new TypeToken<Map<String, Map<String, Double>>>()
		{
		}.getType();

		try (InputStream in = ClueRewards.class.getResourceAsStream("/com/spoon/clue-rewards.json"))
		{
			if (in == null)
			{
				log.warn("The clue reward data is missing from the plugin");
				return Collections.emptyMap();
			}

			Map<String, Map<String, Double>> loaded =
				gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);

			return loaded == null ? Collections.emptyMap() : loaded;
		}
		catch (Exception e)
		{
			// Never fatal. Without rates a clue reward is still recorded; it simply cannot be scored.
			log.warn("Could not read the clue reward data", e);
			return Collections.emptyMap();
		}
	}

	/**
	 * The tier a casket belongs to, from whatever the game called the thing that dropped it.
	 *
	 * @return "hard", "master" and so on, or null if this was not a clue at all
	 */
	public static String tierOf(String source)
	{
		if (source == null)
		{
			return null;
		}

		String text = source.toLowerCase(Locale.ROOT);

		// Loose on purpose: the same tier arrives as "Clue Scroll (Hard)", "Reward Casket (Hard)" and
		// "hard Treasure Trails" depending on which part of the game is speaking.
		for (String tier : new String[]{"beginner", "easy", "medium", "hard", "elite", "master"})
		{
			if (text.contains(tier))
			{
				return tier;
			}
		}

		return null;
	}

	/**
	 * The "1 in N" for a reward out of a given tier.
	 *
	 * @return the denominator, or -1 when this tier never gives that item
	 */
	public double denominatorFor(String tier, String itemName)
	{
		if (tier == null || itemName == null)
		{
			return -1;
		}

		Map<String, Double> rewards = byTier.get(tier.toLowerCase(Locale.ROOT));
		if (rewards == null)
		{
			return -1;
		}

		Double rate = rewards.get(itemName.toLowerCase(Locale.ROOT));
		return rate == null ? -1 : rate;
	}

	public boolean isEmpty()
	{
		return byTier.isEmpty();
	}
}
