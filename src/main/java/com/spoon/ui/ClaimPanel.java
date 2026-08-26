package com.spoon.ui;

import com.spoon.data.Claim;
import java.awt.Component;
import java.awt.Dimension;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Claiming a drop the plugin never saw.
 * <p>
 * Everything except the item is optional, because a drop from three years ago is remembered in
 * pieces. A claim with no kill count still belongs on the board once the group agrees; it simply
 * cannot be scored, and the voting screen says so.
 */
public class ClaimPanel extends JPanel
{
	public ClaimPanel(Consumer<Claim> onSubmit, Runnable onBack)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Theme.BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(4, 0, 8, Cards.SCROLLBAR_ALLOWANCE));

		add(Header.build("Claim a drop"));
		add(Cards.gap(10));
		add(Cards.muted("For something you got before this plugin was watching. The rest of the group "
			+ "votes on it, and more than half of them have to believe you."));

		add(Cards.gap(12));
		JTextField item = field("Item");
		JTextField source = field("Where from");
		JTextField killCount = field("Kill count");
		JTextField evidence = field("Link to a screenshot");
		JTextField note = field("Anything to add");

		add(Cards.gap(14));
		add(new ActionTile("Put it to the group", () ->
		{
			String name = item.getText().trim();
			if (name.isEmpty())
			{
				Cards.warn(this, "Which item?");
				return;
			}

			Claim claim = new Claim();
			claim.setItemName(name);
			claim.setSource(source.getText().trim());
			claim.setEvidence(evidence.getText().trim());
			claim.setNote(note.getText().trim());

			String kc = killCount.getText().trim();
			if (!kc.isEmpty())
			{
				try
				{
					claim.setKillCount(Integer.valueOf(kc));
				}
				catch (NumberFormatException e)
				{
					Cards.warn(this, "\"" + kc + "\" is not a kill count.");
					return;
				}
			}

			onSubmit.accept(claim);
		}));

		add(Cards.gap(8));
		add(Cards.muted("Only the item is needed. Without a kill count it can still go on the board, "
			+ "but it can never be scored."));

		add(Cards.gap(12));
		JButton back = Cards.button("← Back");
		back.setAlignmentX(Component.LEFT_ALIGNMENT);
		back.addActionListener(event -> onBack.run());
		add(back);

		SwingUtilities.invokeLater(item::requestFocusInWindow);
	}

	private JTextField field(String label)
	{
		add(Cards.sectionLabel(label));
		add(Cards.gap(2));

		JTextField field = Theme.textField(new JTextField());
		field.setAlignmentX(Component.LEFT_ALIGNMENT);
		field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		field.setFont(Theme.body());
		add(field);
		add(Cards.gap(8));

		return field;
	}
}
