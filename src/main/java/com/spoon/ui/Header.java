package com.spoon.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The title, with the spoon beside it.
 * <p>
 * One class so every screen carries the same thing. A heading that shifted by a few pixels between
 * the front page and a group would be noticed without anybody being able to say why.
 */
public final class Header
{
	private static final int LOGO = 34;

	/**
	 * What the words may take up.
	 * <p>
	 * A sidebar is a fixed 225 wide and a JLabel asks for however much room its text needs on one
	 * line, so an unwrapped strapline quietly widens the whole plugin until it shoves RuneLite's own
	 * icons off the screen. That is exactly what happened: the group screen fitted because its
	 * strapline was one short word, and the front screen did not because its was a sentence.
	 */
	/**
	 * What is actually available: the sidebar's width less the padding this sits inside.
	 *
	 * The panel pads by ten each side and the screens leave room for a scrollbar, so asking for the
	 * full 225 asks for more than there is and widens everything by the difference. That is the second
	 * time this has pushed RuneLite's icons off the edge, so the sum is written down rather than
	 * guessed at.
	 */
	private static final int AVAILABLE = net.runelite.client.ui.PluginPanel.PANEL_WIDTH - 32;

	private static final int WORDS_WIDTH = AVAILABLE - LOGO - 8;

	private Header()
	{
	}

	public static JPanel build(String strapline)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel words = new JPanel();
		words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
		words.setBackground(Theme.BACKGROUND);

		JLabel title = new JLabel("WHO SPOONED IT?");
		title.setFont(Theme.title());
		title.setForeground(Theme.GOLD);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		words.add(title);

		if (strapline != null && !strapline.isEmpty())
		{
			// Wrapped rather than trusted to be short, so no caller can widen the panel by accident.
			JLabel under = new JLabel(
				"<html><body style='width:" + WORDS_WIDTH + "px'>" + strapline + "</body></html>");
			under.setFont(Theme.body());
			under.setForeground(Theme.TEXT_MUTED);
			under.setAlignmentX(Component.LEFT_ALIGNMENT);
			words.add(under);
		}

		row.add(words, BorderLayout.CENTER);

		JLabel logo = new JLabel(new ImageIcon(SpoonIcon.logo(LOGO)));
		logo.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
		row.add(logo, BorderLayout.EAST);

		int height = row.getPreferredSize().height;
		row.setPreferredSize(new Dimension(AVAILABLE, height));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return row;
	}
}
