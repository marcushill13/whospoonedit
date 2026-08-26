package com.spoon.ui;

import com.spoon.data.DropRates;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;

/**
 * The picture of an item, found from its name.
 * <p>
 * Needed because a drop brought in from Discord has only a name — Dink writes what dropped, not which
 * item id it was — so without this every imported drop is a row with a blank space where everyone
 * else has a picture.
 * <p>
 * The obvious way round it, {@code ItemManager.search}, answers from the price API and so knows only
 * tradeable items. It finds nothing for a pet, which is the row people most want to look at. So the
 * index is built the other way about: every item any monster drops, asked for its real name once.
 */
@Slf4j
@Singleton
public class ItemIcons
{
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final DropRates dropRates;

	/** Lower-cased name to item id. Empty until the first time an icon is wanted. */
	private final Map<String, Integer> byName = new HashMap<>();

	private volatile boolean built;
	private volatile boolean building;

	@Inject
	private ItemIcons(ItemManager itemManager, ClientThread clientThread, DropRates dropRates)
	{
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.dropRates = dropRates;
	}

	/**
	 * Puts an item's picture on a label, now or once the index has been built.
	 *
	 * @param itemId the id if it is known, or anything below 1 to go looking by name
	 */
	public void applyTo(JLabel label, int itemId, String itemName)
	{
		if (itemId > 0)
		{
			itemManager.getImage(itemId).addTo(label);
			return;
		}

		if (itemName == null || itemName.isEmpty())
		{
			return;
		}

		Integer known = byName.get(itemName.toLowerCase(Locale.ROOT));
		if (known != null)
		{
			itemManager.getImage(known).addTo(label);
			return;
		}

		if (built)
		{
			// Looked for and genuinely not there: a clue reward, or something no monster drops. The row
			// simply goes without a picture rather than showing a wrong one.
			return;
		}

		build(() -> SwingUtilities.invokeLater(() ->
		{
			Integer found = byName.get(itemName.toLowerCase(Locale.ROOT));
			if (found != null)
			{
				itemManager.getImage(found).addTo(label);
				label.revalidate();
				label.repaint();
			}
		}));
	}

	/**
	 * Builds the index once, on the client thread, which is the only one allowed to read an item's
	 * composition.
	 * <p>
	 * Every id in the drop data, which is a few thousand once the duplicates across monsters are
	 * dropped — not the thirty-odd thousand items in the game. Done in one pass because a pass costs
	 * about as much as deciding whether to do a pass.
	 */
	private synchronized void build(Runnable then)
	{
		if (built)
		{
			then.run();
			return;
		}

		if (building)
		{
			// Somebody else is already doing it; their pass fills the same map this label will read.
			return;
		}

		building = true;

		clientThread.invokeLater(() ->
		{
			try
			{
				Set<Integer> ids = new HashSet<>();
				for (String source : dropRates.sources())
				{
					for (DropRates.Drop drop : dropRates.dropsFrom(source))
					{
						ids.add(drop.itemId);
					}
				}

				for (int id : ids)
				{
					ItemComposition composition = itemManager.getItemComposition(id);
					if (composition == null || composition.getName() == null)
					{
						continue;
					}

					// The first id for a name wins. Several ids share one name — noted, charged,
					// placeholder versions — and any of them draws the same picture.
					byName.putIfAbsent(composition.getName().toLowerCase(Locale.ROOT), id);
				}

				log.debug("Indexed {} item names", byName.size());
			}
			catch (RuntimeException e)
			{
				// A missing picture is a cosmetic loss. It must never stop a leaderboard being drawn.
				log.debug("Could not index item names", e);
			}
			finally
			{
				built = true;
				building = false;
				then.run();
			}
		});
	}

	/** A label that fills itself with the item's picture. */
	public JLabel label(int itemId, String itemName)
	{
		JLabel label = new JLabel();

		// Sized up front so a row does not jump when the picture arrives.
		label.setPreferredSize(new java.awt.Dimension(Cards.ICON, 32));
		applyTo(label, itemId, itemName);

		return label;
	}
}
