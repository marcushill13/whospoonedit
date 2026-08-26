package com.spoon.ui;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;

/**
 * One group you are in, on the front screen.
 * <p>
 * Painted for the same reason as {@link ActionTile}, and given its own outline because somebody in
 * three groups needs to see three separate things rather than one list of text.
 */
public class GroupCard extends JPanel
{
	private static final int HEIGHT = 44;

	private final String name;
	private final String detail;
	private final Runnable onClick;

	private boolean hovered;

	public GroupCard(String name, String detail, Runnable onClick)
	{
		this.name = name;
		this.detail = detail;
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
		graphics.fillRoundRect(0, 0, width - 1, height - 1, 6, 6);

		graphics.setColor(hovered ? Theme.GOLD : Theme.GOLD_DIM);
		graphics.setStroke(new BasicStroke(1f));
		graphics.drawRoundRect(0, 0, width - 1, height - 1, 6, 6);

		// A gold spine down the left, so a stack of these reads as a list of things rather than a set
		// of boxes.
		graphics.fillRect(0, 1, 3, height - 2);

		graphics.setFont(Theme.heading());
		graphics.setColor(Theme.GOLD);
		FontMetrics metrics = graphics.getFontMetrics();
		graphics.drawString(name, 12, 6 + metrics.getAscent());

		graphics.setFont(Theme.body());
		graphics.setColor(Theme.TEXT_MUTED);
		graphics.drawString(detail, 12, height - 10);

		// The chevron: this opens something.
		graphics.setColor(hovered ? Theme.GOLD : Theme.GOLD_DIM);
		graphics.setStroke(new BasicStroke(2f));
		int mid = height / 2;
		graphics.drawLine(width - 16, mid - 5, width - 11, mid);
		graphics.drawLine(width - 11, mid, width - 16, mid + 5);

		graphics.dispose();
	}
}
