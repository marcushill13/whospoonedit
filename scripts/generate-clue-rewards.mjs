/**
 * Builds the clue reward rates, from the wiki, once.
 *
 * Dink's drop data covers monsters and nothing else, so a clue reward arrives with a kill count the
 * game does keep, how many caskets of that tier have been opened, and no rate to judge it against.
 * This supplies the missing half.
 *
 * Read from the rendered page rather than its source. The source writes a rarity as a MediaWiki sum,
 * `1/{{#expr:1/( 1/23 * 1/37 ) round 1}}`, which would mean carrying an expression evaluator around
 * to find out it means 1 in 851. The wiki has already done that arithmetic by the time it renders,
 * and states the answer plainly in each row's alt text.
 *
 * Run with: node scripts/generate-clue-rewards.mjs
 * Output:   src/main/resources/com/spoon/clue-rewards.json
 */

import { writeFileSync } from 'node:fs';

const TIERS = ['beginner', 'easy', 'medium', 'hard', 'elite', 'master'];

const WIKI = 'https://oldschool.runescape.wiki/api.php';
const AGENT = 'whospoonedit-build/1.0 (github.com/marcushill13)';

/**
 * Anything commoner than this is not carried.
 *
 * A casket's ordinary contents, runes, coins, a handful of seeds, are most of every table, and
 * nobody has ever been called a spoon for pulling seeds. What people talk about is all rarer than
 * this, and the file is a third of the size without the rest.
 */
const RARITY_FLOOR = 100;

/** "…drops Occult ornament kit with rarity 1/851 in quantity 1" */
const ROW = /drops ([^:]+?) with rarity ([^ ]+) in quantity/g;

async function rendered(page)
{
	const url = `${WIKI}?action=parse&page=${encodeURIComponent(page)}&prop=text&format=json`;
	const response = await fetch(url, { headers: { 'user-agent': AGENT } });

	if (!response.ok)
	{
		throw new Error(`${page}: the wiki said ${response.status}`);
	}

	const body = await response.json();
	return body?.parse?.text?.['*'] ?? '';
}

/** Turns "1/851" or "3/128" into the "1 in N" a rate is judged on. Null for Always and Varies. */
function denominator(raw)
{
	const fraction = /^([\d,]+(?:\.\d+)?)\/([\d,]+(?:\.\d+)?)$/.exec(raw.trim());
	if (!fraction)
	{
		return null;
	}

	const top = Number(fraction[1].replace(/,/g, ''));
	const bottom = Number(fraction[2].replace(/,/g, ''));

	return top > 0 && bottom > 0 ? bottom / top : null;
}

async function tier(name)
{
	const html = await rendered(`Reward casket (${name})`);
	const rewards = {};

	for (const match of html.matchAll(ROW))
	{
		const item = match[1].replace(/&#\d+;|&[a-z]+;/g, ' ').trim();
		const rate = denominator(match[2]);

		if (!item || rate === null || rate < RARITY_FLOOR)
		{
			continue;
		}

		// The rarest listing wins where an item appears twice. A page often lists a thing on its own
		// and again inside a set, and the standalone rate is the one a single drop was rolled on.
		const key = item.toLowerCase();
		if (!rewards[key] || rate > rewards[key])
		{
			rewards[key] = Math.round(rate * 100) / 100;
		}
	}

	return rewards;
}

const all = {};
let total = 0;

for (const name of TIERS)
{
	const rewards = await tier(name);
	all[name] = rewards;
	total += Object.keys(rewards).length;

	const rarest = Object.entries(rewards).sort((a, b) => b[1] - a[1])[0];
	console.log(`  ${name.padEnd(9)} ${String(Object.keys(rewards).length).padStart(3)} rewards`
		+ (rarest ? `   rarest: ${rarest[0]} at 1 in ${rarest[1]}` : ''));
}

const out = 'src/main/resources/com/spoon/clue-rewards.json';
writeFileSync(out, JSON.stringify(all));
console.log(`\n${total} rewards written to ${out}`);
