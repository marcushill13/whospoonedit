package com.spoon.ui;

import com.spoon.data.Luck;
import com.spoon.data.Spoon;
import com.spoon.track.GroupStore;
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
	private final GroupStore groupStore;
	private final ItemIcons icons;

	/** Set by the plugin, so this class need not know how a group is made or opened. */
	private Runnable onCreate = () ->
	{
	};

	private Runnable onJoin = () ->
	{
	};

	private java.util.function.Consumer<String> onOpenGroup = code ->
	{
	};

	private final JPanel content = new JPanel();

	/** Whether the list of your own spoons is open. Shut each time the plugin starts. */
	private boolean spooniestOpen;

	@Inject
	private SpoonPanel(SpoonStore spoons, GroupStore groupStore, ItemIcons icons)
	{
		super(false);

		this.spoons = spoons;
		this.groupStore = groupStore;
		this.icons = icons;

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

	public void setActions(Runnable onCreate, Runnable onJoin,
		java.util.function.Consumer<String> onOpenGroup)
	{
		this.onCreate = onCreate;
		this.onJoin = onJoin;
		this.onOpenGroup = onOpenGroup;
	}

	/** Replaces what is on screen. One holder, so only one screen can ever be up. */
	public void show(JPanel screen)
	{
		content.removeAll();
		content.add(screen, BorderLayout.NORTH);
		content.revalidate();
		content.repaint();
	}

	/** Whether the front screen is the one showing, so a refresh knows not to trample a form. */
	public boolean isShowingList()
	{
		return content.getComponentCount() > 0 && content.getComponent(0) instanceof ListView;
	}

	/** Called whenever a drop lands, so the panel keeps up without anyone pressing anything. */
	public void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			// Only when the front screen is up. Rebuilding a half-filled form under someone's hands
			// would throw away what they had typed.
			if (isShowingList())
			{
				rebuild();
			}
		});
	}

	/** Back to the front screen, whatever was showing. */
	public void showList()
	{
		SwingUtilities.invokeLater(this::rebuild);
	}

	/** Marker so a refresh can tell which screen is up without tracking state separately. */
	private static class ListView extends JPanel
	{
	}

	private void rebuild()
	{
		ListView body = new ListView();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(Theme.BACKGROUND);

		body.add(Header.build("Your collection log, and how lucky you got"));

		body.add(Cards.gap(12));

		// The two things you can do, before anything you have. Someone opening this for the first time
		// should see the way in, not an empty list of their own drops.
		body.add(actions());

		body.add(Cards.gap(14));
		body.add(Cards.sectionLabel("Your groups"));
		body.add(Cards.gap(4));
		body.add(groups());

		body.add(Cards.gap(16));
		body.add(Cards.sectionLabel("Personal spoonage"));
		body.add(Cards.gap(4));
		body.add(summary());

		body.add(Cards.gap(12));
		body.add(spooniestToggle());

		if (spooniestOpen)
		{
			List<Spoon> luckiest = spoons.luckiestFirst();
			if (luckiest.isEmpty())
			{
				body.add(Cards.gap(4));
				body.add(Cards.muted(spoons.count() == 0
					? "Nothing yet. Go and fill a log slot and it will appear here."
					: "Nothing scored yet — the drops so far had no kill count to judge them on."));
			}

			for (Spoon spoon : luckiest)
			{
				body.add(Cards.gap(3));
				body.add(row(spoon));
			}
		}

		show(body);
	}

	/** Create and Join, side by side, because neither is the lesser of the two. */
	private JPanel actions()
	{
		JPanel row = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
		row.setBackground(Theme.BACKGROUND);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

		// Called through rather than handed over. This panel is built by injection before the plugin
		// has said what the buttons do, so passing onCreate directly would hand each tile the empty
		// default and keep it for good — which is exactly what happened: two buttons that did nothing.
		row.add(new ActionTile("Create", () -> onCreate.run()));
		row.add(new ActionTile("Join", () -> onJoin.run()));

		return row;
	}

	/**
	 * The groups this account is in. More than one is normal — a clan and a group of mates are
	 * different competitions over the same collection log.
	 */
	private JPanel groups()
	{
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(Theme.BACKGROUND);
		list.setAlignmentX(Component.LEFT_ALIGNMENT);

		List<GroupStore.Membership> mine = groupStore.all();
		if (mine.isEmpty())
		{
			list.add(Cards.muted("None yet. Make one, or join with a code from a mate."));
			return list;
		}

		for (GroupStore.Membership membership : mine)
		{
			int members = membership.group.getMembers();
			list.add(new GroupCard(
				membership.group.getName(),
				members + (members == 1 ? " member" : " members"),
				() -> onOpenGroup.accept(membership.group.getCode())));
			list.add(Cards.gap(5));
		}

		return list;
	}

	/**
	 * "Your spooniest", which opens to show them.
	 * <p>
	 * Shut to begin with, because the front screen is a way in to two things and a list of forty
	 * drops between them buries both.
	 */
	private JPanel spooniestToggle()
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Theme.CARD);
		row.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

		JLabel label = new JLabel((spooniestOpen ? "− " : "+ ") + "Your spooniest");
		label.setFont(Theme.body());
		label.setForeground(Theme.TEXT_MUTED);
		row.add(label, BorderLayout.CENTER);

		int scored = spoons.luckiestFirst().size();
		JLabel count = new JLabel(scored == 0 ? "none yet" : String.valueOf(scored));
		count.setFont(Theme.body());
		count.setForeground(Theme.TEXT_MUTED);
		row.add(count, BorderLayout.EAST);

		row.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent event)
			{
				spooniestOpen = !spooniestOpen;
				rebuild();
			}
		});

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
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

	private JPanel row(Spoon spoon)
	{
		JPanel row = new JPanel(new BorderLayout(Cards.ROW_GAP, 0));
		row.setBackground(Theme.CARD);
		row.setBorder(BorderFactory.createEmptyBorder(Cards.ROW_PAD, Cards.ROW_PAD, Cards.ROW_PAD, Cards.ROW_PAD));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		row.add(icons.label(spoon.getItemId(), spoon.getItemName()), BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(row.getBackground());

		// Wrapped: item names run to "Ancient ceremonial mask" and a plain label asks for the lot on
		// one line, which is enough to widen the whole sidebar.
		JLabel name = new JLabel(Cards.wrap(spoon.getItemName(), Cards.NAME_WRAP));
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
		JLabel luck = new JLabel(GroupView.percent(spoon.getShare()));
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
