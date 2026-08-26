package com.spoon.track;

import com.spoon.WhoSpoonedItConfig;
import com.spoon.data.Spoon;
import com.spoon.net.SpoonApi;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends this account's drops to the groups it is in.
 *
 * <p>Nothing is worked out here. What a drop is worth is the service's business; this decides only
 * what has not been sent yet.
 *
 * <p>A collection log slot is filled a few times a week at best, so there is no batching problem to
 * solve — the timer exists to catch what a failed send left behind, and everything else goes within
 * seconds of happening.
 */
@Slf4j
@Singleton
public class DropSender
{
	/** The safety net: picks up anything a failed send or a closed client left behind. */
	private static final int EVERY_SECONDS = 120;

	/** How long after a drop it goes out. Long enough to gather a double drop, short enough to feel live. */
	private static final int SOON_SECONDS = 5;

	/** The service takes 200 at a time; a first share of a long-standing log can exceed that. */
	private static final int BATCH = 100;

	private final ScheduledExecutorService executor;
	private final SpoonStore spoons;
	private final GroupStore groups;
	private final SpoonApi api;
	private final WhoSpoonedItConfig config;

	private ScheduledFuture<?> scheduled;
	private ScheduledFuture<?> soon;

	/** Told after anything lands, so an open leaderboard catches up on its own. */
	private Runnable onSent = () ->
	{
	};

	@Inject
	private DropSender(
		ScheduledExecutorService executor,
		SpoonStore spoons,
		GroupStore groups,
		SpoonApi api,
		WhoSpoonedItConfig config)
	{
		this.executor = executor;
		this.spoons = spoons;
		this.groups = groups;
		this.api = api;
		this.config = config;
	}

	public void setOnSent(Runnable onSent)
	{
		this.onSent = onSent;
	}

	public void start()
	{
		stop();
		scheduled = executor.scheduleWithFixedDelay(
			this::flush, EVERY_SECONDS, EVERY_SECONDS, TimeUnit.SECONDS);
	}

	public void stop()
	{
		if (scheduled != null)
		{
			scheduled.cancel(false);
			scheduled = null;
		}

		if (soon != null)
		{
			soon.cancel(false);
			soon = null;
		}
	}

	/**
	 * Sends shortly rather than at the next sweep. Ignored if one is already on its way, so a double
	 * drop goes in one request.
	 */
	public synchronized void nudge()
	{
		if (soon != null && !soon.isDone())
		{
			return;
		}

		soon = executor.schedule((Runnable) this::flush, SOON_SECONDS, TimeUnit.SECONDS);
	}

	/**
	 * Sends whatever each group has not had yet. Safe to call at any time and safe to call twice: the
	 * ids mean a request that landed before timing out will not count again.
	 */
	public void flush()
	{
		try
		{
			boolean sentAnything = false;

			for (GroupStore.Membership membership : new ArrayList<>(groups.all()))
			{
				sentAnything |= flush(membership);
			}

			if (sentAnything)
			{
				onSent.run();
			}
		}
		catch (Exception e)
		{
			// A scheduled task that throws stops being scheduled, which would quietly end all sharing.
			log.warn("Sending failed", e);
		}
	}

	private boolean flush(GroupStore.Membership membership)
	{
		String code = membership.group.getCode();
		String token = membership.memberToken;

		if (token == null || token.isEmpty())
		{
			// Joined on another account, or the group was only ever created and never joined from here.
			return false;
		}

		List<Spoon> waiting = unsent(membership);
		if (waiting.isEmpty())
		{
			return false;
		}

		boolean sentAnything = false;

		// Sent in batches, because a first share of a log built up over years is hundreds of drops and
		// the service takes a couple of hundred at a time.
		for (int from = 0; from < waiting.size(); from += BATCH)
		{
			List<Spoon> batch = waiting.subList(from, Math.min(from + BATCH, waiting.size()));

			SpoonApi.Result<SpoonApi.Snapshot> result =
				api.submit(config.serverUrl(), code, token, batch);

			if (!result.ok())
			{
				// Left unsent on purpose. The next run tries again, and nothing is marked as landed that
				// did not land.
				log.debug("Could not send {} drops to {}: {}", batch.size(), code, result.getError());
				break;
			}

			List<String> ids = new ArrayList<>(batch.size());
			for (Spoon spoon : batch)
			{
				ids.add(spoon.getId());
			}

			groups.markSent(code, ids);
			sentAnything = true;
		}

		return sentAnything;
	}

	/**
	 * What this group has not been told about: everything recorded since sharing began, less whatever
	 * has already landed.
	 */
	private List<Spoon> unsent(GroupStore.Membership membership)
	{
		List<Spoon> waiting = new ArrayList<>();

		for (Spoon spoon : spoons.all())
		{
			if (spoon.getObtainedAt() < membership.sharedFrom)
			{
				// From before this account joined, and not asked to be shared.
				continue;
			}

			if (membership.sent != null && membership.sent.contains(spoon.getId()))
			{
				continue;
			}

			waiting.add(spoon);
		}

		return waiting;
	}

	/** How many drops a group has not been told about, for the button that offers to send them. */
	public int waitingFor(String code)
	{
		GroupStore.Membership membership = groups.find(code);
		return membership == null ? 0 : unsent(membership).size();
	}
}
