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

/** A year of a busy clan's Dink messages, and a hard stop on a runaway loop. */
const MAX_PAGES = 200;

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
 * Every message in a channel, newest first, stopping once they are older than asked for.
 *
 * Paged backwards because that is the only direction Discord's API goes, and stopped early on a date
 * so a second import does not re-read years of history to find last week's.
 */
export async function fetchChannelHistory(channelId, botToken, notBefore = 0)
{
	const messages = [];
	let before = null;

	for (let page = 0; page < MAX_PAGES; page++)
	{
		const url = new URL(`${DISCORD_API}/channels/${channelId}/messages`);
		url.searchParams.set('limit', String(PAGE));
		if (before)
		{
			url.searchParams.set('before', before);
		}

		const response = await fetch(url, {
			headers: { authorization: `Bot ${botToken}` }
		});

		if (response.status === 429)
		{
			// Rate limited. Waiting the time Discord asks for is cheaper than being cut off.
			const retry = Number(response.headers.get('retry-after') ?? '1');
			await new Promise(resolve => setTimeout(resolve, Math.min(retry, 5) * 1000));
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

			throw new Error(`Discord said ${response.status}${detail}`);
		}

		const batch = await response.json();
		if (!Array.isArray(batch) || batch.length === 0)
		{
			break;
		}

		for (const message of batch)
		{
			if (notBefore && Date.parse(message.timestamp ?? '') < notBefore)
			{
				// Older than we were asked for, and they only get older from here.
				return messages;
			}

			messages.push(message);
		}

		before = batch[batch.length - 1].id;

		if (batch.length < PAGE)
		{
			break;
		}
	}

	return messages;
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
