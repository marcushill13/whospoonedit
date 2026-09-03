package com.spoon.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Reading a Discord channel that arrives a chunk at a time.
 *
 * A clan's whole history is more than the service can read into one answer, so it hands back a
 * cursor and is asked again until it says the channel is finished. What is tested here is the asking
 * again: that the totals shown at the end describe the channel rather than the last chunk of it, that
 * a read which gives out partway says what it managed, and that a service which has never heard of
 * chunks is still answered properly.
 */
public class SpoonApiTest
{
	/** A chunk with some drops in it, and where the channel goes on to. */
	private static SpoonApi.Import chunk(int found, int matched, String cursor)
	{
		SpoonApi.Import part = new SpoonApi.Import();
		part.setFound(found);
		part.setSkipped(found * 2);
		part.setMatched(matched);
		part.setImported(matched);
		part.setCursor(cursor);
		part.setDone(cursor == null);
		return part;
	}

	/** Hands back the given chunks in order, and records the cursor it was asked with each time. */
	private static class Channel implements Function<String, SpoonApi.Result<SpoonApi.Import>>
	{
		private final List<SpoonApi.Result<SpoonApi.Import>> chunks;
		private final List<String> asked = new ArrayList<>();

		Channel(List<SpoonApi.Result<SpoonApi.Import>> chunks)
		{
			this.chunks = chunks;
		}

		@Override
		public SpoonApi.Result<SpoonApi.Import> apply(String cursor)
		{
			asked.add(cursor);
			return chunks.get(Math.min(asked.size() - 1, chunks.size() - 1));
		}
	}

	@Test
	public void addsUpEveryChunkIntoOneTotal()
	{
		Channel channel = new Channel(List.of(
			SpoonApi.Result.of(chunk(10, 4, "aaa")),
			SpoonApi.Result.of(chunk(20, 6, "bbb")),
			SpoonApi.Result.of(chunk(5, 1, null))));

		SpoonApi.Result<SpoonApi.Import> result = SpoonApi.readChannel(false, null, channel);

		assertTrue(result.ok());
		assertEquals(35, result.getValue().getFound());
		assertEquals(70, result.getValue().getSkipped());
		assertEquals(11, result.getValue().getMatched());
		assertEquals(11, result.getValue().getImported());
		assertTrue(result.getValue().isDone());
	}

	/** Each chunk is asked for with the cursor the one before it handed back, starting at nothing. */
	@Test
	public void carriesOnFromWhereTheLastChunkStopped()
	{
		Channel channel = new Channel(List.of(
			SpoonApi.Result.of(chunk(10, 4, "aaa")),
			SpoonApi.Result.of(chunk(20, 6, "bbb")),
			SpoonApi.Result.of(chunk(5, 1, null))));

		SpoonApi.readChannel(false, null, channel);

		assertEquals(3, channel.asked.size());
		assertNull(channel.asked.get(0));
		assertEquals("aaa", channel.asked.get(1));
		assertEquals("bbb", channel.asked.get(2));
	}

	/**
	 * A service that answers with no cursor read the channel in one go, and so has an older service
	 * that has never heard of chunks. Both are finished after one ask.
	 */
	@Test
	public void asksOnceOfAServiceThatDoesNotPage()
	{
		SpoonApi.Import whole = new SpoonApi.Import();
		whole.setFound(120);
		whole.setMatched(30);

		Channel channel = new Channel(List.of(SpoonApi.Result.of(whole)));

		SpoonApi.Result<SpoonApi.Import> result = SpoonApi.readChannel(false, null, channel);

		assertEquals(1, channel.asked.size());
		assertEquals(120, result.getValue().getFound());
	}

	/** Names not in the group are counted across the whole channel, not just the chunk they were in. */
	@Test
	public void mergesTheNamesItDidNotRecognise()
	{
		SpoonApi.Import first = chunk(10, 4, "aaa");
		Map<String, Integer> one = new LinkedHashMap<>();
		one.put("Zezima", 3);
		one.put("Woox", 1);
		first.setUnmatched(one);

		SpoonApi.Import second = chunk(10, 4, null);
		Map<String, Integer> two = new LinkedHashMap<>();
		two.put("Zezima", 2);
		second.setUnmatched(two);

		SpoonApi.Result<SpoonApi.Import> result = SpoonApi.readChannel(false, null,
			new Channel(List.of(SpoonApi.Result.of(first), SpoonApi.Result.of(second))));

		assertEquals(Integer.valueOf(5), result.getValue().getUnmatched().get("Zezima"));
		assertEquals(Integer.valueOf(1), result.getValue().getUnmatched().get("Woox"));
	}

	/** Nothing read, nothing to soften: the first failure is handed on exactly as it arrived. */
	@Test
	public void passesOnAFailureOnTheFirstChunk()
	{
		SpoonApi.Result<SpoonApi.Import> result = SpoonApi.readChannel(false, null,
			new Channel(List.of(SpoonApi.Result.failed("Could not reach the server"))));

		assertEquals("Could not reach the server", result.getError());
	}

	/**
	 * A read that gives out partway says what it managed before it did.
	 *
	 * Half a clan's history is not nothing, and somebody told only that it failed will not press the
	 * button again.
	 */
	@Test
	public void saysWhatItBroughtInBeforeItStopped()
	{
		SpoonApi.Result<SpoonApi.Import> result = SpoonApi.readChannel(false, null,
			new Channel(List.of(
				SpoonApi.Result.of(chunk(1000, 400, "aaa")),
				SpoonApi.Result.failed("Could not reach the server"))));

		assertTrue(result.getError(), result.getError().contains("Brought in 400 drops"));
		assertTrue(result.getError(), result.getError().contains("Could not reach the server"));
		assertTrue(result.getError(), result.getError().contains("brought in twice"));
	}

	/** The same, for a look that never kept anything: it counts messages rather than drops. */
	@Test
	public void saysHowFarItReadBeforeItStopped()
	{
		SpoonApi.Result<SpoonApi.Import> result = SpoonApi.readChannel(true, null,
			new Channel(List.of(
				SpoonApi.Result.of(chunk(1000, 400, "aaa")),
				SpoonApi.Result.failed("Could not reach the server"))));

		// A thousand collection log messages and two thousand of everything else.
		assertTrue(result.getError(), result.getError().contains("Read 3,000 messages"));
	}

	/** No bot in the server is the step before importing, and it is the whole answer. */
	@Test
	public void stopsAtOnceWhenThereIsNoBot()
	{
		SpoonApi.Import needsBot = new SpoonApi.Import();
		needsBot.setNeedsBot(true);
		needsBot.setInvite("https://discord.com/oauth2/authorize");

		Channel channel = new Channel(List.of(SpoonApi.Result.of(needsBot)));

		SpoonApi.Result<SpoonApi.Import> result = SpoonApi.readChannel(false, null, channel);

		assertEquals(1, channel.asked.size());
		assertTrue(result.getValue().isNeedsBot());
	}

	/** The running totals go out after every chunk, so a long read can say how it is getting on. */
	@Test
	public void reportsAsItGoes()
	{
		List<Integer> reported = new ArrayList<>();

		SpoonApi.readChannel(false, running -> reported.add(running.getImported()),
			new Channel(List.of(
				SpoonApi.Result.of(chunk(10, 4, "aaa")),
				SpoonApi.Result.of(chunk(10, 6, "bbb")),
				SpoonApi.Result.of(chunk(10, 1, null)))));

		assertEquals(List.of(4, 10, 11), reported);
	}

	/**
	 * A service that never says it has finished is stopped anyway.
	 *
	 * Nothing in a clan's Discord justifies four hundred chunks, so reaching that is a service
	 * misbehaving, and a plugin that asked it forever would hang the panel it was asked from.
	 */
	@Test
	public void givesUpOnAChannelThatNeverEnds()
	{
		Channel channel = new Channel(List.of(SpoonApi.Result.of(chunk(1, 1, "always more"))));

		SpoonApi.Result<SpoonApi.Import> result = SpoonApi.readChannel(false, null, channel);

		assertTrue(result.ok());
		assertEquals(400, channel.asked.size());
	}
}
