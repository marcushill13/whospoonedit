package com.spoon.data;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A set of people comparing collection logs.
 * <p>
 * Not tied to a clan or a group ironman team on purpose: a handful of mates on separate accounts is
 * the commonest case, and anything that reads an in-game roster would shut them out.
 */
@Data
@NoArgsConstructor
public class Group
{
	/** What people paste to join. Read aloud in Discord, so no characters that sound alike. */
	private String code = "";

	private String name = "";

	/** Who made it, for display. Being the creator is not a rank, it just means they pressed Create. */
	private String creatorRsn = "";

	private int members;

	private long createdAt;
}
