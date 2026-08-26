package com.spoon;

import com.google.inject.Provides;
import com.spoon.data.Luck;
import com.spoon.data.Spoon;
import com.spoon.track.ClogWatcher;
import com.spoon.track.GroupStore;
import com.spoon.track.SpoonStore;
import com.spoon.net.SpoonApi;
import com.spoon.ui.CreateGroupPanel;
import com.spoon.ui.GroupView;
import com.spoon.ui.JoinGroupPanel;
import com.spoon.ui.Medals;
import com.spoon.ui.SpoonPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
	name = "Who Spooned It?",
	description = "Records how lucky each collection log drop was, and compares it with your group",
	tags = {"collection", "log", "clog", "luck", "drop", "rate", "spoon", "group"}
)
public class WhoSpoonedItPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private EventBus eventBus;

	@Inject
	private ClogWatcher watcher;

	@Inject
	private SpoonStore spoons;

	@Inject
	private GroupStore groups;

	@Inject
	private SpoonPanel panel;

	@Inject
	private SpoonApi api;

	@Inject
	private Medals medals;

	@Inject
	private java.util.concurrent.ScheduledExecutorService executor;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ChatMessageManager chatMessages;

	@Inject
	private WhoSpoonedItConfig config;

	private NavigationButton navigationButton;

	@Provides
	WhoSpoonedItConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WhoSpoonedItConfig.class);
	}

	@Override
	protected void startUp()
	{
		// Registered by hand rather than being a plugin of its own, so it stops listening the moment
		// this plugin is switched off.
		eventBus.register(watcher);
		watcher.setOnCaptured(this::record);

		spoons.load();
		groups.load();

		// Wired here rather than inside the panel, so the panel stays a view and does not have to know
		// how a group gets made.
		panel.setActions(this::createGroup, this::joinGroup, this::openGroup);

		navigationButton = NavigationButton.builder()
			.tooltip("Who Spooned It?")
			.icon(icon())
			.priority(7)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navigationButton);
	}

	@Override
	protected void shutDown()
	{
		eventBus.unregister(watcher);
		clientToolbar.removeNavigation(navigationButton);
	}

	/**
	 * A drop has been seen. Kept, and said out loud if the player wants it said.
	 */
	private void record(Spoon spoon)
	{
		if (!spoons.add(spoon))
		{
			// Already had it. The log announces a slot the first time it fills, so this means a repeat
			// rather than a second drop, and the first record is the one worth keeping.
			return;
		}

		panel.refresh();

		if (config.announceInChat() && spoon.isScored())
		{
			announce(spoon);
		}
	}

	private void announce(Spoon spoon)
	{
		int top = Math.max(1, (int) Math.round(spoon.getShare() * 100));
		String verdict = Luck.isSpooned(spoon.getShare()) ? "Spooned!" : "About time.";

		chatMessages.queue(QueuedMessage.builder()
			.type(net.runelite.api.ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(String.format(
				"%s %s on kill %d — top %d%% of everyone who went for it.",
				verdict, spoon.getItemName(), spoon.getKillCount(), top))
			.build());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// Drops are stored per account, so they cannot be read until there is an account to read
			// them for. Logging in on a second character swaps them over rather than merging them.
			spoons.load();
			groups.load();
			panel.refresh();
		}
	}

	private void createGroup()
	{
		panel.show(new CreateGroupPanel(this::doCreate, panel::showList));
	}

	private void joinGroup()
	{
		panel.show(new JoinGroupPanel(this::doJoin, panel::showList));
	}

	private void doCreate(String name)
	{
		String rsn = localPlayerName();
		if (rsn == null)
		{
			warn("Log in first — a group is joined under your own name.");
			return;
		}

		executor.execute(() ->
		{
			SpoonApi.Result<SpoonApi.Snapshot> result = api.create(config.serverUrl(), name, rsn);

			SwingUtilities.invokeLater(() ->
			{
				if (!result.ok())
				{
					warn(result.getError());
					return;
				}

				SpoonApi.Snapshot snapshot = result.getValue();
				groups.put(snapshot.getGroup(), snapshot.getCreatorToken(), snapshot.getMemberToken());
				openGroup(snapshot.getGroup().getCode());
			});
		});
	}

	private void doJoin(String code)
	{
		String rsn = localPlayerName();
		if (rsn == null)
		{
			warn("Log in first — a group is joined under your own name.");
			return;
		}

		executor.execute(() ->
		{
			SpoonApi.Result<SpoonApi.Snapshot> result = api.join(config.serverUrl(), code, rsn);

			SwingUtilities.invokeLater(() ->
			{
				if (!result.ok())
				{
					warn(result.getError());
					return;
				}

				SpoonApi.Snapshot snapshot = result.getValue();
				groups.put(snapshot.getGroup(), null, snapshot.getMemberToken());
				openGroup(snapshot.getGroup().getCode());
			});
		});
	}

	private void openGroup(String code)
	{
		executor.execute(() ->
		{
			SpoonApi.Result<SpoonApi.Snapshot> result = api.read(config.serverUrl(), code);

			SwingUtilities.invokeLater(() ->
			{
				if (result.isGone())
				{
					// The service answered and said there is no such group: whoever made it deleted it.
					// Keeping it on the list means a card that cannot be opened and cannot be removed.
					forget(code);
					return;
				}

				if (!result.ok())
				{
					warn(result.getError());
					panel.showList();
					return;
				}

				SpoonApi.Snapshot snapshot = result.getValue();
				groups.put(snapshot.getGroup(), null, null);

				boolean creator = groups.creatorTokenFor(code) != null;

				GroupView view = new GroupView(
					snapshot.getGroup(),
					snapshot.getLeaderboard(),
					localPlayerName(),
					creator,
					medals::forPlace,
					query -> search(code, query),
					panel::showList,
					() -> openGroup(code),
					() -> leave(code, creator));

				panel.show(view);
				this.openView = view;
			});
		});
	}

	/** Held so search results can be filled into the group screen that asked for them. */
	private GroupView openView;

	private void search(String code, String query)
	{
		if (query == null || query.trim().length() < 2)
		{
			if (openView != null)
			{
				openView.showSearchMessage("Type at least two letters of an item's name.");
			}

			return;
		}

		GroupView asked = openView;
		asked.showSearchMessage("Looking...");

		executor.execute(() ->
		{
			SpoonApi.Result<java.util.List<com.spoon.data.Holder>> result =
				api.whoSpoonedIt(config.serverUrl(), code, query.trim());

			SwingUtilities.invokeLater(() ->
			{
				// Only if that same screen is still up. A slow answer arriving after someone has gone
				// back should change nothing.
				if (openView != asked)
				{
					return;
				}

				if (!result.ok())
				{
					asked.showSearchMessage(result.getError());
					return;
				}

				asked.showHolders(query.trim(), result.getValue(), medals::forPlace);
			});
		});
	}

	private void leave(String code, boolean creator)
	{
		String question = creator
			? "Delete this group? It goes for everyone in it, along with every drop recorded in it."
			: "Leave this group? Your drops stay on your own machine.";

		int answer = javax.swing.JOptionPane.showConfirmDialog(
			panel, question, "Who Spooned It?", javax.swing.JOptionPane.YES_NO_OPTION);

		if (answer != javax.swing.JOptionPane.YES_OPTION)
		{
			return;
		}

		String creatorToken = groups.creatorTokenFor(code);

		if (creator && creatorToken != null)
		{
			executor.execute(() -> api.delete(config.serverUrl(), code, creatorToken));
		}

		groups.remove(code);
		panel.showList();
	}

	/** Takes a group that no longer exists off this account's list, once the player says so. */
	private void forget(String code)
	{
		int answer = javax.swing.JOptionPane.showConfirmDialog(
			panel,
			"That group no longer exists. Remove it from your list?",
			"Who Spooned It?",
			javax.swing.JOptionPane.YES_NO_OPTION);

		if (answer == javax.swing.JOptionPane.YES_OPTION)
		{
			groups.remove(code);
		}

		panel.showList();
	}

	private void warn(String message)
	{
		javax.swing.JOptionPane.showMessageDialog(
			panel, message, "Who Spooned It?", javax.swing.JOptionPane.INFORMATION_MESSAGE);
	}

	/** The name drops are recorded under. Null until logged in. */
	private String localPlayerName()
	{
		return client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName();
	}

	/**
	 * The sidebar icon: a spoon, drawn rather than shipped as a file so there is one less thing for the
	 * plugin hub's packaging to get wrong.
	 */
	private BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		graphics.setColor(new Color(240, 176, 62));
		graphics.fillOval(5, 1, 6, 7);
		graphics.fillRoundRect(7, 7, 2, 8, 2, 2);

		graphics.dispose();
		return image;
	}
}
