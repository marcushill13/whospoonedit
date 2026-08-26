package com.spoon.track;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.spoon.data.Spoon;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Every collection log drop this account has been seen getting.
 * <p>
 * Kept on the player's own machine and useful on its own: the plugin works from the moment it is
 * installed, with no group, no code and nothing sent anywhere. Joining a group later adds the
 * comparison, it is not what makes the thing work.
 * <p>
 * Stored against the logged-in account rather than the installation, because two characters on one
 * computer have separate collection logs and merging them would be nonsense.
 */
@Slf4j
@Singleton
public class SpoonStore
{
	private static final String CONFIG_GROUP = "whospoonedit";
	private static final String KEY = "spoons";

	private final ConfigManager configManager;
	private final Gson gson;

	private final List<Spoon> spoons = new ArrayList<>();

	@Inject
	private SpoonStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public void load()
	{
		spoons.clear();

		if (configManager.getRSProfileKey() == null)
		{
			// No account yet, so nothing to read. Called again on login.
			return;
		}

		String json = configManager.getRSProfileConfiguration(CONFIG_GROUP, KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}

		Type type = new TypeToken<List<Spoon>>()
		{
		}.getType();

		try
		{
			List<Spoon> saved = gson.fromJson(json, type);
			if (saved != null)
			{
				spoons.addAll(saved);
			}
		}
		catch (JsonSyntaxException e)
		{
			// Never throw the lot away because one entry is unreadable. Someone's drop history is not
			// recoverable if this class deletes it.
			log.warn("Could not read saved drops", e);
		}

		log.debug("Loaded {} drops", spoons.size());
	}

	private void save()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		configManager.setRSProfileConfiguration(CONFIG_GROUP, KEY, gson.toJson(spoons));
	}

	/**
	 * Records a drop, unless this item is already here.
	 * <p>
	 * The collection log announces a slot the first time it is filled, so a repeat means something has
	 * gone wrong rather than that the player got it twice — a replayed chat line, or a re-import. The
	 * first record is the one that matters, because it is the one with the kill count that earns the
	 * spoon.
	 *
	 * @return whether it was new
	 */
	public boolean add(Spoon spoon)
	{
		if (spoon == null || spoon.getItemName().isEmpty() || has(spoon.getItemName()))
		{
			return false;
		}

		spoons.add(spoon);
		save();
		return true;
	}

	public boolean has(String itemName)
	{
		for (Spoon spoon : spoons)
		{
			if (spoon.getItemName().equalsIgnoreCase(itemName))
			{
				return true;
			}
		}

		return false;
	}

	/** Newest first, which is the order anyone wants to read them in. */
	public List<Spoon> all()
	{
		List<Spoon> copy = new ArrayList<>(spoons);
		copy.sort((a, b) -> Long.compare(b.getObtainedAt(), a.getObtainedAt()));
		return Collections.unmodifiableList(copy);
	}

	/** The scored ones, luckiest first. Unscored drops are left out rather than sorted as unlucky. */
	public List<Spoon> luckiestFirst()
	{
		List<Spoon> scored = new ArrayList<>();
		for (Spoon spoon : spoons)
		{
			if (spoon.isScored())
			{
				scored.add(spoon);
			}
		}

		scored.sort((a, b) -> Double.compare(a.getShare(), b.getShare()));
		return scored;
	}

	public int count()
	{
		return spoons.size();
	}

	/** How many came in ahead of the drop rate — the ones worth arguing about. */
	public int spoonCount()
	{
		int spooned = 0;
		for (Spoon spoon : spoons)
		{
			if (com.spoon.data.Luck.isSpooned(spoon.getShare()))
			{
				spooned++;
			}
		}

		return spooned;
	}

	public void remove(String itemName)
	{
		spoons.removeIf(spoon -> spoon.getItemName().equalsIgnoreCase(itemName));
		save();
	}
}
