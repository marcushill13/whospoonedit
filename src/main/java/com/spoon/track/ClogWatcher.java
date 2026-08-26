package com.spoon.track;

import com.spoon.data.ClueRewards;
import com.spoon.data.DropRates;
import com.spoon.data.Luck;
import com.spoon.data.Spoon;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;

/**
 * Catches a collection log item the moment it drops, and works out how lucky it was.
 * <p>
 * The game announces a new log slot in the chat and says nothing about what dropped it, so the source
 * has to come from the kill that just happened. That is what {@link #lastSource} is for: loot arrives
 * first, the chat line follows, and the two are joined here.
 * <p>
 * Nothing is counted in this class. The kill count is read from what other plugins already store, so
 * the fact that RuneLite has two ways of announcing a kill — and may fire both — cannot corrupt
 * anything: setting the same name twice is setting it once.
 */
@Slf4j
@Singleton
public class ClogWatcher
{
	private static final String ADDED = "New item added to your collection log: ";

	private final ItemManager itemManager;
	private final KillCounts killCounts;
	private final DropRates dropRates;
	private final ClueRewards clueRewards;

	/** What died most recently, which is what a log slot moments later is assumed to have come from. */
	private String lastSource;

	/** Told about each captured drop. Set by the plugin so this class need not know where it goes. */
	private java.util.function.Consumer<Spoon> onCaptured = spoon ->
	{
	};

	@Inject
	private ClogWatcher(
		ItemManager itemManager,
		KillCounts killCounts,
		DropRates dropRates,
		ClueRewards clueRewards)
	{
		this.itemManager = itemManager;
		this.killCounts = killCounts;
		this.dropRates = dropRates;
		this.clueRewards = clueRewards;
	}

	public void setOnCaptured(java.util.function.Consumer<Spoon> onCaptured)
	{
		this.onCaptured = onCaptured;
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		if (event.getComposition() != null)
		{
			lastSource = event.getComposition().getName();
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		// Both of these fire for a kill depending on how the server announced it, and either may be the
		// only one. Harmless to take both: this records what died, it does not count how often.
		NPC npc = event.getNpc();
		if (npc != null && npc.getName() != null)
		{
			lastSource = npc.getName();
		}
	}

	@Subscribe
	public void onLootReceived(net.runelite.client.plugins.loottracker.LootReceived event)
	{
		// Chests and caskets, which never die at your feet and so never fire the other two. This is how
		// a clue reward gets a source at all.
		if (event.getName() != null)
		{
			lastSource = event.getName();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		String message = event.getMessage();
		if (message == null)
		{
			return;
		}

		int at = message.indexOf(ADDED);
		if (at < 0)
		{
			return;
		}

		// The message carries the item's name with the game's own formatting around it.
		String itemName = stripTags(message.substring(at + ADDED.length())).trim();
		if (itemName.isEmpty())
		{
			return;
		}

		capture(itemName);
	}

	private void capture(String itemName)
	{
		Spoon spoon = new Spoon();
		spoon.setItemName(itemName);
		spoon.setObtainedAt(System.currentTimeMillis());
		spoon.setSource(lastSource == null ? "" : lastSource);

		// A clue is not a monster: no kill count in the ordinary sense, but the game does keep how many
		// caskets of that tier have been opened, which is the same question — how many goes it took.
		String tier = ClueRewards.tierOf(lastSource);

		int kc;
		double denominator;

		if (tier != null)
		{
			kc = killCounts.forClueTier(tier);
			denominator = clueRewards.denominatorFor(tier, itemName);
			spoon.setItemId(-1);
		}
		else
		{
			// Identified against what this monster actually drops, which settles the item and its rarity
			// in one pass and works for untradeables that a name lookup cannot find.
			DropRates.Drop match = matchIn(lastSource, itemName);
			spoon.setItemId(match == null ? -1 : match.itemId);
			denominator = match == null ? -1 : match.denominator;
			kc = killCounts.forSource(lastSource);
		}

		spoon.setKillCount(kc);
		spoon.setDenominator(denominator);

		// Anything missing leaves this at -1, which means "unscored" everywhere else. A clue scroll item
		// has no monster and no kill count, and pretending otherwise would put a made-up number on a
		// leaderboard.
		spoon.setShare(Luck.shareWhoHaveItBy(denominator, kc));

		log.debug("Collection log: {} from {} at kc {} (1 in {}) -> {}",
			itemName, spoon.getSource(), kc, denominator, spoon.getShare());

		onCaptured.accept(spoon);
	}

	/**
	 * Finds the dropped item among the things this monster drops.
	 * <p>
	 * Safe here: chat events are posted on the client thread, which is the only one allowed to read an
	 * item's composition.
	 */
	private DropRates.Drop matchIn(String source, String itemName)
	{
		for (DropRates.Drop drop : dropRates.dropsFrom(source))
		{
			ItemComposition composition = itemManager.getItemComposition(drop.itemId);
			if (composition != null && itemName.equalsIgnoreCase(composition.getName()))
			{
				return drop;
			}
		}

		return null;
	}

	private static String stripTags(String text)
	{
		return text.replaceAll("<[^>]*>", "");
	}
}
