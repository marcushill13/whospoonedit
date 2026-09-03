/**
 * Builds the rate table the service scores imported drops with.
 *
 * The plugin can already do this on its own: it has the drop data and it can ask the game what an
 * item is called. The service can do neither. It has a name out of a Dink message and nothing to
 * match it against, so a drop whose message did not state a rarity was kept and never scored, even
 * when the rate was sitting in a file two directories away.
 *
 * This turns the id-keyed data the plugin uses into the name-keyed table the service needs, using a
 * published item list for the names. Item ids are not in a Dink message and never will be.
 *
 * Run with: node scripts/generate-rates.mjs
 * Output:   backend/src/rates.json
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs';

const DROPS = 'src/main/resources/com/spoon/npc-drops.json';
const CLUES = 'src/main/resources/com/spoon/clue-rewards.json';
const EXTRA = 'src/main/resources/com/spoon/extra-drops.json';
/**
 * Written as a module rather than as JSON, so that the Worker's bundler and node's test runner load
 * it the same way. A JSON import needs an attribute in one and not the other, and a data file is not
 * worth a difference between how the thing runs and how it is tested.
 */
const OUT = 'backend/src/rates.js';

/**
 * Every item in the game by id, including the ones nobody can trade.
 *
 * The price APIs are no good here: they carry tradeables, and a pet is the drop that matters most and
 * can never be sold. This is the maintained fork of osrsbox rather than osrsbox itself, which stopped
 * being updated and is missing every item added since: Torva, Virtus, and the pets from newer bosses,
 * which between them are most of what a group would actually argue about.
 */
const ITEMS = 'https://raw.githubusercontent.com/0xNeffarion/osrsreboxed-db/master/docs/items-summary.json';

/**
 * Anything commoner than this is left out.
 *
 * Nobody has ever been called a spoon for a herb, and two thirds of this data is drop tables full of
 * them. The rarest thing excluded is commoner than one in twelve, which no collection log slot is.
 */
const FLOOR = 12;

/** One spelling, so a name written in a Discord message finds a name written in a drop table. */
const key = text => String(text ?? '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();

/**
 * Two chances at one in a thousand is not one in a thousand, and it is not one in five hundred
 * either. This is the arithmetic the wiki writes as "2 x 1/1,000".
 */
function combine(denominators)
{
	let missed = 1;
	for (const d of denominators)
	{
		missed *= 1 - 1 / d;
	}

	const chance = 1 - missed;
	return chance > 0 ? 1 / chance : null;
}

async function itemNames()
{
	const response = await fetch(ITEMS, { headers: { 'user-agent': 'whospoonedit-build/1.0' } });
	if (!response.ok)
	{
		throw new Error(`The item list said ${response.status}`);
	}

	const names = new Map();
	for (const item of Object.values(await response.json()))
	{
		names.set(Number(item.id), item.name);
	}

	return names;
}

function read(path)
{
	return existsSync(path) ? JSON.parse(readFileSync(path, 'utf8')) : {};
}

const names = await itemNames();
const sources = read(DROPS);
const extra = read(EXTRA);

// The wiki data is laid over Dink's rather than merged into it: Dink's file is theirs, kept as they
// published it, and what is missing from it is a separate thing that can be regenerated on its own.
for (const [source, drops] of Object.entries(extra))
{
	sources[source] = [...(sources[source] ?? []), ...drops];
}

const table = {};
let kept = 0;
let unnamed = 0;

for (const [source, drops] of Object.entries(sources))
{
	// Every roll at the same item, gathered before any of it is turned into a rate: an item on a
	// table twice is two chances at it, not two separate facts about it.
	const rolls = new Map();

	for (const drop of drops)
	{
		// A wiki-sourced row carries the name itself. Dink's carry an id, and -1 where the data has
		// no item at all.
		const name = drop.name ?? names.get(Number(drop.i));
		if (!name)
		{
			unnamed++;
			continue;
		}

		if (!(drop.d > 0))
		{
			continue;
		}

		const at = key(name);
		rolls.set(at, [...(rolls.get(at) ?? []), drop.d]);
	}

	const table_ = {};
	for (const [name, denominators] of rolls)
	{
		const denominator = combine(denominators);
		if (denominator && denominator >= FLOOR)
		{
			table_[name] = Math.round(denominator * 100) / 100;
			kept++;
		}
	}

	if (Object.keys(table_).length > 0)
	{
		table[key(source)] = table_;
	}
}

// Clue rewards sit in the same table under a source of their own, because a casket is scored the
// same way a monster is: what it gives, and how often. The plugin keeps them apart because it reads
// the two files for different reasons; here they are one lookup.
for (const [tier, rewards] of Object.entries(read(CLUES)))
{
	const at = {};
	for (const [name, denominator] of Object.entries(rewards))
	{
		if (denominator >= FLOOR)
		{
			at[key(name)] = denominator;
			kept++;
		}
	}

	table['clue ' + key(tier)] = at;
}

writeFileSync(OUT, '// Generated by scripts/generate-rates.mjs. Do not edit.\n'
	+ 'export default ' + JSON.stringify(table) + ';\n');

console.log(`${Object.keys(table).length} sources, ${kept} drops -> ${OUT}`);
if (unnamed > 0)
{
	console.log(`${unnamed} item ids had no name in the item list and were left out`);
}
