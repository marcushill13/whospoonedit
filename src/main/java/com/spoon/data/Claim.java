package com.spoon.data;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A drop somebody says they got, waiting on the group.
 */
@Data
@NoArgsConstructor
public class Claim
{
	private String id = "";
	private String rsn = "";

	private String itemName = "";
	private int itemId = -1;

	private String source;
	private Integer killCount;
	private Double denominator;

	/** A link to a screenshot, if they had one to hand. */
	private String evidence;
	private String note;

	private long createdAt;

	private int approvals;
	private int rejections;

	/** How many yes votes carry it: more than half of everybody except the claimant. */
	private int needed = 1;

	/** This is your own claim, so you cannot vote on it. */
	private boolean yours;

	/** How you voted, or null if you have not. */
	private Boolean yourVote;
}
