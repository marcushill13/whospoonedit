package com.spoon.data;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Working out which casket a reward came out of, from whatever the game called it.
 */
public class ClueRewardsTest
{
	/**
	 * The same tier arrives worded three ways depending on which part of the game is speaking, and a
	 * tier that is not recognised means the reward is never scored.
	 */
	@Test
	public void recognisesATierHoweverItIsWorded()
	{
		assertEquals("hard", ClueRewards.tierOf("Clue Scroll (Hard)"));
		assertEquals("hard", ClueRewards.tierOf("Reward Casket (hard)"));
		assertEquals("master", ClueRewards.tierOf("Clue Scroll (Master)"));
		assertEquals("beginner", ClueRewards.tierOf("beginner Treasure Trails"));
	}

	@Test
	public void aMonsterIsNotAClue()
	{
		assertNull(ClueRewards.tierOf("Cockatrice"));
		assertNull(ClueRewards.tierOf("Vorkath"));
		assertNull(ClueRewards.tierOf(null));
	}
}
