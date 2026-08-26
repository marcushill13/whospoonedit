package com.spoon.track;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.spoon.data.Group;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * The groups this account is in, and the tokens that prove it.
 * <p>
 * More than one is expected. A clan competition and a group of mates are different arguments about
 * the same collection log, and being in both should not mean choosing.
 */
@Slf4j
@Singleton
public class GroupStore
{
	private static final String CONFIG_GROUP = "whospoonedit";
	private static final String KEY = "groups";

	/** One group, with whatever this account is allowed to do in it. */
	public static class Membership
	{
		public Group group = new Group();

		/** Held only by whoever created it. Its absence is what hides the creator's controls. */
		public String creatorToken;

		public String memberToken;

		/**
		 * Drops from this moment on are shared with this group; earlier ones are not.
		 * <p>
		 * Set to the moment of joining, because joining a group should not hand over a collection log
		 * built up over years without being asked. Pressing "share my earlier drops" sets it to zero,
		 * which is that asking.
		 */
		public long sharedFrom;

		/** Ids the service has taken, so nothing is sent twice and a failure can be retried. */
		public java.util.Set<String> sent = new java.util.HashSet<>();

		public boolean isCreator()
		{
			return creatorToken != null && !creatorToken.isEmpty();
		}
	}

	private final ConfigManager configManager;
	private final Gson gson;

	private final List<Membership> memberships = new ArrayList<>();

	@Inject
	private GroupStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	public void load()
	{
		memberships.clear();

		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		String json = configManager.getRSProfileConfiguration(CONFIG_GROUP, KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}

		Type type = new TypeToken<List<Membership>>()
		{
		}.getType();

		try
		{
			List<Membership> saved = gson.fromJson(json, type);
			if (saved != null)
			{
				memberships.addAll(saved);
			}
		}
		catch (JsonSyntaxException e)
		{
			// Never drop the lot because one entry is unreadable; that would quietly remove someone from
			// a group they had joined, along with the token that proves they belong.
			log.warn("Could not read saved groups", e);
		}
	}

	private void save()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}

		configManager.setRSProfileConfiguration(CONFIG_GROUP, KEY, gson.toJson(memberships));
	}

	public List<Membership> all()
	{
		return Collections.unmodifiableList(memberships);
	}

	@Nullable
	public Membership find(String code)
	{
		for (Membership membership : memberships)
		{
			if (membership.group.getCode().equalsIgnoreCase(code))
			{
				return membership;
			}
		}

		return null;
	}

	/**
	 * Adds a group, or updates one already known. Tokens already held are kept when the incoming copy
	 * has none, a refresh carries the group but not the secrets.
	 */
	public void put(Group group, @Nullable String creatorToken, @Nullable String memberToken)
	{
		Membership existing = find(group.getCode());

		if (existing == null)
		{
			existing = new Membership();
			memberships.add(existing);
		}

		existing.group = group;

		if (creatorToken != null && !creatorToken.isEmpty())
		{
			existing.creatorToken = creatorToken;
		}

		if (memberToken != null && !memberToken.isEmpty())
		{
			// The moment of joining, and only the first time: a later refresh must not move the line and
			// quietly un-share what has already gone.
			if (existing.memberToken == null)
			{
				existing.sharedFrom = System.currentTimeMillis();
			}

			existing.memberToken = memberToken;
		}

		save();
	}

	/** Records that these drops have landed, so they are not sent again. */
	public void markSent(String code, java.util.Collection<String> ids)
	{
		Membership membership = find(code);
		if (membership == null)
		{
			return;
		}

		if (membership.sent == null)
		{
			membership.sent = new java.util.HashSet<>();
		}

		membership.sent.addAll(ids);
		save();
	}

	/**
	 * Shares everything already recorded with this group, from here on.
	 * <p>
	 * The deliberate act the join screen promises: nothing you already have goes anywhere until you
	 * press the button.
	 */
	public void shareEverything(String code)
	{
		Membership membership = find(code);
		if (membership != null)
		{
			membership.sharedFrom = 0;
			save();
		}
	}

	public void remove(String code)
	{
		memberships.removeIf(membership -> membership.group.getCode().equalsIgnoreCase(code));
		save();
	}

	@Nullable
	public String memberTokenFor(String code)
	{
		Membership membership = find(code);
		return membership == null ? null : membership.memberToken;
	}

	@Nullable
	public String creatorTokenFor(String code)
	{
		Membership membership = find(code);
		return membership == null ? null : membership.creatorToken;
	}
}
