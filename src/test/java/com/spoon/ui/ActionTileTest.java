package com.spoon.ui;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * That a tile runs whatever the action is <em>now</em>, not whatever it was when the tile was built.
 */
public class ActionTileTest
{
	/**
	 * The bug this was written for: two buttons that did nothing.
	 * <p>
	 * The panel is built by injection before the plugin has said what its buttons do, so a tile handed
	 * the action directly keeps the empty default for ever. Reading it through a lambda at click time
	 * is what makes a later hand-over take effect.
	 */
	@Test
	public void picksUpAnActionSetAfterItWasBuilt()
	{
		AtomicInteger runs = new AtomicInteger();

		// Stands in for the panel's field, empty at build time and filled later.
		Runnable[] action = { () -> { } };
		ActionTile tile = new ActionTile("Create", () -> action[0].run());

		tile.press();
		assertEquals("the empty default should have run", 0, runs.get());

		action[0] = runs::incrementAndGet;

		tile.press();
		assertEquals("the action set afterwards should have run", 1, runs.get());
	}
}
