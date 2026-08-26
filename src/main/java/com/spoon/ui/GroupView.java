package com.spoon.ui;

import com.spoon.data.Group;
import com.spoon.data.Holder;
import com.spoon.data.Standing;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.runelite.client.ui.FontManager;

/**
 * One group, open: who has been spooned the most, and who spooned any given item.
 */
public class GroupView extends JPanel
{
	/** Gold, silver and bronze. Everyone below fourth gets a number, which is its own message. */
	private static final Color[] MEDALS = {
		new Color(240, 176, 62),
		new Color(198, 200, 208),
		new Color(176, 122, 74)
	};

	private final JPanel searchResults = new JPanel();

	/**
	 * @param medal     the game's own wooden spoon, already tinted, or null if it could not be loaded
	 * @param onSearch  given what was typed; results come back through {@link #showHolders}
	 */
	public GroupView(
		Group group,
		List<Standing> leaderboard,
		String yourName,
		boolean creator,
		java.util.function.IntFunction<ImageIcon> medal,
		Consumer<String> onSearch,
		Runnable onBack,
		Runnable onRefresh,
		Runnable onLeave)
	{
		setLayout(new BorderLayout());
		setBackground(Theme.BACKGROUND);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(Theme.BACKGROUND);
		body.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, Cards.SCROLLBAR_ALLOWANCE));

		body.add(backRow(onBack, onRefresh));
		body.add(Cards.gap(6));

		body.add(Header.build(group.getName()));
		body.add(Cards.gap(10));

		body.add(codeCard(group, creator, onLeave));

		body.add(Cards.gap(14));
		body.add(Cards.sectionLabel("Spooniest in the group"));
		body.add(Cards.gap(4));
		body.add(leaderboardList(leaderboard, yourName, medal));

		body.add(Cards.gap(16));
		body.add(Cards.sectionLabel("Who spooned it?"));
		body.add(Cards.gap(4));
		body.add(searchRow(onSearch));

		searchResults.setLayout(new BoxLayout(searchResults, BoxLayout.Y_AXIS));
		searchResults.setBackground(Theme.BACKGROUND);
		searchResults.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(Cards.gap(4));
		body.add(searchResults);

		add(body, BorderLayout.NORTH);
	}

	private JPanel backRow(Runnable onBack, Runnable onRefresh)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

		JButton back = Cards.button("← Groups");
		back.addActionListener(event -> onBack.run());
		row.add(back);

		row.add(javax.swing.Box.createHorizontalStrut(4));

		JButton refresh = Cards.button("Refresh");
		refresh.addActionListener(event -> onRefresh.run());
		row.add(refresh);

		return row;
	}

	/** The code, shown large — it is what gets pasted into Discord and read back by hand. */
	private JPanel codeCard(Group group, boolean creator, Runnable onLeave)
	{
		JPanel card = Cards.card();

		card.add(Cards.sectionLabel(creator ? "Your group code" : "Group code"));

		JLabel code = new JLabel(group.getCode());
		code.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 18f));
		code.setForeground(Theme.GOLD);
		code.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(code);

		card.add(Cards.gap(2));
		card.add(Cards.muted(group.getMembers()
			+ (group.getMembers() == 1 ? " member" : " members")
			+ " · made by " + group.getCreatorRsn()));

		card.add(Cards.gap(6));
		JButton leave = Cards.button(creator ? "Delete group" : "Leave group");
		leave.setAlignmentX(Component.LEFT_ALIGNMENT);
		leave.addActionListener((ActionEvent event) -> onLeave.run());
		card.add(leave);

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private JPanel leaderboardList(
		List<Standing> leaderboard, String yourName, java.util.function.IntFunction<ImageIcon> medal)
	{
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(Theme.BACKGROUND);
		list.setAlignmentX(Component.LEFT_ALIGNMENT);

		if (leaderboard.isEmpty())
		{
			list.add(Cards.muted("Nobody has scored a drop yet."));
			return list;
		}

		for (Standing standing : leaderboard)
		{
			list.add(standingRow(standing, yourName, medal));
			list.add(Cards.gap(3));
		}

		return list;
	}

	private JPanel standingRow(
		Standing standing, String yourName, java.util.function.IntFunction<ImageIcon> medal)
	{
		boolean you = standing.getRsn().equalsIgnoreCase(yourName == null ? "" : yourName);

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(Theme.CARD);
		row.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		// The top three get a spoon; everyone else gets their number. Fourth place being plainly not a
		// medal is most of the joke.
		ImageIcon icon = standing.getPlace() <= 3 ? medal.apply(standing.getPlace()) : null;
		if (icon != null)
		{
			row.add(new JLabel(icon), BorderLayout.WEST);
		}
		else
		{
			JLabel place = new JLabel(standing.getPlace() + ordinal(standing.getPlace()));
			place.setFont(FontManager.getRunescapeSmallFont());
			place.setForeground(Theme.TEXT_MUTED);
			place.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 6));
			row.add(place, BorderLayout.WEST);
		}

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(row.getBackground());

		JLabel name = new JLabel(standing.getRsn());
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(you ? Theme.GOLD : Theme.TEXT);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(name);

		text.add(Cards.mutedInRow("account luck " + percent(standing.getAvgShare())
			+ " · " + standing.getScored() + " scored"));
		row.add(text, BorderLayout.CENTER);

		JLabel spoons = new JLabel(String.valueOf(standing.getSpoons()));
		spoons.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 15f));
		spoons.setForeground(standing.getPlace() <= 3 ? MEDALS[standing.getPlace() - 1] : Theme.TEXT);
		row.add(spoons, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private JPanel searchRow(Consumer<String> onSearch)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JTextField query = Theme.textField(new JTextField());
		query.setFont(Theme.body());
		row.add(query, BorderLayout.CENTER);

		JButton go = Cards.button("Search");
		go.addActionListener(event -> onSearch.accept(query.getText().trim()));
		row.add(go, BorderLayout.EAST);

		// Enter searches, because nobody reaches for a button after typing a name.
		query.addActionListener(event -> onSearch.accept(query.getText().trim()));

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		return row;
	}

	/**
	 * Fills in the results of a search: everyone who has that item, luckiest first.
	 */
	public void showHolders(
		String itemName, List<Holder> holders, java.util.function.IntFunction<ImageIcon> medal)
	{
		searchResults.removeAll();

		if (holders.isEmpty())
		{
			searchResults.add(Cards.muted("Nobody in this group has " + itemName + "."));
			searchResults.revalidate();
			searchResults.repaint();
			return;
		}

		searchResults.add(Cards.sectionLabel(itemName));
		searchResults.add(Cards.gap(4));

		for (Holder holder : holders)
		{
			searchResults.add(holderRow(holder, medal));
			searchResults.add(Cards.gap(3));
		}

		searchResults.revalidate();
		searchResults.repaint();
	}

	public void showSearchMessage(String message)
	{
		searchResults.removeAll();
		searchResults.add(Cards.muted(message));
		searchResults.revalidate();
		searchResults.repaint();
	}

	private JPanel holderRow(Holder holder, java.util.function.IntFunction<ImageIcon> medal)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(Theme.CARD);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Only the scored get medals here. Somebody who has the item but never had a kill count
		// recorded is not first, and is not last either — they are simply not in the running.
		ImageIcon icon = holder.getShare() != null && holder.getPlace() <= 3
			? medal.apply(holder.getPlace())
			: null;

		if (icon != null)
		{
			row.add(new JLabel(icon), BorderLayout.WEST);
		}

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(row.getBackground());

		JLabel name = new JLabel(holder.getRsn());
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(Theme.TEXT);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		text.add(name);

		String detail = holder.getKillCount() != null
			? "kc " + holder.getKillCount()
			: "kill count unknown";

		if (holder.getDenominator() != null)
		{
			detail += " · 1 in " + Math.round(holder.getDenominator());
		}

		if (holder.isClaimed())
		{
			// Typed in rather than seen. Said on the row, so a claimed drop is never mistaken for one
			// the plugin watched happen.
			detail += " · claimed";
		}

		text.add(Cards.mutedInRow(detail));
		row.add(text, BorderLayout.CENTER);

		JLabel luck = new JLabel(holder.getShare() == null ? "—" : percent(holder.getShare()));
		luck.setFont(FontManager.getRunescapeBoldFont());
		luck.setForeground(holder.getShare() == null ? Theme.TEXT_MUTED : luckColour(holder.getShare()));
		row.add(luck, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/**
	 * "top 12%", or "top &lt;1%" for the absurd ones.
	 * <p>
	 * Rounding a one-in-five-hundred stroke of luck down to "top 0%" would read as a bug rather than as
	 * the best number on the screen.
	 */
	static String percent(double share)
	{
		double asPercent = share * 100;
		if (asPercent < 1)
		{
			return "top <1%";
		}

		return "top " + Math.round(asPercent) + "%";
	}

	private static Color luckColour(double share)
	{
		if (share < 0.05)
		{
			return MEDALS[0];
		}

		if (share < 0.25)
		{
			return MEDALS[1];
		}

		if (share < 0.5)
		{
			return MEDALS[2];
		}

		return Theme.TEXT_MUTED;
	}

	private static String ordinal(int place)
	{
		if (place % 100 >= 11 && place % 100 <= 13)
		{
			return "th";
		}

		switch (place % 10)
		{
			case 1:
				return "st";
			case 2:
				return "nd";
			case 3:
				return "rd";
			default:
				return "th";
		}
	}
}
