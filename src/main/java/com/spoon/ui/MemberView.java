package com.spoon.ui;

import com.spoon.data.Holder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;

/**
 * One person's drops.
 * <p>
 * Newest first, because the commonest question about somebody is what they have just had. Sorted by
 * luck when asked, which is the other question people mean: what is the best thing they have ever
 * done.
 */
public class MemberView extends JPanel
{
	private static final SimpleDateFormat WHEN = new SimpleDateFormat("d MMM yyyy");

	private final JPanel list = new JPanel();
	private final ItemManager itemManager;

	private final String rsn;
	private final Consumer<String> onSort;

	private String sort;

	public MemberView(
		String rsn,
		int spoons,
		int scored,
		double avgShare,
		String sort,
		ItemManager itemManager,
		Consumer<String> onSort,
		Runnable onBack)
	{
		this.rsn = rsn;
		this.sort = sort;
		this.itemManager = itemManager;
		this.onSort = onSort;

		setLayout(new BorderLayout());
		setBackground(Theme.BACKGROUND);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(Theme.BACKGROUND);
		body.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, Cards.SCROLLBAR_ALLOWANCE));

		JButton back = Cards.button("← Leaderboard");
		back.setAlignmentX(Component.LEFT_ALIGNMENT);
		back.addActionListener(event -> onBack.run());
		body.add(back);

		body.add(Cards.gap(6));
		body.add(Header.build(rsn));

		body.add(Cards.gap(10));
		body.add(summary(spoons, scored, avgShare));

		body.add(Cards.gap(12));
		body.add(sortRow());

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(Theme.BACKGROUND);
		list.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(Cards.gap(4));
		body.add(list);

		add(body, BorderLayout.NORTH);
	}

	private JPanel summary(int spoons, int scored, double avgShare)
	{
		JPanel card = Cards.card();

		JLabel total = new JLabel(spoons + (spoons == 1 ? " spoon" : " spoons"));
		total.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 18f));
		total.setForeground(Theme.GOLD);
		total.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(total);

		card.add(Cards.gap(2));
		card.add(Cards.muted("out of " + scored + " scored drops · account luck "
			+ GroupView.percent(avgShare)));

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	/** Newest or luckiest. Two, because there is no third question anybody asks. */
	private JPanel sortRow()
	{
		JPanel row = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

		row.add(sortButton("Newest", "recent"));
		row.add(sortButton("Biggest spoon", "luck"));

		return row;
	}

	private JButton sortButton(String label, String value)
	{
		boolean chosen = value.equals(sort);

		JButton button = Cards.button(chosen ? label + " ✓" : label);
		button.setForeground(chosen ? Theme.GOLD : Theme.TEXT_MUTED);
		button.addActionListener(event ->
		{
			if (!value.equals(sort))
			{
				onSort.accept(value);
			}
		});

		return button;
	}

	public void show(List<Holder> drops)
	{
		list.removeAll();

		if (drops.isEmpty())
		{
			list.add(Cards.muted(rsn + " has nothing recorded in this group yet."));
		}

		for (Holder drop : drops)
		{
			list.add(row(drop));
			list.add(Cards.gap(3));
		}

		list.revalidate();
		list.repaint();
	}

	public void showMessage(String message)
	{
		list.removeAll();
		list.add(Cards.muted(message));
		list.revalidate();
		list.repaint();
	}

	private JPanel row(Holder drop)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(Theme.CARD);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		if (drop.getItemId() > 0)
		{
			JLabel icon = new JLabel();
			itemManager.getImage(drop.getItemId()).addTo(icon);
			row.add(icon, BorderLayout.WEST);
		}

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(row.getBackground());

		JLabel name = new JLabel(drop.getItemName());
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(Theme.TEXT);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(name);

		StringBuilder detail = new StringBuilder();
		if (drop.getKillCount() != null)
		{
			detail.append("kc ").append(drop.getKillCount());
		}
		else if (drop.getSource() != null && !drop.getSource().isEmpty())
		{
			detail.append(drop.getSource());
		}

		if (drop.getDenominator() != null)
		{
			detail.append(detail.length() > 0 ? " · " : "")
				.append("1 in ").append(Math.round(drop.getDenominator()));
		}

		if (drop.isClaimed())
		{
			// Voted in rather than watched happening, and said so wherever it appears.
			detail.append(detail.length() > 0 ? " · " : "").append("claimed");
		}

		text.add(Cards.mutedInRow(detail.length() == 0 ? "no details" : detail.toString()));
		text.add(Cards.mutedInRow(WHEN.format(new Date(drop.getObtainedAt()))));

		row.add(text, BorderLayout.CENTER);

		JLabel luck = new JLabel(drop.getShare() == null ? "—" : GroupView.percent(drop.getShare()));
		luck.setFont(FontManager.getRunescapeBoldFont());
		luck.setForeground(drop.getShare() == null ? Theme.TEXT_MUTED : colourFor(drop.getShare()));
		row.add(luck, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private static Color colourFor(double share)
	{
		if (share < 0.05)
		{
			return new Color(240, 176, 62);
		}

		if (share < 0.25)
		{
			return new Color(198, 200, 208);
		}

		if (share < 0.5)
		{
			return new Color(176, 122, 74);
		}

		return Theme.TEXT_MUTED;
	}
}
