package com.spoon.data;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of a group's leaderboard.
 */
@Data
@NoArgsConstructor
public class Standing
{
	private String rsn = "";

	/** How many drops came in ahead of the rate. What the ranking is on, and what people argue about. */
	private int spoons;

	/** How many of their drops could be scored at all. */
	private int scored;

	private int place;

	/**
	 * Mean share across their scored drops: how spooned this account is. Low is lucky.
	 * <p>
	 * Averaged over what they have, not what they are still waiting on, so it flatters everybody a
	 * little — going a thousand dry on a pet costs nothing here because there is no drop to score.
	 */
	private double avgShare = 0.5;
}
