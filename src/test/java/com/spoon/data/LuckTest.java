package com.spoon.data;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LuckTest
{
	/**
	 * Taken from a real Dink message in the group's Discord: Iron boots off Cockatrice, 1 in 128, on
	 * kill 65, which Dink reported as "Top 40% (Lucky)".
	 * <p>
	 * Pinned because agreeing with Dink is a feature. The group has been reading its numbers since
	 * December and a leaderboard that quietly disagreed with them would be argued with, rightly.
	 */
	@Test
	public void agreesWithDink()
	{
		assertEquals(0.40, Luck.shareWhoHaveItBy(128, 65), 0.005);
	}

	@Test
	public void gettingItEarlyIsLucky()
	{
		// A 1 in 5000 on the tenth kill: almost nobody has it that soon.
		double share = Luck.shareWhoHaveItBy(5000, 10);
		assertTrue(share < 0.01);
		assertTrue(Luck.isSpooned(share));
	}

	@Test
	public void goingDryIsNot()
	{
		// The warhammer at 6000 kills, when most people would have had it long before.
		double share = Luck.shareWhoHaveItBy(5000, 6000);
		assertTrue(share > 0.5);
		assertFalse(Luck.isSpooned(share));
	}

	/** Long odds over many kills are where a naive pow() loses its accuracy. */
	@Test
	public void staysPreciseAtLongOdds()
	{
		assertEquals(0.0001, Luck.shareWhoHaveItBy(100000, 10), 1e-7);
		assertTrue(Luck.shareWhoHaveItBy(1000000, 1) > 0);
	}

	@Test
	public void refusesWhatItCannotScore()
	{
		assertEquals(-1, Luck.shareWhoHaveItBy(0, 100), 0);
		assertEquals(-1, Luck.shareWhoHaveItBy(128, 0), 0);
		assertFalse(Luck.isSpooned(-1));
	}
}
