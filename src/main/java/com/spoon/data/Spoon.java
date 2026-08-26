package com.spoon.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One collection log item, and how lucky getting it was.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Spoon
{
	private String itemName = "";
	private int itemId;

	/** The monster it came off, as the game named it. Empty when nothing could be attributed. */
	private String source = "";

	/** The kill count it landed on, or -1 when nothing knew it. */
	private int killCount = -1;

	/** The "1 in N", or -1 when the item is not in the drop data. */
	private double denominator = -1;

	/**
	 * The share of players who would already have this by that kill count. Low is lucky. -1 when it
	 * could not be worked out, which is not the same as unlucky and must never be sorted as though it
	 * were.
	 */
	private double share = -1;

	private long obtainedAt;

	/** Typed in by the player rather than seen happening, and shown as such. */
	private boolean claimed;

	public boolean isScored()
	{
		return share >= 0;
	}
}
