package com.spoon.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * A spoon, drawn.
 * <p>
 * Drawn rather than taken from the game because this is a logo rather than an item: it has to stay
 * crisp at whatever size a heading needs, and be the colour of the heading beside it. The real
 * article — the wooden spoon — is used where an item is actually meant, on the leaderboard.
 */
public final class SpoonIcon
{
	private SpoonIcon()
	{
	}

	/**
	 * @param size   the square to draw within
	 * @param body   the metal
	 * @param hollow the inside of the bowl, which is what stops it reading as a flat blob
	 */
	public static BufferedImage draw(int size, Color body, Color hollow)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = Theme.smooth(image.createGraphics());

		// Tilted, because a spoon standing to attention looks like a lollipop.
		graphics.rotate(Math.toRadians(-30), size / 2.0, size / 2.0);

		// A narrow, tall bowl. Anything close to a circle reads as a magnifying glass, which is what
		// the first attempt at this looked like.
		double bowlWidth = size * 0.34;
		double bowlHeight = size * 0.46;
		double bowlX = (size - bowlWidth) / 2.0;
		double bowlY = size * 0.05;

		// The handle is drawn first and long, so the shape is mostly stem, which is what says spoon.
		double handleWidth = Math.max(1.6, size * 0.10);
		graphics.setColor(body);
		graphics.fill(new RoundRectangle2D.Double(
			(size - handleWidth) / 2.0,
			bowlY + bowlHeight * 0.72,
			handleWidth,
			size * 0.60,
			handleWidth,
			handleWidth));

		graphics.fill(new Ellipse2D.Double(bowlX, bowlY, bowlWidth, bowlHeight));

		// The hollow is filled rather than outlined: a ring around the bowl is exactly what made the
		// first version look like a lens.
		double inset = Math.max(1.0, size * 0.06);
		graphics.setColor(hollow);
		graphics.fill(new Ellipse2D.Double(
			bowlX + inset,
			bowlY + inset,
			bowlWidth - inset * 2,
			bowlHeight - inset * 2.4));

		graphics.dispose();
		return image;
	}

	/** The logo beside the title: gold, with a warmer highlight. */
	public static BufferedImage logo(int size)
	{
		return draw(size, Theme.GOLD, new Color(255, 228, 170));
	}

	/**
	 * Turns the game's own wooden spoon into a medal.
	 * <p>
	 * Taken to grey first and then tinted, rather than shifted in hue, so a brown sprite comes out as
	 * clean gold or silver instead of a muddier brown.
	 */
	public static BufferedImage tint(BufferedImage source, Color colour)
	{
		BufferedImage out = new BufferedImage(
			source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < source.getHeight(); y++)
		{
			for (int x = 0; x < source.getWidth(); x++)
			{
				int argb = source.getRGB(x, y);
				int alpha = (argb >>> 24) & 0xFF;

				if (alpha == 0)
				{
					continue;
				}

				int red = (argb >> 16) & 0xFF;
				int green = (argb >> 8) & 0xFF;
				int blue = argb & 0xFF;

				// Perceptual weights, so the sprite's shading survives the trip to grey.
				double luma = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0;

				int r = clamp(colour.getRed() * luma);
				int g = clamp(colour.getGreen() * luma);
				int b = clamp(colour.getBlue() * luma);

				out.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
			}
		}

		return out;
	}

	private static int clamp(double value)
	{
		return Math.max(0, Math.min(255, (int) Math.round(value)));
	}
}
