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

	/** Held so the offer to share earlier drops can come and go without rebuilding the screen. */
	private final JPanel earlierHolder = new JPanel();

	private Runnable onShareEarlier = () ->
	{
	};

	/** Held so the code block can be opened and shut without rebuilding the screen. */
	private final JPanel codeHolder = new JPanel();

	/**
	 * Shut to begin with, every time.
	 * <p>
	 * The code, the member count and a delete button are wanted about once — when the group is made
	 * and the code goes into Discord. After that they are a large noisy block sitting above the thing
	 * everyone actually opened this for.
	 */
	private boolean codeOpen;

	private Group group;
	private boolean creator;
	private Runnable onLeave;
	private Runnable onImport;

	/**
	 * @param medal     builds a label carrying the spoon for a place; it fills itself once the game's
	 *                  sprite has loaded
	 * @param onSearch  given what was typed; results come back through {@link #showHolders}
	 */
	public GroupView(
		Group group,
		List<Standing> leaderboard,
		String yourName,
		boolean creator,
		java.util.function.IntFunction<JLabel> medal,
		Consumer<String> onSearch,
		Runnable onBack,
		Runnable onRefresh,
		Runnable onLeave,
		int earlierDrops,
		Runnable onShareEarlier,
		Runnable onImport,
		ClaimsPanel claims,
		Runnable onClaim,
		Consumer<String> onOpenMember)
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

		this.group = group;
		this.creator = creator;
		this.onLeave = onLeave;
		this.onImport = onImport;

		codeHolder.setLayout(new BoxLayout(codeHolder, BoxLayout.Y_AXIS));
		codeHolder.setBackground(Theme.BACKGROUND);
		codeHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
		renderCode();
		body.add(codeHolder);

		this.onShareEarlier = onShareEarlier;

		earlierHolder.setLayout(new BoxLayout(earlierHolder, BoxLayout.Y_AXIS));
		earlierHolder.setBackground(Theme.BACKGROUND);
		earlierHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(earlierHolder);
		setEarlierDrops(earlierDrops);

		body.add(Cards.gap(14));
		body.add(Cards.sectionLabel("Spooniest in the group"));
		body.add(Cards.gap(4));
		body.add(leaderboardList(leaderboard, yourName, medal, onOpenMember));

		body.add(Cards.gap(16));
		body.add(claimsHeader(onClaim));
		body.add(Cards.gap(4));
		body.add(claims);

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

	/**
	 * "Up for a vote", with the way to add one beside it.
	 * <p>
	 * Anyone may claim. Nobody may carry their own, so putting the button here rather than hiding it
	 * behind the creator's block is safe and is where people will look for it.
	 */
	private JPanel claimsHeader(Runnable onClaim)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		row.add(Cards.sectionLabel("Up for a vote"), BorderLayout.WEST);

		JButton add = Cards.button("Claim a drop");
		add.setToolTipText("For something you got before this plugin was watching");
		add.addActionListener(event -> onClaim.run());
		row.add(add, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/**
	 * Changes how many earlier drops are on offer, without redrawing the screen.
	 * <p>
	 * Called the moment sharing is agreed to rather than after the send comes back, so the card goes
	 * at once. Waiting for the round trip would leave it sitting there for a few seconds still
	 * offering what has just been given away.
	 */
	public void setEarlierDrops(int count)
	{
		earlierHolder.removeAll();

		if (count > 0)
		{
			earlierHolder.add(Cards.gap(8));
			earlierHolder.add(shareEarlier(count, onShareEarlier));
		}

		earlierHolder.revalidate();
		earlierHolder.repaint();
	}

	/**
	 * The offer to hand over what was already recorded before joining.
	 * <p>
	 * Offered rather than done, because joining a group should not push a collection log built up over
	 * years at everyone without being asked. Shown only while there is something to send, so it
	 * disappears once it has been taken up.
	 */
	private JPanel shareEarlier(int count, Runnable onShare)
	{
		JPanel card = Cards.card();

		JLabel heading = new JLabel(count + (count == 1 ? " earlier drop" : " earlier drops"));
		heading.setFont(Theme.heading());
		heading.setForeground(Theme.GOLD);
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(heading);

		card.add(Cards.gap(2));
		card.add(Cards.muted("Recorded before you joined, and not shared with this group."));

		card.add(Cards.gap(6));
		JButton share = Cards.button("Share them");
		share.setAlignmentX(Component.LEFT_ALIGNMENT);
		share.addActionListener(event -> onShare.run());
		card.add(share);

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	/** Fills {@link #codeHolder}, open or shut. */
	private void renderCode()
	{
		codeHolder.removeAll();
		codeHolder.add(codeToggle());

		if (codeOpen)
		{
			codeHolder.add(Cards.gap(3));
			codeHolder.add(codeCard());
		}

		codeHolder.revalidate();
		codeHolder.repaint();
	}

	/** The one slim row that is always there, and the whole block when it is shut. */
	private JPanel codeToggle()
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Theme.CARD);
		row.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

		JLabel label = new JLabel((codeOpen ? "− " : "+ ")
			+ (creator ? "Your group code" : "Group code"));
		label.setFont(Theme.body());
		label.setForeground(Theme.TEXT_MUTED);
		row.add(label, BorderLayout.CENTER);

		JLabel members = new JLabel(group.getMembers()
			+ (group.getMembers() == 1 ? " member" : " members"));
		members.setFont(Theme.body());
		members.setForeground(Theme.TEXT_MUTED);
		row.add(members, BorderLayout.EAST);

		row.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent event)
			{
				codeOpen = !codeOpen;
				renderCode();
			}
		});

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/** The code, shown large — it is what gets pasted into Discord and read back by hand. */
	private JPanel codeCard()
	{
		JPanel card = Cards.card();

		JLabel code = new JLabel(group.getCode());
		code.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 18f));
		code.setForeground(Theme.GOLD);
		code.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(code);

		card.add(Cards.gap(2));
		card.add(Cards.muted("Share this so people can join. Made by " + group.getCreatorRsn() + "."));

		if (creator)
		{
			// In here with the code and the delete button, because it belongs to the same rare visit:
			// set the group up, share the code, bring in the history, and then never look again.
			card.add(Cards.gap(8));
			card.add(Cards.sectionLabel("Import from Discord"));
			card.add(Cards.gap(2));
			card.add(Cards.muted("If your group posts Dink messages to a channel, the drops in it can "
				+ "be brought in. You are shown what was found before anything is kept."));

			card.add(Cards.gap(4));
			JButton go = Cards.button("Look for history");
			go.setAlignmentX(Component.LEFT_ALIGNMENT);
			go.addActionListener(event -> onImport.run());
			card.add(go);
		}

		card.add(Cards.gap(8));
		JButton leave = Cards.button(creator ? "Delete group" : "Leave group");
		leave.setAlignmentX(Component.LEFT_ALIGNMENT);
		leave.addActionListener((ActionEvent event) -> onLeave.run());
		card.add(leave);

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private JPanel leaderboardList(
		List<Standing> leaderboard, String yourName, java.util.function.IntFunction<JLabel> medal,
		Consumer<String> onOpenMember)
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
			list.add(standingRow(standing, yourName, medal, onOpenMember));
			list.add(Cards.gap(3));
		}

		return list;
	}

	private JPanel standingRow(
		Standing standing, String yourName, java.util.function.IntFunction<JLabel> medal,
		Consumer<String> onOpenMember)
	{
		boolean you = standing.getRsn().equalsIgnoreCase(yourName == null ? "" : yourName);

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(Theme.CARD);
		row.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		// The top three get a spoon; everyone else gets their number. Fourth place being plainly not a
		// medal is most of the joke.
		if (standing.getPlace() <= 3)
		{
			row.add(medal.apply(standing.getPlace()), BorderLayout.WEST);
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

		// The whole row opens them, not just the name: a name is a small target in a sidebar, and
		// everything on the row is about that person anyway.
		row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		row.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent event)
			{
				onOpenMember.accept(standing.getRsn());
			}

			@Override
			public void mouseEntered(java.awt.event.MouseEvent event)
			{
				row.setBackground(Theme.CARD_HOVER);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent event)
			{
				row.setBackground(Theme.CARD);
			}
		});

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
		String itemName, List<Holder> holders, java.util.function.IntFunction<JLabel> medal)
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

	private JPanel holderRow(Holder holder, java.util.function.IntFunction<JLabel> medal)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(Theme.CARD);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Only the scored get medals here. Somebody who has the item but never had a kill count
		// recorded is not first, and is not last either — they are simply not in the running.
		if (holder.getShare() != null && holder.getPlace() <= 3)
		{
			row.add(medal.apply(holder.getPlace()), BorderLayout.WEST);
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
