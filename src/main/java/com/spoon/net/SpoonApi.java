package com.spoon.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.spoon.data.Group;
import com.spoon.data.Holder;
import com.spoon.data.Standing;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Talks to the service groups share.
 * <p>
 * Every call returns either what was asked for or why not, and never throws at the caller. A panel
 * that has to catch exceptions to show a message ends up showing stack traces to players.
 */
@Slf4j
@Singleton
public class SpoonApi
{
	private static final MediaType JSON = MediaType.get("application/json");

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	private SpoonApi(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/** Either what was asked for, or why not. */
	@Value
	public static class Result<T>
	{
		T value;
		String error;

		/**
		 * The service answered and said there is no such group.
		 * <p>
		 * Kept apart from every other failure because they call for opposite responses: a group that is
		 * gone should come off the list, while one that merely could not be reached must be left alone.
		 * Treating the second as the first would delete somebody's group because their wifi dropped.
		 */
		boolean gone;

		public boolean ok()
		{
			return error == null;
		}

		static <T> Result<T> of(T value)
		{
			return new Result<>(value, null, false);
		}

		static <T> Result<T> failed(String error)
		{
			return new Result<>(null, error, false);
		}

		static <T> Result<T> gone(String error)
		{
			return new Result<>(null, error, true);
		}
	}

	/** A group and its leaderboard, which always travel together. */
	@Value
	public static class Snapshot
	{
		Group group;
		List<Standing> leaderboard;

		/** Only present when the group was just created or joined. */
		String creatorToken;
		String memberToken;
	}

	public Result<Snapshot> create(String baseUrl, String name, String creatorRsn)
	{
		JsonObject body = new JsonObject();
		body.addProperty("name", name);
		body.addProperty("creatorRsn", creatorRsn);

		return send(new Request.Builder()
			.url(url(baseUrl, "v1", "groups"))
			.post(RequestBody.create(JSON, gson.toJson(body))));
	}

	public Result<Snapshot> read(String baseUrl, String code)
	{
		return send(new Request.Builder()
			.url(url(baseUrl, "v1", "groups", code))
			.get());
	}

	public Result<Snapshot> join(String baseUrl, String code, String rsn)
	{
		JsonObject body = new JsonObject();
		body.addProperty("rsn", rsn);

		return send(new Request.Builder()
			.url(url(baseUrl, "v1", "groups", code, "join"))
			.post(RequestBody.create(JSON, gson.toJson(body))));
	}

	public Result<Snapshot> delete(String baseUrl, String code, String creatorToken)
	{
		return send(new Request.Builder()
			.url(url(baseUrl, "v1", "groups", code))
			.delete()
			.header("X-Creator-Token", creatorToken));
	}

	/**
	 * Sends drops. Each keeps its id, so a batch sent twice counts once.
	 */
	public Result<Snapshot> submit(String baseUrl, String code, String memberToken, Object drops)
	{
		JsonObject body = new JsonObject();
		body.add("drops", gson.toJsonTree(drops));

		return send(new Request.Builder()
			.url(url(baseUrl, "v1", "groups", code, "drops"))
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.header("X-Member-Token", memberToken));
	}

	/**
	 * Asks the service to read the group's linked Discord channel.
	 *
	 * @param dryRun report what would happen without doing it, which is what the plugin shows first
	 */
	public Result<Import> importFromDiscord(
		String baseUrl, String code, String creatorToken, boolean dryRun)
	{
		JsonObject body = new JsonObject();
		body.addProperty("dryRun", dryRun);

		Request request = new Request.Builder()
			.url(url(baseUrl, "v1", "groups", code, "import"))
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.header("X-Creator-Token", creatorToken)
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			ResponseBody responseBody = response.body();
			String text = responseBody == null ? "" : responseBody.string();

			if (!response.isSuccessful())
			{
				// A group with no bot in its server is not a failure, it is the step before this one, and
				// the answer carries what is needed to take it. Handed back as a result so the plugin can
				// offer the invite rather than print a message telling someone to go and find it.
				if (response.code() == 409)
				{
					Import needsBot = gson.fromJson(text, Import.class);
					if (needsBot != null && needsBot.isNeedsBot())
					{
						return Result.of(needsBot);
					}
				}

				return Result.failed(messageIn(text, "Could not import"));
			}

			return Result.of(gson.fromJson(text, Import.class));
		}
		catch (IOException e)
		{
			return Result.failed("Could not reach the server");
		}
		catch (JsonSyntaxException e)
		{
			return Result.failed("The server sent something unreadable");
		}
	}

	/** What an import found, or would find. */
	@lombok.Data
	public static class Import
	{
		/** Collection log messages seen in the channel. */
		private int found;

		/** Messages that were not collection log notifications at all. */
		private int skipped;

		/** Of those found, how many belong to somebody in this group. */
		private int matched;

		private int imported;

		/** Drops with no kill count recorded, which can be kept but never scored. */
		private int withoutKillCount;

		/** Names seen in the channel that are not in this group, and how many drops each had. */
		private java.util.Map<String, Integer> unmatched = new java.util.LinkedHashMap<>();

		/** There is no bot in the group's Discord server yet, so there is nothing to read. */
		private boolean needsBot;

		/** Where to send someone to put that right. */
		private String invite;

		/** What to type once the bot is in, with the group's own code already in it. */
		private String linkCommand;
	}

	/** Everyone in the group who has one item, luckiest first. The question this is all named after. */
	public Result<List<Holder>> whoSpoonedIt(String baseUrl, String code, String itemName)
	{
		Request request = new Request.Builder()
			.url(url(baseUrl, "v1", "groups", code, "items", itemName))
			.get()
			.build();

		return readList(request, "holders", new TypeToken<List<Holder>>()
		{
		}.getType(), "Could not look that item up");
	}

	/** Item names anyone in the group has, for the search box to offer. */
	public Result<List<Holder>> search(String baseUrl, String code, String query)
	{
		HttpUrl url = url(baseUrl, "v1", "groups", code, "search")
			.newBuilder()
			.addQueryParameter("q", query)
			.build();

		return readList(new Request.Builder().url(url).get().build(),
			"items", new TypeToken<List<Holder>>()
			{
			}.getType(), "Could not search");
	}

	private <T> Result<List<T>> readList(Request request, String field, Type type, String fallback)
	{
		try (Response response = httpClient.newCall(request).execute())
		{
			ResponseBody responseBody = response.body();
			String text = responseBody == null ? "" : responseBody.string();

			if (!response.isSuccessful())
			{
				String message = messageIn(text, fallback);
				return response.code() == 404 ? Result.gone(message) : Result.failed(message);
			}

			JsonObject root = gson.fromJson(text, JsonObject.class);
			List<T> parsed = root != null && root.has(field)
				? gson.fromJson(root.get(field), type)
				: new ArrayList<>();

			return Result.of(parsed == null ? new ArrayList<>() : parsed);
		}
		catch (IOException e)
		{
			return Result.failed("Could not reach the server");
		}
		catch (JsonSyntaxException e)
		{
			return Result.failed("The server sent something unreadable");
		}
	}

	private Result<Snapshot> send(Request.Builder builder)
	{
		try (Response response = httpClient.newCall(builder.build()).execute())
		{
			ResponseBody responseBody = response.body();
			String text = responseBody == null ? "" : responseBody.string();

			if (!response.isSuccessful())
			{
				String message = messageIn(text, "The server said no (" + response.code() + ")");
				return response.code() == 404 ? Result.gone(message) : Result.failed(message);
			}

			return Result.of(parse(text));
		}
		catch (IOException e)
		{
			log.debug("Request failed", e);
			return Result.failed("Could not reach the server");
		}
		catch (JsonSyntaxException e)
		{
			log.warn("Unreadable response", e);
			return Result.failed("The server sent something unreadable");
		}
	}

	private Snapshot parse(String text)
	{
		JsonObject root = gson.fromJson(text, JsonObject.class);
		if (root == null)
		{
			return new Snapshot(null, new ArrayList<>(), null, null);
		}

		Group group = root.has("group") && !root.get("group").isJsonNull()
			? gson.fromJson(root.get("group"), Group.class)
			: null;

		List<Standing> leaderboard = new ArrayList<>();
		if (root.has("leaderboard") && root.get("leaderboard").isJsonArray())
		{
			Type type = new TypeToken<List<Standing>>()
			{
			}.getType();

			List<Standing> parsed = gson.fromJson(root.get("leaderboard"), type);
			if (parsed != null)
			{
				leaderboard.addAll(parsed);
			}
		}

		// The code comes back at the top level on create, where the group does not carry it yet.
		if (group != null && (group.getCode() == null || group.getCode().isEmpty()) && root.has("code"))
		{
			group.setCode(root.get("code").getAsString());
		}

		return new Snapshot(
			group,
			leaderboard,
			stringOrNull(root, "creatorToken"),
			stringOrNull(root, "memberToken"));
	}

	/**
	 * The server's own wording where there is one. It says "No group with that code", which is more use
	 * to a player than anything this class could invent.
	 */
	private String messageIn(String text, String fallback)
	{
		try
		{
			JsonObject root = gson.fromJson(text, JsonObject.class);
			if (root != null && root.has("error"))
			{
				return root.get("error").getAsString();
			}
		}
		catch (JsonSyntaxException ignored)
		{
			// Fall through to the generic message.
		}

		return fallback;
	}

	private static String stringOrNull(JsonObject root, String key)
	{
		return root.has(key) && !root.get(key).isJsonNull() ? root.get(key).getAsString() : null;
	}

	private static HttpUrl url(String baseUrl, String... segments)
	{
		HttpUrl parsed = HttpUrl.parse(baseUrl.trim());
		if (parsed == null)
		{
			throw new IllegalArgumentException("The server address is not a valid URL: " + baseUrl);
		}

		HttpUrl.Builder builder = parsed.newBuilder();
		for (String segment : segments)
		{
			builder.addPathSegment(segment);
		}

		return builder.build();
	}
}
