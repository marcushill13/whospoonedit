package com.spoon.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;

/**
 * One of the two things you can do from the front screen, drawn rather than themed.
 * <p>
 * A Swing button cannot be made to look like this without fighting its look and feel the whole way,
 * and the result still changes shape on someone else's machine. Painting it means the gold outline,
 * the fill and the lift on hover are the same everywhere.
 */
public class ActionTile extends JPanel
{
	private static final int HEIGHT = 46;

	private final String label;
	private final Runnable onClick;

	private boolean hovered;

	public ActionTile(String label, Runnable onClick)
	{
		this.label = label;
		this.onClick = onClick;

		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setPreferredSize(new Dimension(10, HEIGHT));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent event)
			{
				hovered = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent event)
			{
				hovered = false;
				repaint();
			}

			@Override
			public void mouseClicked(MouseEvent event)
			{
				onClick.run();
			}
		});
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D graphics = Theme.smooth((Graphics2D) g.create());

		int width = getWidth();
		int height = getHeight();

		graphics.setColor(hovered ? Theme.CARD_HOVER : Theme.CARD);
		graphics.fillRoundRect(0, 0, width - 1, height - 1, 8, 8);

		// The outline is what makes it read as a button rather than a heading. Brighter on hover, so
		// the thing under the cursor is obvious without anything moving.
		graphics.setColor(hovered ? Theme.GOLD : Theme.GOLD_DIM);
		graphics.setStroke(new BasicStroke(hovered ? 2f : 1f));
		graphics.drawRoundRect(1, 1, width - 3, height - 3, 8, 8);

		graphics.setFont(Theme.tile());
		graphics.setColor(hovered ? Theme.GOLD : new Color(226, 190, 130));

		FontMetrics metrics = graphics.getFontMetrics();
		int x = (width - metrics.stringWidth(label)) / 2;
		int y = (height - metrics.getHeight()) / 2 + metrics.getAscent();
		graphics.drawString(label, x, y);

		graphics.dispose();
	}
}
