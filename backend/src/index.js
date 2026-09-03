/**
 * Who Spooned It, the small service groups share.
 *
 * A RuneLite plugin only ever sees its own client, so comparing collection logs across a group needs
 * somewhere for everyone's drops to meet.
 *
 * One decision worth stating: how lucky a drop was is worked out here, from the kill count and the
 * rarity the plugin reports. The plugin says what dropped and on which kill; it does not get to say
 * how impressive that was. That does not make it cheat-proof, a modified client can still claim a
 * kill count it never reached, but it keeps an honest client's numbers right and stops the obvious.
 */

import { parseDinkMessages } from './dink.js';
import {
	readChannelChunk,
	WRITING,
	handleInteraction,
	inviteUrl,
	registerCommands,
	verifySignature
} from './discord.js';
import rates, { version as RATES } from './rates.js';

/** Codes people read aloud, so no O/0 or I/1. */
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const CODE_LENGTH = 6;

const TOKEN_BYTES = 24;

/** One request cannot carry more than this many drops. Keeps a bad client from flooding the table. */
const MAX_DROPS_PER_REQUEST = 200;

/** Fixed-point scales, so SQLite integers can hold these without float surprises. */
const RATE_SCALE = 100;
const SHARE_SCALE = 10000;

export default {
	async fetch(request, env)
	{
		try
		{
			return await route(request, env);
		}
		catch (error)
		{
			console.error(error);
			return json({ error: 'Something went wrong' }, 500);
		}
	}
};

async function route(request, env)
{
	const url = new URL(request.url);
	const path = url.pathname.replace(/\/+$/, '');

	if (request.method === 'OPTIONS')
	{
		return withCors(new Response(null, { status: 204 }));
	}

	// Discord posts here for every slash command, and to check the endpoint is real.
	if (path === '/discord/interactions' && request.method === 'POST')
	{
		return discordInteraction(request, env);
	}

	// Says whether the stored bot token still works, and nothing else about it. Here because a stale
	// token and a missing permission produce very similar symptoms and very different fixes, and
	// telling them apart by pressing a button in a game client is a slow way to find out.
	if (path === '/discord/health' && request.method === 'GET')
	{
		return withCors(await discordHealth(env));
	}

	// Run once after deploying, to tell Discord what the command looks like.
	if (path === '/discord/register' && request.method === 'POST')
	{
		return withCors(await registerSlashCommand(request, env));
	}

	if (path === '/v1/groups' && request.method === 'POST')
	{
		return withCors(await createGroup(request, env));
	}

	const byCode = path.match(/^\/v1\/groups\/([A-Za-z0-9]+)$/);
	if (byCode && request.method === 'GET')
	{
		return withCors(await readGroup(byCode[1].toUpperCase(), env));
	}

	if (byCode && request.method === 'DELETE')
	{
		return withCors(await deleteGroup(byCode[1].toUpperCase(), request, env));
	}

	const join = path.match(/^\/v1\/groups\/([A-Za-z0-9]+)\/join$/);
	if (join && request.method === 'POST')
	{
		return withCors(await joinGroup(join[1].toUpperCase(), request, env));
	}

	const drops = path.match(/^\/v1\/groups\/([A-Za-z0-9]+)\/drops$/);
	if (drops && request.method === 'POST')
	{
		return withCors(await submitDrops(drops[1].toUpperCase(), request, env));
	}

	const item = path.match(/^\/v1\/groups\/([A-Za-z0-9]+)\/items\/([^/]+)$/);
	if (item && request.method === 'GET')
	{
		return withCors(await whoSpoonedIt(item[1].toUpperCase(), decodeURIComponent(item[2]), env));
	}

	const importPath = path.match(/^\/v1\/groups\/([A-Za-z0-9]+)\/import$/);
	if (importPath && request.method === 'POST')
	{
		return withCors(await importFromDiscord(importPath[1].toUpperCase(), request, env));
	}

	const claims = path.match(/^\/v1\/groups\/([A-Za-z0-9]+)\/claims$/);
	if (claims && request.method === 'POST')
	{
		return withCors(await submitClaim(claims[1].toUpperCase(), request, env));
	}

	if (claims && request.method === 'GET')
	{
		return withCors(await listClaims(claims[1].toUpperCase(), request, env));
	}

	const vote = path.match(/^\/v1\/groups\/([A-Za-z0-9]+)\/claims\/([^/]+)\/vote$/);
	if (vote && request.method === 'POST')
	{
		return withCors(await voteOnClaim(
			vote[1].toUpperCase(), decodeURIComponent(vote[2]), request, env));
	}

	const memberDrops = path.match(/^\/v1\/groups\/([A-Za-z0-9]+)\/members\/([^/]+)\/drops$/);
	if (memberDrops && request.method === 'GET')
	{
		return withCors(await dropsFor(
			memberDrops[1].toUpperCase(),
			decodeURIComponent(memberDrops[2]),
			url.searchParams.get('sort'),
			env));
	}

	const search = path.match(/^\/v1\/groups\/([A-Za-z0-9]+)\/search$/);
	if (search && request.method === 'GET')
	{
		return withCors(await searchItems(search[1].toUpperCase(), url.searchParams.get('q'), env));
	}

	return withCors(json({ error: 'No such endpoint' }, 404));
}

async function createGroup(request, env)
{
	const body = await readJson(request);
	const name = String(body?.name ?? '').trim();
	const creatorRsn = String(body?.creatorRsn ?? '').trim();

	if (!name)
	{
		return json({ error: 'A name is required' }, 400);
	}

	if (!creatorRsn)
	{
		return json({ error: 'A creator name is required' }, 400);
	}

	const code = await unusedCode(env);
	const creatorToken = randomToken();
	const memberToken = randomToken();
	const now = Date.now();

	await env.DB.batch([
		env.DB.prepare(
			'INSERT INTO groups (code, name, creator_rsn, creator_token, created_at) VALUES (?, ?, ?, ?, ?)')
			.bind(code, name.slice(0, 60), creatorRsn.slice(0, 24), creatorToken, now),

		// Whoever makes a group is in it. Unlike running a competition for other people, comparing
		// collection logs is not something done on someone else's behalf, a group of one with its
		// maker outside it is nobody's idea of what Create means.
		env.DB.prepare(
			'INSERT INTO members (group_code, rsn, token, joined_at) VALUES (?, ?, ?, ?)')
			.bind(code, creatorRsn.slice(0, 24), memberToken, now)
	]);

	const group = await loadGroup(code, env);

	return json({
		code,
		creatorToken,
		memberToken,
		group: await publicGroup(group, env),
		leaderboard: await leaderboardFor(code, env)
	}, 201);
}

async function readGroup(code, env)
{
	const group = await loadGroup(code, env);
	if (!group)
	{
		return json({ error: 'No group with that code' }, 404);
	}

	return json({
		group: await publicGroup(group, env),
		leaderboard: await leaderboardFor(code, env)
	});
}

async function joinGroup(code, request, env)
{
	const group = await loadGroup(code, env);
	if (!group)
	{
		return json({ error: 'No group with that code' }, 404);
	}

	const body = await readJson(request);
	const rsn = String(body?.rsn ?? '').trim();
	if (!rsn)
	{
		return json({ error: 'A name is required to join' }, 400);
	}

	const existing = await env.DB.prepare(
		'SELECT token FROM members WHERE group_code = ? AND rsn = ?')
		.bind(code, rsn)
		.first();

	// Rejoining is not an error. People reinstall, hop accounts, and join twice by accident.
	const token = existing ? existing.token : randomToken();
	if (!existing)
	{
		await env.DB.prepare(
			'INSERT INTO members (group_code, rsn, token, joined_at) VALUES (?, ?, ?, ?)')
			.bind(code, rsn.slice(0, 24), token, Date.now())
			.run();
	}

	return json({
		memberToken: token,
		group: await publicGroup(group, env),
		leaderboard: await leaderboardFor(code, env)
	});
}

async function deleteGroup(code, request, env)
{
	const group = await loadGroup(code, env);
	if (!group)
	{
		return json({ error: 'No group with that code' }, 404);
	}

	if (request.headers.get('X-Creator-Token') !== group.creator_token)
	{
		return json({ error: 'Only whoever made this group can delete it' }, 403);
	}

	await env.DB.prepare('DELETE FROM groups WHERE code = ?').bind(code).run();
	return json({ deleted: true });
}

/**
 * Takes in drops and scores them.
 *
 * Idempotent on the drop's id, so a resend after a timeout counts once and re-importing the same
 * Discord export changes nothing.
 */
async function submitDrops(code, request, env)
{
	const member = await memberFor(code, request, env);
	if (!member)
	{
		return json({ error: 'Join the group before sending anything' }, 403);
	}

	const body = await readJson(request);
	const submitted = Array.isArray(body?.drops) ? body.drops : [];
	if (submitted.length > MAX_DROPS_PER_REQUEST)
	{
		return json({ error: 'No more than ' + MAX_DROPS_PER_REQUEST + ' drops at a time' }, 400);
	}

	const now = Date.now();
	const statements = [];

	for (const drop of submitted)
	{
		if (typeof drop?.id !== 'string' || !drop.id)
		{
			continue;
		}

		const itemName = String(drop.itemName ?? '').trim();
		if (!itemName)
		{
			continue;
		}

		const killCount = Number.isFinite(drop.killCount) && drop.killCount > 0
			? Math.trunc(drop.killCount)
			: null;

		const denominator = Number.isFinite(drop.denominator) && drop.denominator > 0
			? drop.denominator
			: null;

		statements.push(env.DB.prepare(
			'INSERT OR IGNORE INTO drops' +
			' (id, group_code, rsn, item_name, item_id, source, kill_count, denominator, share,' +
			'  obtained_at, recorded_at, claimed)' +
			' VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)')
			.bind(
				drop.id,
				code,
				member.rsn,
				itemName.slice(0, 120),
				Number.isFinite(drop.itemId) ? Math.trunc(drop.itemId) : -1,
				drop.source ? String(drop.source).slice(0, 80) : null,
				killCount,
				denominator === null ? null : Math.round(denominator * RATE_SCALE),
				shareOf(denominator, killCount),
				Number(drop.obtainedAt) || now,
				now,
				drop.claimed ? 1 : 0));
	}

	if (statements.length > 0)
	{
		await env.DB.batch(statements);
		await refreshTotals(code, member.rsn, env);
	}

	return json({
		group: await publicGroup(await loadGroup(code, env), env),
		leaderboard: await leaderboardFor(code, env)
	});
}

/**
 * The share of players who would already hold an item by a given kill count, as an integer.
 *
 * Null when it cannot be worked out, which is not the same as unlucky and must never be counted as
 * though it were. A clue reward has no monster and no kill count; scoring it zero would put it at the
 * top of a leaderboard it has no business being on.
 */
function shareOf(denominator, killCount)
{
	if (!denominator || !killCount || denominator <= 0 || killCount <= 0)
	{
		return null;
	}

	// 1 - (1-p)^n, through logs so the long odds people actually care about keep their precision.
	const share = -Math.expm1(killCount * Math.log1p(-1 / denominator));
	return Math.round(share * SHARE_SCALE);
}

/**
 * Rebuilds one member's totals from their drops, so the leaderboard can never drift from them.
 */
async function refreshTotals(code, rsn, env)
{
	const half = SHARE_SCALE / 2;

	const totals = await env.DB.prepare(
		'SELECT COUNT(*) AS scored,' +
		' COALESCE(SUM(CASE WHEN share < ? THEN 1 ELSE 0 END), 0) AS spoons,' +
		' COALESCE(AVG(share), ?) AS avgShare' +
		' FROM drops WHERE group_code = ? AND rsn = ? AND share IS NOT NULL')
		.bind(half, half, code, rsn)
		.first();

	await env.DB.prepare(
		'UPDATE members SET spoons = ?, scored = ?, avg_share = ? WHERE group_code = ? AND rsn = ?')
		.bind(
			totals?.spoons ?? 0,
			totals?.scored ?? 0,
			Math.round(totals?.avgShare ?? half),
			code,
			rsn)
		.run();
}

/**
 * The leaderboard: who has been spooned the most.
 *
 * Ranked on the count rather than the average, because that is the thing people argue about, being
 * handed four pets early beats one absurd drop and an otherwise ordinary log. The average rides
 * alongside as "how spooned is this account", where lower is luckier.
 */
async function leaderboardFor(code, env)
{
	const rows = await env.DB.prepare(
		'SELECT rsn, spoons, scored, avg_share AS avgShare FROM members' +
		' WHERE group_code = ? ORDER BY spoons DESC, avg_share ASC, rsn ASC')
		.bind(code)
		.all();

	return (rows.results ?? []).map((row, index) => ({
		rsn: row.rsn,
		spoons: row.spoons,
		scored: row.scored,
		place: index + 1,
		avgShare: row.avgShare / SHARE_SCALE
	}));
}

/**
 * Who in this group has one particular item, luckiest first.
 *
 * The question the whole plugin is named after.
 */
async function whoSpoonedIt(code, itemName, env)
{
	const group = await loadGroup(code, env);
	if (!group)
	{
		return json({ error: 'No group with that code' }, 404);
	}

	const rows = await env.DB.prepare(
		'SELECT rsn, item_name AS itemName, item_id AS itemId, source, kill_count AS killCount,' +
		' denominator, share, obtained_at AS obtainedAt, claimed FROM drops' +
		' WHERE group_code = ? AND item_name = ? COLLATE NOCASE' +
		// Unscored holders go last rather than first: having it with no kill count recorded is not the
		// same as having got it on kill one.
		' ORDER BY share IS NULL, share ASC, kill_count ASC')
		.bind(code, itemName)
		.all();

	return json({
		itemName,
		holders: (rows.results ?? []).map((row, index) => ({
			rsn: row.rsn,
			itemName: row.itemName,
			itemId: row.itemId,
			source: row.source,
			killCount: row.killCount,
			place: index + 1,
			denominator: row.denominator === null ? null : row.denominator / RATE_SCALE,
			share: row.share === null ? null : row.share / SHARE_SCALE,
			claimed: row.claimed === 1,
			obtainedAt: row.obtainedAt
		}))
	});
}

/** Names of items anyone in the group has, for the search box to offer. */
async function searchItems(code, query, env)
{
	const text = String(query ?? '').trim();
	if (text.length < 2)
	{
		return json({ items: [] });
	}

	const rows = await env.DB.prepare(
		'SELECT item_name AS itemName, MAX(item_id) AS itemId, COUNT(*) AS holders FROM drops' +
		' WHERE group_code = ? AND item_name LIKE ? COLLATE NOCASE' +
		' GROUP BY item_name COLLATE NOCASE ORDER BY item_name LIMIT 25')
		.bind(code, '%' + text + '%')
		.all();

	return json({ items: rows.results ?? [] });
}

/**
 * Brings in a group's history from its Discord channel.
 *
 * Only whoever made the group may do this, because one person's import becomes everyone's history.
 *
 * Names that are not in the group are ignored rather than added. A clan channel carries Dink messages
 * from people who are not in this particular group, and quietly enrolling them would be a surprise.
 * They are reported back so the plugin can say who was left out, since the commonest reason for a
 * name not matching is that somebody has changed theirs.
 */
async function importFromDiscord(code, request, env)
{
	const group = await loadGroup(code, env);
	if (!group)
	{
		return json({ error: 'No group with that code' }, 404);
	}

	if (request.headers.get('X-Creator-Token') !== group.creator_token)
	{
		return json({ error: 'Only whoever made this group can import its history' }, 403);
	}

	const body = await readJson(request);

	// Two ways in, one code path. Messages handed over directly are how a file export works; asking
	// for the linked channel is how the bot works.
	let messages = body?.messages;

	// An export arrives whole and in whatever order the tool wrote it, so it says nothing about how
	// far through the channel anybody is and must not be allowed to move the place.
	const fromExport = Array.isArray(messages);

	// A channel is read a chunk at a time, so who holds the place in it has to be settled. A plugin
	// that knows about chunks sends its own cursor and drives the loop itself; one that does not gets
	// the cursor kept here, and moves on a chunk each time somebody presses the button. Either way the
	// channel is read the same way, and only the pressing differs.
	const paged = body?.paged === true;
	const startingOut = paged ? !body?.cursor : !group.discord_cursor;

	// A group brought in against older rate data has drops in it that could not be scored then and can
	// be now, and nothing would ever go back for them: a finished import leaves a mark saying how far
	// it read, and later ones start from there. So when the data has changed underneath it, the mark is
	// ignored and the channel is read again, once. The sweep stamps the new version when it finishes,
	// which is what stops it happening every time.
	const rescoring = group.discord_scored_with !== RATES;

	// Where this read stopped, and whether any channel is left behind it. Handed back so a plugin that
	// can carry on knows to, and a file export, which arrives whole, is already finished.
	let chunk = { before: null, done: true };

	if (!Array.isArray(messages))
	{
		if (!group.discord_channel_id)
		{
			// Answered with the way forward rather than only the problem: the plugin turns this into a
			// button that opens Discord, instead of a message telling somebody to go and find it.
			return json({
				error: 'The bot is not in your Discord server yet.',
				needsBot: true,
				invite: env.DISCORD_APPLICATION_ID ? inviteUrl(env.DISCORD_APPLICATION_ID) : null,
				linkCommand: '/spoons link ' + code
			}, 409);
		}

		if (!env.DISCORD_BOT_TOKEN)
		{
			return json({ error: 'This service has no Discord bot configured' }, 501);
		}

		try
		{
			chunk = await readChannelChunk(group.discord_channel_id, env.DISCORD_BOT_TOKEN, {
				before: paged ? (body?.cursor ?? null) : (group.discord_cursor ?? null),

				// A look spends the whole answer on reading. Bringing it in has the writing to pay for
				// out of the same ten seconds, so it does not read as far in one go.
				...(body?.dryRun ? {} : WRITING),

				// A finished sweep leaves a high-water mark behind it, so bringing in a clan's whole
				// history once does not mean reading all of it again to pick up this week's drops.
				notBefore: rescoring ? 0 : (group.discord_read_through ?? 0)
			});

			messages = chunk.messages;
		}
		catch (error)
		{
			console.error(error);
			return json({
				error: whyDiscordRefused(error),
				channelId: group.discord_channel_id
			}, 502);
		}
	}

	const { drops, details, skipped, names } = parseDinkMessages(messages);

	// Before anything is matched or scored, since a drop that borrows its source from the loot beside
	// it can then be scored like any other.
	fillFromLoot(drops, details);

	const rows = await env.DB.prepare('SELECT rsn FROM members WHERE group_code = ?')
		.bind(code)
		.all();

	// Matched without regard to case, because a name typed into a plugin and a name written by Dink
	// are the same name however it was capitalised.
	const members = new Map();
	for (const row of rows.results ?? [])
	{
		members.set(row.rsn.toLowerCase(), row.rsn);
	}

	const matched = [];
	const unmatched = {};
	let withoutKillCount = 0;
	let withoutRate = 0;

	for (const drop of drops)
	{
		const member = members.get(drop.rsn.toLowerCase());
		if (!member)
		{
			unmatched[drop.rsn] = (unmatched[drop.rsn] ?? 0) + 1;
			continue;
		}

		// Dink's own figure first, where it gave one, so a group that has been reading its numbers for
		// months is not told something different about a drop it already discussed.
		const denominator = drop.denominator ?? rateFor(drop.source, drop.itemName);

		if (!drop.killCount)
		{
			withoutKillCount++;
		}
		else if (!denominator)
		{
			// Counted apart from the above, because they are different holes: one is a drop nothing
			// knows the kill count of, the other a drop nothing knows the odds of.
			withoutRate++;
		}

		matched.push({ ...drop, rsn: member, denominator });
	}

	const summary = {
		found: drops.length,
		skipped,
		matched: matched.length,
		withoutKillCount,
		withoutRate,
		names,
		unmatched
	};

	// Asked what would happen, rather than told to do it. The plugin shows this first, because an
	// import that silently drops a third of a channel is one nobody would trust afterwards.
	if (body?.dryRun)
	{
		// A dry run leaves the place in the channel where it was: what it has just described is
		// what the next press brings in. The exception is a stretch holding nothing for this
		// group, which is stepped over rather than offered again, since there is nothing in it
		// to keep and a plugin that presses once per chunk would otherwise never get past it.
		//
		// The end of the channel counts as such a stretch, and leaving it out was a way to get
		// stuck: the plugin says "nothing to bring in" and never commits, so nothing ever cleared
		// the place, and every press after that read from the oldest end and found nothing there.
		if (!fromExport && matched.length === 0)
		{
			await rememberPlace(code, { paged, startingOut, chunk, messages, dryRun: true }, env);
		}

		return json({ ...summary, imported: 0, dryRun: true, cursor: chunk.before, done: chunk.done });
	}

	const now = Date.now();
	const touched = new Set();

	for (let from = 0; from < matched.length; from += 100)
	{
		const batch = matched.slice(from, from + 100);

		await env.DB.batch(batch.map(drop =>
		{
			touched.add(drop.rsn);

			return env.DB.prepare(
				'INSERT INTO drops' +
				' (id, group_code, rsn, item_name, item_id, source, kill_count, denominator, share,' +
				'  obtained_at, recorded_at, claimed)' +
				' VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)' +

				// A second import used to change nothing at all, which was right while the only thing
				// it could have changed was a duplicate. It is wrong once the service learns to score
				// something it could not score before: those drops are already on the board, unscored,
				// and nothing would ever go back for them.
				//
				// So a gap is filled and a value is never overwritten. What is already known came from
				// the drop itself or from Dink, and is not for a later pass to second-guess.
				' ON CONFLICT(id) DO UPDATE SET' +
				'  source = COALESCE(drops.source, excluded.source),' +
				'  kill_count = COALESCE(drops.kill_count, excluded.kill_count),' +
				'  denominator = COALESCE(drops.denominator, excluded.denominator),' +
				'  share = COALESCE(drops.share, excluded.share)')
				.bind(
					drop.id,
					code,
					drop.rsn,
					String(drop.itemName).slice(0, 120),
					-1,
					drop.source ? String(drop.source).slice(0, 80) : null,
					drop.killCount ?? null,
					drop.denominator === null ? null : Math.round(drop.denominator * RATE_SCALE),
					shareOf(drop.denominator, drop.killCount),
					drop.obtainedAt,
					now);
		}));
	}

	await refreshTotalsFor(code, [...touched], env);

	if (!fromExport)
	{
		await rememberPlace(code, { paged, startingOut, chunk, messages }, env);
	}

	return json({
		...summary,
		imported: matched.length,
		cursor: chunk.before,
		done: chunk.done,
		leaderboard: await leaderboardFor(code, env)
	});
}

/**
 * One spelling for a name, so one written in a Discord message finds one written in a drop table.
 *
 * The same function built the table, which is the only reason this works: "Hydra's claw", "Torva
 * full helm (damaged)" and "Vet'ion" are punctuated differently by everything that writes them.
 */
const spelled = text => String(text ?? '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();

/**
 * The tier a casket belongs to, from whatever the game called the thing that dropped it.
 *
 * Loose on purpose, the same way the plugin is: one tier arrives as "Clue Scroll (Hard)", "Reward
 * Casket (Hard)" and "hard Treasure Trails" depending which part of the game is speaking.
 */
export function clueTier(source)
{
	const text = spelled(source);

	for (const tier of ['beginner', 'easy', 'medium', 'hard', 'elite', 'master'])
	{
		if (text.includes(tier))
		{
			return tier;
		}
	}

	return null;
}

/**
 * How rare a thing is, worked out here when the message that carried it did not say.
 *
 * Dink states a rarity in some of its notifications and not in others, and the ones without were kept
 * and never scored, which is a drop on the board with nothing to judge it by. The plugin has never had
 * this problem: it holds the drop data and can ask the game what an item is called. This is the same
 * data, keyed by name, so the service can do it too.
 *
 * Still nothing rather than a guess when the source is unknown: a monster that is not in the data is
 * not a monster with an average drop rate.
 */
export function rateFor(source, itemName)
{
	if (!source || !itemName)
	{
		return null;
	}

	const item = spelled(itemName);

	for (const at of filedUnder(source))
	{
		const found = rates[at]?.[item];
		if (found)
		{
			return found;
		}
	}

	return null;
}

/**
 * The names one source might be filed under, likeliest first.
 *
 * Dink writes whatever the game called the thing that gave the item, which for an event is often a
 * container with the event in brackets: "Reward pool (Tempoross)". The table knows the pool and it
 * knows Tempoross, and neither of them is spelled like the whole string, so all three are tried.
 *
 * The clue tier is offered first and does not stop the rest being tried, because a tier is recognised
 * by a word appearing anywhere in the source, and a boss whose name happens to contain one is a boss
 * rather than a casket.
 */
export function* filedUnder(source)
{
	const tier = clueTier(source);
	if (tier)
	{
		yield 'clue ' + tier;
	}

	yield spelled(source);

	const brackets = /^(.*?)\s*\(([^)]+)\)\s*$/.exec(String(source));
	if (brackets)
	{
		yield spelled(brackets[2]);
		yield spelled(brackets[1]);
	}
}

/** How far apart a loot notification and a collection log message may be and still be one drop. */
const SAME_DROP_MILLIS = 5 * 60 * 1000;

/**
 * Fills in what a collection log message did not say, from the loot notification beside it.
 *
 * Dink writes the source and the count into a collection log message only when it knows the count,
 * and leaves both out together otherwise, which is why a drop can arrive with no details at all. The
 * loot notification fired seconds earlier usually has all three.
 *
 * Matched on who, what, and when. The same item to the same player within a few minutes is the same
 * drop; the same item to the same player a year later is a different one, and its details are not
 * this drop's to borrow.
 */
export function fillFromLoot(drops, details)
{
	if (details.length === 0)
	{
		return;
	}

	for (const drop of drops)
	{
		if (drop.source && drop.killCount && drop.denominator)
		{
			continue;
		}

		let best = null;
		let closest = SAME_DROP_MILLIS;

		for (const detail of details)
		{
			if (detail.rsn.toLowerCase() !== drop.rsn.toLowerCase())
			{
				continue;
			}

			if (!detail.items.some(item => item.toLowerCase() === drop.itemName.toLowerCase()))
			{
				continue;
			}

			const apart = Math.abs(detail.at - drop.obtainedAt);
			if (apart <= closest)
			{
				best = detail;
				closest = apart;
			}
		}

		if (!best)
		{
			continue;
		}

		drop.source = drop.source ?? best.source;
		drop.killCount = drop.killCount ?? best.killCount;
		drop.denominator = drop.denominator ?? best.denominator;
	}
}

/**
 * Rebuilds the running totals of everyone an import touched, in one statement.
 *
 * One member at a time is right for a drop landing on its own and wrong here. An import touches the
 * whole group at once, and a round trip each is both slow enough to lose the plugin's patience partway
 * through and enough subrequests to run a Worker out of its allowance.
 *
 * The figures are the ones refreshTotals works out, said in SQL: how many scored drops, how many of
 * them were luckier than the middle, and the mean share across them.
 */
async function refreshTotalsFor(code, names, env)
{
	if (names.length === 0)
	{
		return;
	}

	const half = SHARE_SCALE / 2;
	const theirs = 'FROM drops WHERE group_code = members.group_code AND rsn = members.rsn'
		+ ' AND share IS NOT NULL';

	await env.DB.prepare(
		'UPDATE members SET' +
		' spoons = (SELECT COALESCE(SUM(CASE WHEN share < ? THEN 1 ELSE 0 END), 0) ' + theirs + '),' +
		' scored = (SELECT COUNT(*) ' + theirs + '),' +
		' avg_share = (SELECT CAST(ROUND(COALESCE(AVG(share), ?)) AS INTEGER) ' + theirs + ')' +
		' WHERE group_code = ? AND rsn IN (' + names.map(() => '?').join(', ') + ')')
		.bind(half, half, code, ...names)
		.run();
}

/**
 * Keeps the place in a channel that is being read a chunk at a time.
 *
 * The cursor is only for a plugin that does not hold its own: it presses the button again and carries
 * on from here. What is kept for every plugin is the high-water mark, the newest message a finished
 * sweep saw, so that the next sweep stops as soon as it reaches ground already covered.
 *
 * That mark has to be taken at the start of a sweep, while the newest message is still in front of
 * us. By the end we are years down the channel, where the newest thing in the last chunk read is the
 * oldest thing in the channel.
 */
export async function rememberPlace(code, { paged, startingOut, chunk, messages, dryRun }, env)
{
	const newest = startingOut && messages.length > 0
		? Date.parse(messages[0].timestamp ?? '') || null
		: null;

	// A look that reaches the end has finished nothing. It clears the place so the next press starts
	// afresh instead of reading the oldest end for ever, and leaves every mark of a completed import
	// alone: saying a channel has been read, and scored against this data, is the commit's to say. A
	// few presses answered with No would otherwise quietly use up a re-read that never happened.
	if (chunk.done && dryRun)
	{
		await env.DB.prepare(
			'UPDATE groups SET discord_cursor = NULL, discord_sweep_newest = NULL WHERE code = ?')
			.bind(code)
			.run();

		return;
	}

	if (chunk.done)
	{
		await env.DB.prepare(
			'UPDATE groups SET discord_cursor = NULL, discord_sweep_newest = NULL,' +
			' discord_read_through = COALESCE(?, discord_sweep_newest, discord_read_through),' +

			// Stamped only on a finished sweep. Stamping it earlier would stop a sweep that gave out
			// halfway from ever reading the half it never got to.
			' discord_scored_with = ?' +
			' WHERE code = ?')
			.bind(newest, RATES, code)
			.run();

		return;
	}

	// A paged plugin holds its own cursor, and storing a second one here would leave two places in
	// the channel disagreeing about where the reading had got to.
	await env.DB.prepare(
		'UPDATE groups SET discord_cursor = ?,' +
		' discord_sweep_newest = COALESCE(?, discord_sweep_newest) WHERE code = ?')
		.bind(paged ? null : chunk.before, newest, code)
		.run();
}

/**
 * Which of Discord's refusals this is, said in the words that fit it.
 *
 * They call for opposite fixes and only one of them is about the channel. A rejected token is the
 * service's own, which nobody but whoever deployed it can put right, and telling them the channel
 * could not be read sends them off inspecting permissions that were never the problem.
 */
function whyDiscordRefused(error)
{
	switch (error?.status)
	{
		case 401:
			return 'This service\'s Discord bot token has been rejected, so nothing can be read from '
				+ 'Discord at all. Whoever runs the service needs to set a new one. Nothing about your '
				+ 'group or your channel is wrong.';

		case 403:
			return 'The bot cannot see that channel. It needs View Channel and Read Message History in '
				+ 'it.';

		case 404:
			return 'That channel is gone. Run /spoons link again in the channel you want read.';

		default:
			return 'Could not read that channel. ' + (error?.message ?? '');
	}
}

/**
 * A slash command, or Discord checking the endpoint answers.
 *
 * The signature is checked first and always. Discord refuses to register an endpoint that does not
 * reject a bad one, and without it anybody could post an interaction claiming to be anybody.
 */
async function discordInteraction(request, env)
{
	const body = await request.text();

	if (!await verifySignature(request, body, env.DISCORD_PUBLIC_KEY))
	{
		return new Response('bad signature', { status: 401 });
	}

	let interaction;
	try
	{
		interaction = JSON.parse(body);
	}
	catch (error)
	{
		return new Response('bad body', { status: 400 });
	}

	return json(await handleInteraction(interaction, env));
}

/**
 * Whether the bot can still talk to Discord.
 *
 * Reveals only the bot's own name, which is public to anyone in a server it is in. The token itself
 * is never echoed, and nothing here says anything about any group.
 */
async function discordHealth(env)
{
	if (!env.DISCORD_BOT_TOKEN)
	{
		return json({ ok: false, reason: 'No bot token is set' });
	}

	try
	{
		const response = await fetch('https://discord.com/api/v10/users/@me', {
			headers: { authorization: `Bot ${env.DISCORD_BOT_TOKEN}` }
		});

		if (!response.ok)
		{
			return json({
				ok: false,
				status: response.status,
				reason: response.status === 401
					? 'Discord rejected the stored token. It has probably been reset since it was saved.'
					: 'Discord said ' + response.status
			});
		}

		const bot = await response.json();
		return json({ ok: true, bot: bot.username, id: bot.id });
	}
	catch (error)
	{
		return json({ ok: false, reason: 'Could not reach Discord' });
	}
}

async function registerSlashCommand(request, env)
{
	// Guarded by the bot token itself, since there is nobody else this could belong to.
	if (request.headers.get('X-Bot-Token') !== env.DISCORD_BOT_TOKEN)
	{
		return json({ error: 'No' }, 403);
	}

	await registerCommands(env.DISCORD_APPLICATION_ID, env.DISCORD_BOT_TOKEN);
	return json({ registered: true, invite: inviteUrl(env.DISCORD_APPLICATION_ID) });
}

/**
 * Puts a drop to the group that the plugin never saw.
 *
 * Everything from before a group installed this is in that position: real, unrecorded, and worth
 * having on the board. Taking somebody's word for it would make the leaderboard worthless, and
 * refusing it outright would throw away most of everyone's collection log. So it goes to the people
 * who would know.
 */
async function submitClaim(code, request, env)
{
	const member = await memberFor(code, request, env);
	if (!member)
	{
		return json({ error: 'Join the group before claiming anything' }, 403);
	}

	const body = await readJson(request);
	const itemName = String(body?.itemName ?? '').trim();
	if (!itemName)
	{
		return json({ error: 'Which item?' }, 400);
	}

	const already = await env.DB.prepare(
		'SELECT id FROM drops WHERE group_code = ? AND rsn = ? AND item_name = ? COLLATE NOCASE')
		.bind(code, member.rsn, itemName)
		.first();

	if (already)
	{
		return json({ error: 'That is already on the board for you' }, 409);
	}

	const waiting = await env.DB.prepare(
		"SELECT id FROM claims WHERE group_code = ? AND rsn = ? AND item_name = ? COLLATE NOCASE"
		+ " AND status = 'pending'")
		.bind(code, member.rsn, itemName)
		.first();

	if (waiting)
	{
		return json({ error: 'You have claimed that already, and it is still being voted on' }, 409);
	}

	const killCount = Number.isFinite(body?.killCount) && body.killCount > 0
		? Math.trunc(body.killCount)
		: null;

	const denominator = Number.isFinite(body?.denominator) && body.denominator > 0
		? body.denominator
		: null;

	await env.DB.prepare(
		'INSERT INTO claims'
		+ ' (id, group_code, rsn, item_name, item_id, source, kill_count, denominator, evidence,'
		+ '  note, created_at)'
		+ ' VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)')
		.bind(
			randomToken().slice(0, 16),
			code,
			member.rsn,
			itemName.slice(0, 120),
			Number.isFinite(body?.itemId) ? Math.trunc(body.itemId) : -1,
			body?.source ? String(body.source).slice(0, 80) : null,
			killCount,
			denominator === null ? null : Math.round(denominator * RATE_SCALE),
			body?.evidence ? String(body.evidence).slice(0, 500) : null,
			body?.note ? String(body.note).slice(0, 300) : null,
			Date.now())
		.run();

	return json({ claims: await claimsFor(code, member.rsn, env) }, 201);
}

/** Everything still being voted on, and how this member has voted on each. */
async function listClaims(code, request, env)
{
	const member = await memberFor(code, request, env);
	if (!member)
	{
		return json({ error: 'Join the group first' }, 403);
	}

	return json({ claims: await claimsFor(code, member.rsn, env) });
}

async function claimsFor(code, rsn, env)
{
	const rows = await env.DB.prepare(
		'SELECT c.id, c.rsn, c.item_name AS itemName, c.item_id AS itemId, c.source,'
		+ ' c.kill_count AS killCount, c.denominator, c.evidence, c.note,'
		+ ' c.created_at AS createdAt,'
		+ ' COALESCE(SUM(CASE WHEN v.approve = 1 THEN 1 ELSE 0 END), 0) AS approvals,'
		+ ' COALESCE(SUM(CASE WHEN v.approve = 0 THEN 1 ELSE 0 END), 0) AS rejections,'
		+ ' MAX(CASE WHEN v.rsn = ? THEN v.approve ELSE NULL END) AS yourVote'
		+ ' FROM claims c LEFT JOIN votes v ON v.claim_id = c.id'
		+ " WHERE c.group_code = ? AND c.status = 'pending'"
		+ ' GROUP BY c.id ORDER BY c.created_at')
		.bind(rsn, code)
		.all();

	const needed = await votesNeeded(code, env);

	return (rows.results ?? []).map(row => ({
		id: row.id,
		rsn: row.rsn,
		itemName: row.itemName,
		itemId: row.itemId,
		source: row.source,
		killCount: row.killCount,
		denominator: row.denominator === null ? null : row.denominator / RATE_SCALE,
		evidence: row.evidence,
		note: row.note,
		createdAt: row.createdAt,
		approvals: row.approvals,
		rejections: row.rejections,
		yours: row.rsn === rsn,
		yourVote: row.yourVote === null ? null : row.yourVote === 1,
		needed
	}));
}

/**
 * How many yes votes carries a claim: more than half of everybody else.
 *
 * The claimant is left out of their own count, which is the whole point of putting it to the group.
 * A group of one has nobody to ask, so nothing can be carried until somebody joins, which is the
 * honest answer rather than waving it through.
 */
async function votesNeeded(code, env)
{
	const row = await env.DB.prepare('SELECT COUNT(*) AS members FROM members WHERE group_code = ?')
		.bind(code)
		.first();

	const others = Math.max(0, (row?.members ?? 1) - 1);
	return Math.floor(others / 2) + 1;
}

/**
 * A yes or a no from somebody who is not the claimant.
 *
 * Settled the moment it can be. A claim with the votes should not wait on stragglers, and one that
 * can no longer reach the threshold should not sit there pretending it might.
 */
async function voteOnClaim(code, claimId, request, env)
{
	const member = await memberFor(code, request, env);
	if (!member)
	{
		return json({ error: 'Join the group before voting' }, 403);
	}

	const claim = await env.DB.prepare(
		"SELECT * FROM claims WHERE id = ? AND group_code = ? AND status = 'pending'")
		.bind(claimId, code)
		.first();

	if (!claim)
	{
		return json({ error: 'No claim waiting with that id' }, 404);
	}

	if (claim.rsn === member.rsn)
	{
		return json({ error: 'You cannot vote on your own claim' }, 403);
	}

	const body = await readJson(request);

	// Changing your mind replaces your vote rather than adding a second voice.
	await env.DB.prepare(
		'INSERT OR REPLACE INTO votes (claim_id, rsn, approve, voted_at) VALUES (?, ?, ?, ?)')
		.bind(claimId, member.rsn, body?.approve ? 1 : 0, Date.now())
		.run();

	const tally = await env.DB.prepare(
		'SELECT COALESCE(SUM(CASE WHEN approve = 1 THEN 1 ELSE 0 END), 0) AS approvals,'
		+ ' COALESCE(SUM(CASE WHEN approve = 0 THEN 1 ELSE 0 END), 0) AS rejections'
		+ ' FROM votes WHERE claim_id = ?')
		.bind(claimId)
		.first();

	const needed = await votesNeeded(code, env);

	const memberRow = await env.DB.prepare(
		'SELECT COUNT(*) AS members FROM members WHERE group_code = ?')
		.bind(code)
		.first();

	const others = Math.max(0, (memberRow?.members ?? 1) - 1);

	if ((tally?.approvals ?? 0) >= needed)
	{
		await acceptClaim(claim, env);
		return json({ settled: 'accepted', leaderboard: await leaderboardFor(code, env) });
	}

	// Once enough people have said no, the yeses still to come cannot reach the threshold.
	if (others - (tally?.rejections ?? 0) < needed)
	{
		await env.DB.prepare(
			"UPDATE claims SET status = 'rejected', settled_at = ? WHERE id = ?")
			.bind(Date.now(), claimId)
			.run();

		return json({ settled: 'rejected' });
	}

	return json({ settled: null, claims: await claimsFor(code, member.rsn, env) });
}

/** Turns a carried claim into an ordinary drop, marked as claimed for ever after. */
async function acceptClaim(claim, env)
{
	const now = Date.now();
	const denominator = claim.denominator === null ? null : claim.denominator / RATE_SCALE;

	await env.DB.batch([
		env.DB.prepare(
			'INSERT OR IGNORE INTO drops'
			+ ' (id, group_code, rsn, item_name, item_id, source, kill_count, denominator, share,'
			+ '  obtained_at, recorded_at, claimed)'
			+ ' VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)')
			.bind(
				'claim-' + claim.id,
				claim.group_code,
				claim.rsn,
				claim.item_name,
				claim.item_id,
				claim.source,
				claim.kill_count,
				claim.denominator,
				shareOf(denominator, claim.kill_count),
				claim.created_at,
				now),

		env.DB.prepare("UPDATE claims SET status = 'accepted', settled_at = ? WHERE id = ?")
			.bind(now, claim.id)
	]);

	await refreshTotals(claim.group_code, claim.rsn, env);
}

/**
 * One member's drops, for looking at somebody's luck on its own.
 *
 * Newest first by default, because the commonest question about a person is what they have just had.
 * Sorted by luck when asked, which is the other question: what is the best thing they have ever done.
 */
async function dropsFor(code, rsn, sort, env)
{
	const group = await loadGroup(code, env);
	if (!group)
	{
		return json({ error: 'No group with that code' }, 404);
	}

	const member = await env.DB.prepare(
		'SELECT rsn, spoons, scored, avg_share AS avgShare FROM members'
		+ ' WHERE group_code = ? AND rsn = ? COLLATE NOCASE')
		.bind(code, rsn)
		.first();

	if (!member)
	{
		return json({ error: 'Nobody by that name is in this group' }, 404);
	}

	// Unscored drops go last whichever way it is sorted. Having something with no kill count recorded
	// is not a stroke of luck and must not be presented among them.
	const order = sort === 'luck'
		? 'share IS NULL, share ASC, obtained_at DESC'
		: 'obtained_at DESC';

	const rows = await env.DB.prepare(
		'SELECT item_name AS itemName, item_id AS itemId, source, kill_count AS killCount,'
		+ ' denominator, share, obtained_at AS obtainedAt, claimed FROM drops'
		+ ' WHERE group_code = ? AND rsn = ? COLLATE NOCASE'
		+ ' ORDER BY ' + order + ' LIMIT 200')
		.bind(code, member.rsn)
		.all();

	return json({
		rsn: member.rsn,
		spoons: member.spoons,
		scored: member.scored,
		avgShare: member.avgShare / SHARE_SCALE,
		sort: sort === 'luck' ? 'luck' : 'recent',
		drops: (rows.results ?? []).map(row => ({
			rsn: member.rsn,
			itemName: row.itemName,
			itemId: row.itemId,
			source: row.source,
			killCount: row.killCount,
			denominator: row.denominator === null ? null : row.denominator / RATE_SCALE,
			share: row.share === null ? null : row.share / SHARE_SCALE,
			claimed: row.claimed === 1,
			obtainedAt: row.obtainedAt
		}))
	});
}

async function memberFor(code, request, env)
{
	return env.DB.prepare('SELECT rsn FROM members WHERE group_code = ? AND token = ?')
		.bind(code, request.headers.get('X-Member-Token'))
		.first();
}

async function loadGroup(code, env)
{
	return env.DB.prepare('SELECT * FROM groups WHERE code = ?').bind(code).first();
}

/**
 * Everything a member is allowed to see. The creator token is not in here on purpose.
 *
 * The member count is read rather than stored, because it changes only when somebody joins and the
 * group screen is not read often enough for one extra count to be worth keeping in step.
 */
async function publicGroup(row, env)
{
	if (!row)
	{
		return null;
	}

	const count = await env.DB.prepare('SELECT COUNT(*) AS members FROM members WHERE group_code = ?')
		.bind(row.code)
		.first();

	return {
		code: row.code,
		name: row.name,
		creatorRsn: row.creator_rsn,
		createdAt: row.created_at,
		members: count?.members ?? 0
	};
}

async function unusedCode(env)
{
	for (let attempt = 0; attempt < 10; attempt++)
	{
		const code = randomCode();
		const existing = await env.DB.prepare('SELECT code FROM groups WHERE code = ?')
			.bind(code)
			.first();

		if (!existing)
		{
			return code;
		}
	}

	throw new Error('Could not find an unused code');
}

function randomCode()
{
	const bytes = crypto.getRandomValues(new Uint8Array(CODE_LENGTH));
	let code = '';
	for (const byte of bytes)
	{
		code += CODE_ALPHABET[byte % CODE_ALPHABET.length];
	}

	return code;
}

function randomToken()
{
	const bytes = crypto.getRandomValues(new Uint8Array(TOKEN_BYTES));
	return [...bytes].map(byte => byte.toString(16).padStart(2, '0')).join('');
}

async function readJson(request)
{
	try
	{
		return await request.json();
	}
	catch (error)
	{
		return null;
	}
}

function json(body, status = 200)
{
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'content-type': 'application/json' }
	});
}

function withCors(response)
{
	const headers = new Headers(response.headers);
	headers.set('access-control-allow-origin', '*');
	headers.set('access-control-allow-headers', 'content-type, x-creator-token, x-member-token');
	headers.set('access-control-allow-methods', 'GET, POST, PATCH, DELETE, OPTIONS');

	return new Response(response.body, { status: response.status, headers });
}
