/**
 * Reading Dink's collection log messages back out of Discord.
 *
 * A group that has been posting Dink notifications to a channel has, without meaning to, kept a
 * complete record of who got what and on which kill. Dink itself cannot look backwards — it works out
 * the kill count at the moment of the drop and posts it — so that channel is the only place the
 * history exists.
 *
 * The same shape arrives from two places: Discord's own API, and a JSON export made with a tool. Both
 * hand back the same message objects, so both come through here.
 */

/** What Dink writes above the embed, with the player's name in front of it. */
const ADDED = /^(.+?) has added (.+?) to their collection/i;

/**
 * Field names as they appear in the embed.
 *
 * Matched loosely because Dink has added fields over the years and a group's oldest messages are not
 * shaped like its newest. Anything not found is left out rather than guessed at.
 */
const FIELDS = {
	source: /^source$/i,
	killCount: /^kill\s*count$/i,
	rarity: /^drop\s*rate$/i
};

/**
 * Pulls every collection log drop out of a set of Discord messages.
 *
 * @param messages raw message objects, from the API or an export
 * @returns {{drops: Array, skipped: number, names: Object}} the drops found, how many messages were
 *   not collection log notifications, and a count per player name so the caller can say who was seen
 */
export function parseDinkMessages(messages)
{
	const drops = [];
	const names = {};
	let skipped = 0;

	for (const message of messages ?? [])
	{
		const parsed = parseOne(message);
		if (!parsed)
		{
			skipped++;
			continue;
		}

		names[parsed.rsn] = (names[parsed.rsn] ?? 0) + 1;
		drops.push(parsed);
	}

	return { drops, skipped, names };
}

function parseOne(message)
{
	const embeds = message?.embeds ?? [];
	if (embeds.length === 0)
	{
		return null;
	}

	for (const embed of embeds)
	{
		// The line Dink writes lives in the embed's description on some versions and in the message
		// content on others, so both are tried.
		const text = embed?.description ?? message?.content ?? '';
		const match = ADDED.exec(stripMarkdown(text));
		if (!match)
		{
			continue;
		}

		const rsn = match[1].trim();
		const itemName = match[2].trim();
		if (!rsn || !itemName)
		{
			continue;
		}

		const fields = readFields(embed?.fields);

		return {
			// Discord's message id, so importing the same export twice changes nothing.
			id: 'dink-' + (message.id ?? `${rsn}-${itemName}-${message.timestamp ?? ''}`),
			rsn,
			itemName,
			source: fields.source ?? null,
			killCount: fields.killCount ?? null,
			denominator: fields.denominator ?? null,
			obtainedAt: toMillis(message.timestamp),
			claimed: false
		};
	}

	return null;
}

function readFields(fields)
{
	const out = { source: null, killCount: null, denominator: null };

	for (const field of fields ?? [])
	{
		const name = String(field?.name ?? '').trim();
		const value = String(field?.value ?? '').trim();

		if (FIELDS.source.test(name))
		{
			out.source = stripMarkdown(value) || null;
		}
		else if (FIELDS.killCount.test(name))
		{
			const digits = stripMarkdown(value).replace(/[^0-9]/g, '');
			out.killCount = digits ? Number(digits) : null;
		}
		else if (FIELDS.rarity.test(name))
		{
			out.denominator = readRarity(stripMarkdown(value));
		}
	}

	return out;
}

/**
 * Turns "1 in 128.0 (0.781%)" into 128.
 *
 * Read even though the rarity is looked up locally anyway, because a group's oldest messages may name
 * a monster this service does not have rates for, and Dink's own figure is better than nothing.
 */
function readRarity(value)
{
	const inN = /1\s*in\s*([0-9][0-9,]*(?:\.[0-9]+)?)/i.exec(value);
	if (inN)
	{
		const number = Number(inN[1].replace(/,/g, ''));
		return Number.isFinite(number) && number > 0 ? number : null;
	}

	// Some versions write it as a percentage instead.
	const percent = /([0-9]+(?:\.[0-9]+)?)\s*%/.exec(value);
	if (percent)
	{
		const number = Number(percent[1]);
		return Number.isFinite(number) && number > 0 ? 100 / number : null;
	}

	return null;
}

/**
 * Discord writes names and items as links and in bold, and the wording is what the name is read out
 * of, so the formatting has to come off first.
 */
function stripMarkdown(text)
{
	return String(text ?? '')
		// [Iron boots](https://...) -> Iron boots
		.replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
		.replace(/[*_`~]/g, '')
		.trim();
}

function toMillis(timestamp)
{
	if (!timestamp)
	{
		return Date.now();
	}

	const parsed = Date.parse(timestamp);
	return Number.isFinite(parsed) ? parsed : Date.now();
}
