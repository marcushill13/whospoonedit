/**
 * Who Spooned It — the small service groups share.
 *
 * A RuneLite plugin only ever sees its own client, so comparing collection logs across a group needs
 * somewhere for everyone's drops to meet.
 *
 * One decision worth stating: how lucky a drop was is worked out here, from the kill count and the
 * rarity the plugin reports. The plugin says what dropped and on which kill; it does not get to say
 * how impressive that was. That does not make it cheat-proof — a modified client can still claim a
 * kill count it never reached — but it keeps an honest client's numbers right and stops the obvious.
 */

import { parseDinkMessages } from './dink.js';
import {
	fetchChannelHistory,
	handleInteraction,
	inviteUrl,
	registerCommands,
	verifySignature
} from './discord.js';

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
		// collection logs is not something done on someone else's behalf — a group of one with its
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
 * Ranked on the count rather than the average, because that is the thing people argue about — being
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

	if (!Array.isArray(messages))
	{
		if (!group.discord_channel_id)
		{
			return json({
				error: 'No Discord channel is linked to this group yet. '
					+ 'Invite the bot, then type /spoons link ' + code + ' in your Dink channel.'
			}, 409);
		}

		if (!env.DISCORD_BOT_TOKEN)
		{
			return json({ error: 'This service has no Discord bot configured' }, 501);
		}

		try
		{
			messages = await fetchChannelHistory(group.discord_channel_id, env.DISCORD_BOT_TOKEN);
		}
		catch (error)
		{
			console.error(error);
			return json({ error: 'Could not read that channel. Is the bot still in the server?' }, 502);
		}
	}

	const { drops, skipped, names } = parseDinkMessages(messages);

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

	for (const drop of drops)
	{
		const member = members.get(drop.rsn.toLowerCase());
		if (!member)
		{
			unmatched[drop.rsn] = (unmatched[drop.rsn] ?? 0) + 1;
			continue;
		}

		if (!drop.killCount)
		{
			withoutKillCount++;
		}

		matched.push({ ...drop, rsn: member });
	}

	const summary = {
		found: drops.length,
		skipped,
		matched: matched.length,
		withoutKillCount,
		names,
		unmatched
	};

	// Asked what would happen, rather than told to do it. The plugin shows this first, because an
	// import that silently drops a third of a channel is one nobody would trust afterwards.
	if (body?.dryRun)
	{
		return json({ ...summary, imported: 0, dryRun: true });
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
				'INSERT OR IGNORE INTO drops' +
				' (id, group_code, rsn, item_name, item_id, source, kill_count, denominator, share,' +
				'  obtained_at, recorded_at, claimed)' +
				' VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)')
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

	for (const rsn of touched)
	{
		await refreshTotals(code, rsn, env);
	}

	return json({
		...summary,
		imported: matched.length,
		leaderboard: await leaderboardFor(code, env)
	});
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
