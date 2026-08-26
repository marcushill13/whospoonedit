package com.spoon.track;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * Turning the name the game uses when something dies into the name its kill count is filed under.
 */
public class KillCountsTest
{
	@Test
	public void ordinaryMonstersJustLowercase()
	{
		assertEquals("cockatrice", KillCounts.cleanName("Cockatrice"));
		assertEquals("demonic gorilla", KillCounts.cleanName("Demonic gorilla"));
	}

	@Test
	public void colonsAreDropped()
	{
		assertEquals("kree'arra", KillCounts.cleanName("Kree'arra"));
		assertEquals("tzkal-zuk", KillCounts.cleanName("TzKal-Zuk"));
	}

	/**
	 * The ones where what dies is not what the kill count is called. Get these wrong and the drop is
	 * recorded with no kill count, which quietly means it cannot be scored.
	 */
	@Test
	public void bossesFiledUnderSomethingElse()
	{
		assertEquals("gauntlet", KillCounts.cleanName("Crystalline Hunllef"));
		assertEquals("corrupted gauntlet", KillCounts.cleanName("Corrupted Hunllef"));
		assertEquals("whisperer", KillCounts.cleanName("The Whisperer"));
		assertEquals("leviathan", KillCounts.cleanName("The Leviathan"));
		assertEquals("hueycoatl", KillCounts.cleanName("The Hueycoatl"));
		assertEquals("barrows chests", KillCounts.cleanName("Barrows"));
	}
}
