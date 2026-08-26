package com.spoon.ui;

import com.spoon.data.Luck;
import com.spoon.data.Spoon;
import com.spoon.track.SpoonStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar.
 * <p>
 * Shows this account's own drops before anything else, because the plugin has to be worth having on
 * its own. A group is something you join later to compare; it is not the price of admission.
 */
@Singleton
public class SpoonPanel extends PluginPanel
{
	private static final SimpleDateFormat WHEN = new SimpleDateFormat("d MMM yyyy");

	private final SpoonStore spoons;
	private final ItemManager itemManager;

	private final JPanel content = new JPanel();

	@Inject
	private SpoonPanel(SpoonStore spoons, ItemManager itemManager)
	{
		super(false);

		this.spoons = spoons;
		this.itemManager = itemManager;

		setLayout(new BorderLayout());
		setBackground(Theme.BACKGROUND);
		setOpaque(true);
		setBorder(BorderFactory.createEmptyBorder());

		content.setLayout(new BorderLayout());
		content.setBackground(Theme.BACKGROUND);
		content.setOpaque(true);
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JScrollPane scroll = new JScrollPane(
			content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setBackground(Theme.BACKGROUND);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getVerticalScrollBar().setBackground(Theme.BACKGROUND);
		scroll.getViewport().setBackground(Theme.BACKGROUND);
		add(scroll, BorderLayout.CENTER);

		rebuild();
	}

	/** Called whenever a drop lands, so the panel keeps up without anyone pressing anything. */
	public void refresh()
	{
		SwingUtilities.invokeLater(this::rebuild);
	}

	private void rebuild()
	{
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(Theme.BACKGROUND);

		JLabel heading = new JLabel("WHO SPOONED IT?");
		heading.setFont(Theme.title());
		heading.setForeground(Theme.GOLD);
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(heading);

		JLabel strapline = new JLabel("Your collection log, and how lucky you got");
		strapline.setFont(Theme.body());
		strapline.setForeground(Theme.TEXT_MUTED);
		strapline.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(strapline);

		body.add(Cards.gap(12));
		body.add(summary());

		body.add(Cards.gap(12));
		body.add(Cards.sectionLabel("Your spooniest"));

		List<Spoon> luckiest = spoons.luckiestFirst();
		if (luckiest.isEmpty())
		{
			body.add(Cards.gap(4));
			body.add(Cards.muted(spoons.count() == 0
				? "Nothing yet. Go and fill a log slot and it will appear here."
				: "Nothing scored yet — the drops so far had no kill count to judge them on."));
		}

		int shown = 0;
		for (Spoon spoon : luckiest)
		{
			body.add(Cards.gap(3));
			body.add(row(spoon, ++shown));

			if (shown == 10)
			{
				break;
			}
		}

		content.removeAll();
		content.add(body, BorderLayout.NORTH);
		content.revalidate();
		content.repaint();
	}

	/** The headline: how many log slots, and how many of them beat the drop rate. */
	private JPanel summary()
	{
		JPanel card = Cards.card();

		JLabel total = new JLabel(spoons.spoonCount() + " spooned");
		total.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 20f));
		total.setForeground(Theme.GOLD);
		total.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(total);

		card.add(Cards.gap(2));
		card.add(Cards.muted("out of " + spoons.count()
			+ (spoons.count() == 1 ? " log slot" : " log slots") + " since you installed this"));

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private JPanel row(Spoon spoon, int place)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(Theme.CARD);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		if (spoon.getItemId() > 0)
		{
			JLabel icon = new JLabel();
			itemManager.getImage(spoon.getItemId()).addTo(icon);
			row.add(icon, BorderLayout.WEST);
		}

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(row.getBackground());

		JLabel name = new JLabel(spoon.getItemName());
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(Theme.TEXT);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(name);

		String detail = spoon.getKillCount() > 0
			? "kc " + spoon.getKillCount() + " · 1 in " + Math.round(spoon.getDenominator())
			: spoon.getSource();
		text.add(Cards.mutedInRow(detail));
		text.add(Cards.mutedInRow(WHEN.format(new Date(spoon.getObtainedAt()))));

		row.add(text, BorderLayout.CENTER);

		// The luck reads as "top N%", the same wording the group already sees from Dink in Discord.
		JLabel luck = new JLabel("top " + Math.max(1, (int) Math.round(spoon.getShare() * 100)) + "%");
		luck.setFont(FontManager.getRunescapeBoldFont());
		luck.setForeground(colourFor(spoon.getShare()));
		row.add(luck, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/**
	 * Gold for the genuinely absurd, down to muted for the ones that were merely on time. Colour rather
	 * than a number alone, so the good ones are findable without reading every row.
	 */
	private static Color colourFor(double share)
	{
		if (share < 0.05)
		{
			return Theme.GOLD;
		}

		if (share < 0.25)
		{
			return new Color(200, 200, 205);
		}

		if (share < 0.5)
		{
			return new Color(176, 122, 74);
		}

		return Theme.TEXT_MUTED;
	}
}
