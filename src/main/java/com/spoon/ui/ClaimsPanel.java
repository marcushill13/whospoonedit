package com.spoon.ui;

import com.spoon.data.Claim;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/**
 * Drops the group is still voting on.
 * <p>
 * Kept apart from the leaderboard on purpose. Nothing here counts for anything yet, and a claim
 * sitting among real drops would look like one.
 */
public class ClaimsPanel extends JPanel
{
	private final BiConsumer<String, Boolean> onVote;
	private final Consumer<String> onOpenEvidence;

	public ClaimsPanel(BiConsumer<String, Boolean> onVote, Consumer<String> onOpenEvidence)
	{
		this.onVote = onVote;
		this.onOpenEvidence = onOpenEvidence;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Theme.BACKGROUND);
		setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	public void show(List<Claim> claims)
	{
		removeAll();

		if (claims.isEmpty())
		{
			add(Cards.muted("Nothing waiting on a vote."));
		}

		for (Claim claim : claims)
		{
			add(row(claim));
			add(Cards.gap(3));
		}

		revalidate();
		repaint();
	}

	public void showMessage(String message)
	{
		removeAll();
		add(Cards.muted(message));
		revalidate();
		repaint();
	}

	private JPanel row(Claim claim)
	{
		JPanel card = Cards.card();

		JLabel who = new JLabel(Cards.wrap(claim.getRsn() + " says they got", 150));
		who.setFont(Theme.body());
		who.setForeground(Theme.TEXT_MUTED);
		who.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(who);

		JLabel item = new JLabel(Cards.wrap(claim.getItemName(), 150));
		item.setFont(FontManager.getRunescapeBoldFont());
		item.setForeground(Theme.GOLD);
		item.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(item);

		StringBuilder detail = new StringBuilder();
		if (claim.getKillCount() != null)
		{
			detail.append("kc ").append(claim.getKillCount());
		}
		else
		{
			// Said plainly. A claim with no kill count can be voted onto the board and can never be
			// scored, and somebody voting on it should know that before they do.
			detail.append("no kill count given");
		}

		if (claim.getSource() != null && !claim.getSource().isEmpty())
		{
			detail.append(" · ").append(claim.getSource());
		}

		card.add(Cards.gap(1));
		card.add(Cards.mutedInRow(detail.toString()));

		if (claim.getNote() != null && !claim.getNote().isEmpty())
		{
			card.add(Cards.gap(2));
			card.add(Cards.muted("“" + claim.getNote() + "”"));
		}

		card.add(Cards.gap(4));
		card.add(Cards.mutedInRow(claim.getApprovals() + " of " + claim.getNeeded() + " needed"
			+ (claim.getRejections() > 0 ? " · " + claim.getRejections() + " against" : "")));

		if (claim.getEvidence() != null && !claim.getEvidence().isEmpty())
		{
			card.add(Cards.gap(4));
			JButton evidence = Cards.button("Open the screenshot");
			evidence.setAlignmentX(Component.LEFT_ALIGNMENT);
			evidence.addActionListener(event -> onOpenEvidence.accept(claim.getEvidence()));
			card.add(evidence);
		}

		card.add(Cards.gap(4));
		card.add(voteRow(claim));

		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		return card;
	}

	private JPanel voteRow(Claim claim)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(Theme.CARD);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		if (claim.isYours())
		{
			// Nobody carries their own claim, which is the point of putting it to the group.
			JLabel yours = new JLabel("Yours, waiting on the others");
			yours.setFont(Theme.body());
			yours.setForeground(Theme.TEXT_MUTED);
			row.add(yours, BorderLayout.CENTER);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
			return row;
		}

		JPanel buttons = new JPanel(new java.awt.GridLayout(1, 2, 4, 0));
		buttons.setBackground(Theme.CARD);

		JButton yes = Cards.button(Boolean.TRUE.equals(claim.getYourVote()) ? "Believed ✓" : "Believe");
		yes.addActionListener(event -> onVote.accept(claim.getId(), true));
		buttons.add(yes);

		JButton no = Cards.button(Boolean.FALSE.equals(claim.getYourVote()) ? "Doubted ✓" : "Doubt");
		no.addActionListener(event -> onVote.accept(claim.getId(), false));
		buttons.add(no);

		row.add(buttons, BorderLayout.CENTER);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		return row;
	}
}
