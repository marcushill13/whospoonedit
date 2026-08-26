package com.spoon.ui;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;

/**
 * Gold, silver and bronze spoons for the top three.
 * <p>
 * The game's own wooden spoon, recoloured. Using the real item rather than something drawn is the
 * whole joke — it is a spoon that exists in Old School, and everyone knows what it means.
 */
@Slf4j
@Singleton
public class Medals
{
	/** The wooden spoon, which is the only spoon Old School has. */
	private static final int WOODEN_SPOON = 7433;

	private static final int SIZE = 22;

	private static final Color[] COLOURS = {
		new Color(255, 196, 70),
		new Color(214, 216, 224),
		new Color(196, 132, 76)
	};

	private final ItemManager itemManager;
	private final ImageIcon[] cache = new ImageIcon[3];

	@Inject
	private Medals(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/**
	 * @param place 1, 2 or 3
	 * @return the spoon for that place, or null if the sprite could not be had
	 */
	public ImageIcon forPlace(int place)
	{
		if (place < 1 || place > 3)
		{
			return null;
		}

		if (cache[place - 1] != null)
		{
			return cache[place - 1];
		}

		try
		{
			BufferedImage sprite = itemManager.getImage(WOODEN_SPOON);
			if (sprite == null)
			{
				return null;
			}

			BufferedImage tinted = SpoonIcon.tint(sprite, COLOURS[place - 1]);
			cache[place - 1] = new ImageIcon(
				tinted.getScaledInstance(SIZE, SIZE, Image.SCALE_SMOOTH));

			return cache[place - 1];
		}
		catch (RuntimeException e)
		{
			// The item cache may not be ready yet. A missing medal costs a bit of fun; it must not stop
			// the leaderboard being drawn.
			log.debug("Could not make a medal for place {}", place, e);
			return null;
		}
	}
}
