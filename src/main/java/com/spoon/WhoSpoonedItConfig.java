package com.spoon;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("whospoonedit")
public interface WhoSpoonedItConfig extends Config
{
	@ConfigItem(
		keyName = "serverUrl",
		name = "Server address",
		description = "Only used once you join a group. Change it if your group runs its own.",
		position = 1
	)
	default String serverUrl()
	{
		return "https://spoons.marcushill3313.workers.dev";
	}

	@ConfigItem(
		keyName = "announceInChat",
		name = "Say it in chat",
		description = "Prints how lucky a collection log drop was, the moment you get it.",
		position = 2
	)
	default boolean announceInChat()
	{
		return true;
	}
}
