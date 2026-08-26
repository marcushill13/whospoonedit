package com.spoon.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Gold, silver and bronze spoons for the top three.
 * <p>
 * The game's own wooden spoon, recoloured. Using the real item rather than something drawn is the
 * whole joke, it is a spoon that exists in Old School, and everyone knows what it means.
 * <p>
 * Filled in when the sprite arrives rather than asked for outright. {@link ItemManager#getImage}
 * hands back an image that is still empty and loads itself a moment later, so reading its pixels
 * straight away gives a blank, which is what an earlier version of this did, and then cached the
 * blank for good. Hence a label to fill rather than an icon to return.
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

	/** Kept once made, since the same three spoons are drawn on every screen. */
	private final ImageIcon[] cache = new ImageIcon[3];

	@Inject
	private Medals(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/**
	 * A label carrying the spoon for a place, filled now or as soon as the sprite loads.
	 *
	 * @param place 1, 2 or 3
	 */
	public JLabel label(int place)
	{
		JLabel label = new JLabel();

		// Sized up front so the row does not jump when the sprite arrives a moment later.
		label.setPreferredSize(new Dimension(SIZE, SIZE));

		if (place < 1 || place > 3)
		{
			return label;
		}

		ImageIcon ready = cache[place - 1];
		if (ready != null)
		{
			label.setIcon(ready);
			return label;
		}

		try
		{
			AsyncBufferedImage sprite = itemManager.getImage(WOODEN_SPOON);
			if (sprite != null)
			{
				sprite.onLoaded(() -> fill(label, sprite, place));
			}
		}
		catch (RuntimeException e)
		{
			// A missing medal costs a bit of fun; it must never stop the leaderboard being drawn.
			log.debug("Could not make a medal for place {}", place, e);
		}

		return label;
	}

	private void fill(JLabel label, BufferedImage sprite, int place)
	{
		ImageIcon icon = cache[place - 1];

		if (icon == null)
		{
			try
			{
				BufferedImage tinted = SpoonIcon.tint(sprite, COLOURS[place - 1]);
				icon = new ImageIcon(tinted.getScaledInstance(SIZE, SIZE, Image.SCALE_SMOOTH));
				cache[place - 1] = icon;
			}
			catch (RuntimeException e)
			{
				log.debug("Could not tint the spoon for place {}", place, e);
				return;
			}
		}

		ImageIcon ready = icon;
		SwingUtilities.invokeLater(() ->
		{
			label.setIcon(ready);
			label.revalidate();
			label.repaint();
		});
	}
}
