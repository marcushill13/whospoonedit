package com.spoon;

import com.google.inject.Provides;
import com.spoon.data.Luck;
import com.spoon.data.Spoon;
import com.spoon.track.ClogWatcher;
import com.spoon.track.SpoonStore;
import com.spoon.ui.SpoonPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
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
	private SpoonPanel panel;

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
			panel.refresh();
		}
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
