/**
 * The Discord side: a bot that reads one channel's history, and only when asked to.
 *
 * The split matters. Discord supplies which channel; the plugin supplies who is allowed to import it.
 * A slash command cannot prove who typed it, Discord knows a Discord account, not a RuneScape one -
 * so linking a channel is all a command may do. The import itself is authorised from the plugin by
 * whoever holds the group's creator token, which is the only thing that proves anything.
 */

const DISCORD_API = 'https://discord.com/api/v10';

/** View Channel and Read Message History. Nothing else is needed and nothing else is asked for. */
export const BOT_PERMISSIONS = '66560';

/** How many messages one page of history holds. Discord's maximum. */
const PAGE = 100;

/**
 * How many pages one chunk reads before handing back.
 *
 * Twenty pages is two thousand messages. That answers in a couple of seconds, and leaves room inside
 * Cloudflare's fifty-subrequest ceiling for the database writes that follow it.
 */
const PAGES_PER_CHUNK = 20;

/** And a clock, for when Discord itself is slow. Well inside the plugin's ten second patience. */
const CHUNK_MILLIS = 6000;

/**
 * What a read gets when what it finds is going to be written as well.
 *
 * A look at the channel spends the whole ten seconds the plugin waits on reading. Bringing it in has
 * to pay for the writing too, out of the same ten, so it reads less far each time and is asked more
 * often. Which is invisible to a plugin that drives the loop itself, and a few more presses for one
 * that does not.
 */
export const WRITING = { pages: 12, millis: 3500 };

/**
 * Checks that a request really came from Discord.
 *
 * Discord signs every interaction, and refuses to register an endpoint that does not reject a bad
 * signature. Skipping this would also let anyone post an interaction claiming to be anyone.
 */
export async function verifySignature(request, body, publicKey)
{
	const signature = request.headers.get('X-Signature-Ed25519');
	const timestamp = request.headers.get('X-Signature-Timestamp');

	if (!signature || !timestamp || !publicKey)
	{
		return false;
	}

	try
	{
		const key = await crypto.subtle.importKey(
			'raw', hexToBytes(publicKey), { name: 'Ed25519' }, false, ['verify']);

		return await crypto.subtle.verify(
			{ name: 'Ed25519' },
			key,
			hexToBytes(signature),
			new TextEncoder().encode(timestamp + body));
	}
	catch (error)
	{
		console.error('Could not check a Discord signature', error);
		return false;
	}
}

/**
 * One chunk of a channel's history, newest first.
 *
 * Deliberately not the whole channel. The plugin waits ten seconds for an answer before it decides
 * the service is dead, and a clan channel with years of Dink messages in it takes far longer than
 * that to read a hundred at a time. Cloudflare puts its own ceiling on it: a Worker may make fifty
 * subrequests while answering one request on the free plan, and every page of history is one of them.
 *
 * So a read is bounded twice over, by pages and by the clock, and hands back where it got to. The
 * caller asks again with that cursor until done comes back true. Each answer arrives in a second or
 * two however deep the channel is, and only the number of asks changes.
 *
 * @param before message id to carry on from, or null to start at the newest
 * @param notBefore stop once messages are older than this, so a later sweep reads only what is new
 * @param pages how many pages this read may take, for a caller with work of its own to do after it
 * @param millis and how long it may spend taking them
 * @returns {{messages: Array, before: string|null, done: boolean}}
 */
export async function readChannelChunk(channelId, botToken,
	{ before = null, notBefore = 0, pages = PAGES_PER_CHUNK, millis = CHUNK_MILLIS } = {})
{
	const messages = [];
	const startedAt = Date.now();
	let cursor = before;

	for (let page = 0; page < pages; page++)
	{
		const url = new URL(`${DISCORD_API}/channels/${channelId}/messages`);
		url.searchParams.set('limit', String(PAGE));
		if (cursor)
		{
			url.searchParams.set('before', cursor);
		}

		const response = await fetch(url, {
			headers: { authorization: `Bot ${botToken}` }
		});

		if (response.status === 429)
		{
			// Rate limited. Waiting the time Discord asks for is cheaper than being cut off, but only
			// while somebody is still waiting for this answer. The clock has to be read here as well as
			// at the bottom of the loop, because carrying on from here skips it: a run of these would
			// otherwise sit out a full minute inside a request that was given ten seconds, and hand back
			// nothing at the end of it.
			const retry = Number(response.headers.get('retry-after') ?? '1');
			const left = millis - (Date.now() - startedAt);

			if (left <= 0)
			{
				break;
			}

			await new Promise(resolve => setTimeout(resolve, Math.min(retry * 1000, 5000, left)));
			continue;
		}

		if (!response.ok)
		{
			// Discord's own wording, carried out to the plugin. "Missing Access" and "Unknown Channel"
			// call for completely different fixes, and a message that says neither leaves someone
			// guessing at permissions when the bot was never in the server.
			let detail = '';
			try
			{
				const problem = await response.json();
				detail = problem?.message ? `: ${problem.message}` : '';
			}
			catch (ignored)
			{
				// Not JSON. The status alone will have to do.
			}

			const refused = new Error(`Discord said ${response.status}${detail}`);
			refused.status = response.status;
			throw refused;
		}

		const batch = await response.json();
		if (!Array.isArray(batch) || batch.length === 0)
		{
			return { messages, before: null, done: true };
		}

		for (const message of batch)
		{
			if (notBefore && Date.parse(message.timestamp ?? '') < notBefore)
			{
				// Older than we were asked for, and they only get older from here.
				return { messages, before: null, done: true };
			}

			messages.push(message);
		}

		cursor = batch[batch.length - 1].id;

		if (batch.length < PAGE)
		{
			// A short page is the last page: Discord had nothing older to fill it with.
			return { messages, before: null, done: true };
		}

		if (Date.now() - startedAt >= millis)
		{
			break;
		}
	}

	return { messages, before: cursor, done: false };
}

/**
 * Handles a slash command.
 *
 * Only one exists: linking this channel to a group. It deliberately does not import anything, see
 * the note at the top of this file.
 */
export async function handleInteraction(interaction, env)
{
	// A ping, which Discord sends when the endpoint is first registered and periodically after.
	if (interaction.type === 1)
	{
		return { type: 1 };
	}

	if (interaction.type !== 2)
	{
		return reply('I only know one thing, and that was not it.');
	}

	const name = interaction.data?.name;
	if (name !== 'spoons')
	{
		return reply('I only know one thing, and that was not it.');
	}

	const sub = interaction.data?.options?.[0];
	if (sub?.name !== 'link')
	{
		return reply('Try `/spoons link <group code>`.');
	}

	const code = String(sub.options?.[0]?.value ?? '').trim().toUpperCase();
	if (!code)
	{
		return reply('Give me the group code: `/spoons link ABC123`.');
	}

	const group = await env.DB.prepare('SELECT code, name FROM groups WHERE code = ?')
		.bind(code)
		.first();

	if (!group)
	{
		return reply(`I do not know a group with the code **${code}**.`);
	}

	const channelId = interaction.channel_id;
	if (!channelId)
	{
		return reply('I could not tell which channel this is.');
	}

	await env.DB.prepare('UPDATE groups SET discord_channel_id = ? WHERE code = ?')
		.bind(channelId, code)
		.run();

	return reply(
		`Linked **${group.name}** to this channel.\n\n` +
		'Now open the plugin and press **Import from Discord**. Nothing is read until then, and only ' +
		'whoever made the group can do it.');
}

/** A reply only the person who typed the command can see. */
function reply(content)
{
	return {
		type: 4,
		data: { content, flags: 64 }
	};
}

/**
 * Tells Discord what the command looks like. Run once per deployment; harmless to repeat.
 */
export async function registerCommands(applicationId, botToken)
{
	const body = [{
		name: 'spoons',
		description: 'Who Spooned It',
		options: [{
			type: 1,
			name: 'link',
			description: 'Link this channel to a group, so its Dink history can be imported',
			options: [{
				type: 3,
				name: 'code',
				description: 'The group code from the plugin',
				required: true
			}]
		}]
	}];

	const response = await fetch(`${DISCORD_API}/applications/${applicationId}/commands`, {
		method: 'PUT',
		headers: {
			authorization: `Bot ${botToken}`,
			'content-type': 'application/json'
		},
		body: JSON.stringify(body)
	});

	if (!response.ok)
	{
		throw new Error(`Discord said ${response.status} registering the command`);
	}

	return response.json();
}

export function inviteUrl(applicationId)
{
	return `https://discord.com/oauth2/authorize?client_id=${applicationId}` +
		`&permissions=${BOT_PERMISSIONS}&scope=bot+applications.commands`;
}

function hexToBytes(hex)
{
	const bytes = new Uint8Array(hex.length / 2);
	for (let i = 0; i < bytes.length; i++)
	{
		bytes[i] = parseInt(hex.substr(i * 2, 2), 16);
	}

	return bytes;
}
