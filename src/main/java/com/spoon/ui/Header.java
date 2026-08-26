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
			JLabel under = new JLabel(strapline);
			under.setFont(Theme.body());
			under.setForeground(Theme.TEXT_MUTED);
			under.setAlignmentX(Component.LEFT_ALIGNMENT);
			words.add(under);
		}

		row.add(words, BorderLayout.CENTER);

		JLabel logo = new JLabel(new ImageIcon(SpoonIcon.logo(LOGO)));
		logo.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
		row.add(logo, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}
}
