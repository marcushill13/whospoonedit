package com.spoon.track;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.http.api.loottracker.LootRecordType;
import org.apache.commons.lang3.StringUtils;

/**
 * What kill count you were on when something dropped.
 * <p>
 * Deliberately reads counts that other plugins already keep rather than counting kills itself. That is
 * partly because they are better at it, the Chat Commands plugin stores the number the server told
 * you, which cannot drift, and partly to avoid a whole class of bug: a plugin that increments a
 * counter on a loot event double counts the moment two loot events fire for one kill, and RuneLite has
 * more than one way of announcing a kill. Reading a stored number cannot double anything.
 * <p>
 * Three sources, best first. The Chat Commands count comes from "Your Cockatrice kill count is: 65",
 * which is the server's own figure. The Loot Tracker's is inferred from kills it recorded, so it is
 * right for anything that always drops something and low for anything that does not.
 */
@Slf4j
@Singleton
public class KillCounts
{
	private final ConfigManager configManager;

	@Inject
	private KillCounts(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/**
	 * @param source the monster as the game named it
	 * @return the kill count, or -1 when nothing knows it
	 */
	public int forSource(String source)
	{
		if (source == null || source.isEmpty())
		{
			return -1;
		}

		Integer announced = configManager.getRSProfileConfiguration(
			"killcount", cleanName(source), int.class);

		if (announced != null)
		{
			return announced;
		}

		return fromLootTracker(source);
	}

	/**
	 * The Loot Tracker's count of kills it has seen.
	 * <p>
	 * A fallback, and an undercount for anything that can die without dropping anything, because the
	 * Loot Tracker only records a kill when there was loot to record. Better than nothing for the
	 * ordinary monsters that never announce a kill count.
	 */
	private int fromLootTracker(String source)
	{
		String json = configManager.getConfiguration(
			"loottracker", configManager.getRSProfileKey(), "drops_" + LootRecordType.NPC + "_" + source);

		if (json == null)
		{
			return -1;
		}

		// Read by hand rather than through the Loot Tracker's own class, which is not ours to depend on
		// and would tie this to its internals.
		int at = json.indexOf("\"kills\"");
		if (at < 0)
		{
			return -1;
		}

		String digits = json.substring(at).replaceAll("^[^0-9-]*(-?[0-9]+).*$", "$1");

		try
		{
			return Integer.parseInt(digits);
		}
		catch (NumberFormatException e)
		{
			log.debug("Could not read a kill count out of the loot tracker for {}", source);
			return -1;
		}
	}

	/**
	 * How many caskets of a tier have been opened.
	 * <p>
	 * The game keeps this, and it is the kill count of a clue: how many goes it took. Which key the
	 * Chat Commands plugin files it under has changed with the wording of the message it reads, so
	 * every plausible spelling is tried rather than one being assumed, a wrong guess here does not
	 * fail loudly, it just quietly leaves every clue reward unscored.
	 *
	 * @param tier "hard", "master" and so on
	 * @return the count, or -1 when nothing knows it
	 */
	public int forClueTier(String tier)
	{
		if (tier == null || tier.isEmpty())
		{
			return -1;
		}

		String[] candidates = {
			"clue scroll (" + tier + ")",
			tier + " treasure trails",
			"treasure trails (" + tier + ")",
			"reward casket (" + tier + ")",
			tier
		};

		for (String key : candidates)
		{
			Integer count = configManager.getRSProfileConfiguration("killcount", key, int.class);
			if (count != null && count > 0)
			{
				return count;
			}
		}

		return -1;
	}

	/**
	 * The name the Chat Commands plugin files a boss under, which is not always the name the game uses
	 * when it dies.
	 */
	static String cleanName(String source)
	{
		if ("Crystalline Hunllef".equals(source) || "The Gauntlet".equalsIgnoreCase(source))
		{
			return "gauntlet";
		}

		if ("Corrupted Hunllef".equals(source))
		{
			return "corrupted gauntlet";
		}

		if ("The Leviathan".equalsIgnoreCase(source))
		{
			return "leviathan";
		}

		if ("The Whisperer".equalsIgnoreCase(source))
		{
			return "whisperer";
		}

		if ("The Hueycoatl".equalsIgnoreCase(source))
		{
			return "hueycoatl";
		}

		if (source.startsWith("Barrows"))
		{
			return "barrows chests";
		}

		return StringUtils.remove(source.toLowerCase(java.util.Locale.ROOT), ':');
	}
}
