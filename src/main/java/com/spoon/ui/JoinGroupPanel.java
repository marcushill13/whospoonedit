package com.spoon.ui;

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
 * Joining: a code, and a button.
 */
public class JoinGroupPanel extends JPanel
{
	public JoinGroupPanel(Consumer<String> onJoin, Runnable onBack)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Theme.BACKGROUND);
		setBorder(BorderFactory.createEmptyBorder(4, 0, 8, Cards.SCROLLBAR_ALLOWANCE));

		add(Header.build("Join a group"));
		add(Cards.gap(14));

		add(Cards.sectionLabel("Group code"));
		add(Cards.gap(4));

		JTextField code = Theme.textField(new JTextField());
		code.setAlignmentX(Component.LEFT_ALIGNMENT);
		code.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		// Big and centred, because a code is read off a phone or out of Discord and typed by hand.
		code.setFont(Theme.figure(18f));
		code.setHorizontalAlignment(JTextField.CENTER);
		add(code);

		add(Cards.gap(6));
		add(Cards.muted("Six characters, from whoever made the group."));

		add(Cards.gap(14));
		add(new ActionTile("Join", () ->
		{
			String typed = code.getText().trim().toUpperCase();
			if (typed.isEmpty())
			{
				Cards.warn(this, "Paste the code first.");
				return;
			}

			onJoin.accept(typed);
		}));

		add(Cards.gap(10));
		add(Cards.muted("Your drops from here on will be compared with theirs. Nothing you already "
			+ "have is sent unless you ask for it."));

		add(Cards.gap(12));
		JButton back = Cards.button("← Back");
		back.setAlignmentX(Component.LEFT_ALIGNMENT);
		back.addActionListener(event -> onBack.run());
		add(back);

		SwingUtilities.invokeLater(code::requestFocusInWindow);
	}
}
