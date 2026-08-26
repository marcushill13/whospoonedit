package com.spoon;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Runs a development client with this plugin loaded. Not shipped. */
public class WhoSpoonedItPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(WhoSpoonedItPlugin.class);
		RuneLite.main(args);
	}
}
