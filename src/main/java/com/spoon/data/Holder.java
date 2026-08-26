package com.spoon.data;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One person's copy of one item, for the "who spooned it" search.
 * <p>
 * Doubles as a search result, where only the name, id and holder count are filled in.
 */
@Data
@NoArgsConstructor
public class Holder
{
	private String rsn = "";
	private String itemName = "";
	private int itemId = -1;

	private String source;
	private Integer killCount;
	private Double denominator;

	/** Null when the drop could not be scored, which is not the same as having been unlucky. */
	private Double share;

	private int place;
	private boolean claimed;
	private long obtainedAt;

	/** Only on search results: how many people in the group have it. */
	private int holders;
}
