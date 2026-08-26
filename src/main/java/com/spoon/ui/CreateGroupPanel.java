package com.spoon.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Making a group: a name, and that is all.
 * <p>
 * Nothing else is asked for because nothing else is needed. A group has no rules to agree and no
 * settings to get wrong — it is a set of people and their collection logs, and everything worth
 * arguing about comes out of the drops themselves.
 */
public class CreateGroupPanel extends JPanel
{
	public CreateGroupPanel(Consumer<String> onCreate, Runnable onBack)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Theme.BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(4, 0, 8, Cards.SCROLLBAR_ALLOWANCE));

		add(Header.build("Start a group"));
		add(Cards.gap(14));

		add(Cards.sectionLabel("Group name"));
		add(Cards.gap(4));

		JTextField name = Theme.textField(new JTextField());
		name.setAlignmentX(Component.LEFT_ALIGNMENT);
		name.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		name.setFont(Theme.figure(14f));
		add(name);

		add(Cards.gap(6));
		add(Cards.muted("What your mates will see. \"THE Tards\", \"GIM Buddies\", whatever you call "
			+ "yourselves."));

		add(Cards.gap(14));
		add(new ActionTile("Create", () ->
		{
			String typed = name.getText().trim();
			if (typed.isEmpty())
			{
				Cards.warn(this, "Give it a name first.");
				return;
			}

			onCreate.accept(typed);
		}));

		add(Cards.gap(10));
		add(Cards.muted("You will get a code to share. Anyone with it can join."));

		add(Cards.gap(12));
		JButton back = Cards.button("← Back");
		back.setAlignmentX(Component.LEFT_ALIGNMENT);
		back.addActionListener(event -> onBack.run());
		add(back);

		// The only field on the screen, so the cursor belongs in it.
		SwingUtilities.invokeLater(name::requestFocusInWindow);
	}
}
