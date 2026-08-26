package com.spoon.data;

/**
 * How lucky a drop was, on a scale everyone can be compared on.
 * <p>
 * A raw kill count says nothing on its own, ten kills for a 1/128 is unremarkable, ten kills for a
 * 1/5000 is a story. What matters is how you did against the people who went for the same thing.
 * <p>
 * So a drop is scored by the share of players who would already have had it by the kill count you got
 * it on. Getting an item at the point where only 3% of people have it makes you spoonier than 97% of
 * the accounts that chased it, whatever the rate happens to be. That puts a pet and a clue item on one
 * scale, which is the only way a leaderboard across different bosses means anything.
 * <p>
 * This is the same figure Dink puts in its Discord messages, deliberately: a group that has been
 * reading "Top 40% (Lucky)" for months should not be told a different number by this.
 */
public final class Luck
{
	private Luck()
	{
	}

	/**
	 * The share of players who would already hold this item by the given kill count.
	 * <p>
	 * Low is lucky. A result of 0.03 means you got it while 97% of people were still going.
	 *
	 * @param denominator the "1 in N" of the drop
	 * @param killCount   the kill count it landed on
	 * @return between 0 and 1, or -1 when it cannot be worked out
	 */
	public static double shareWhoHaveItBy(double denominator, int killCount)
	{
		if (denominator <= 0 || killCount <= 0)
		{
			// No rate for this item, or no kill count recorded against it. Both happen, a clue scroll
			// item has no source to count kills of, and neither can be scored.
			return -1;
		}

		double perKill = 1.0 / denominator;

		// 1 - (1-p)^n, the chance of at least one success in n attempts. Worked out through logs rather
		// than Math.pow so that the very long odds people actually care about, a 1 in 5000 pet over
		// thousands of kills, do not lose their precision.
		return -Math.expm1(killCount * Math.log1p(-perKill));
	}

	/** Whether a drop counts as spooned: it came before most people would have had it. */
	public static boolean isSpooned(double share)
	{
		return share >= 0 && share < 0.5;
	}
}
