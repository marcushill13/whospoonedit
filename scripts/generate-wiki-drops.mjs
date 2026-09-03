/**
 * Fills the holes in Dink's drop data, from the wiki.
 *
 * Dink's file covers six hundred odd monsters and not every one. Grotesque Guardians is not in it,
 * which is why a Black tourmaline core at 118 kills sat on the board unscored while the wiki page for
 * it says plainly that it is two rolls at one in a thousand.
 *
 * Kept beside Dink's data rather than merged into it. Theirs is theirs, used under their licence and
 * credited, and what is missing from it is a separate thing that can be rebuilt on its own.
 *
 * Run with: node scripts/generate-wiki-drops.mjs [Monster page] [Another monster page]
 * Output:   src/main/resources/com/spoon/extra-drops.json
 */

import { writeFileSync, readFileSync, existsSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const WIKI = 'https://oldschool.runescape.wiki/api.php';
const AGENT = 'whospoonedit-build/1.0 (github.com/marcushill13)';
const ITEMS = 'https://raw.githubusercontent.com/0xNeffarion/osrsreboxed-db/master/docs/items-summary.json';
const OUT = 'src/main/resources/com/spoon/extra-drops.json';

/**
 * What is missing, and what the game calls it when it drops something.
 *
 * The two are not the same. A Grotesque Guardians kill is announced by Dink as Dusk, because Dusk is
 * what dies last, while its pet notification says Grotesque Guardians. Both have to find the table or
 * half the drops stay unscored, so one page is registered under every name it answers to.
 */
const MISSING = [
	{ pages: ['Grotesque Guardians'], as: ['Grotesque Guardians', 'Dusk', 'Dawn'] },
	{ pages: ['The Hueycoatl'], as: ['The Hueycoatl', 'Hueycoatl'] },

	// What Wintertodt gives comes out of a supply crate, and the crate has the table. Its rates move
	// with points and level, so both pages may state none at all.
	{ pages: ['Supply crate', 'Wintertodt'], as: ['Wintertodt', 'Supply crate'] },

	{ pages: ['Tempoross', 'Reward pool'], as: ['Tempoross', 'Reward pool'] },

	// Named after the chest rather than the fight, which is what the game calls the thing that gave
	// it, and so what Dink writes.
	{
		pages: ['Lunar Chest'],
		as: ['Lunar Chest', 'Moons of Peril', 'Blood Moon', 'Blue Moon', 'Eclipse Moon']
	},

	// The two Gauntlets are read from their bosses rather than from the one page that describes both.
	// That page states the corrupted rates, so taking it for the normal Gauntlet scored a Youngllef
	// against one in eight hundred when the normal one is one in two thousand: not a missing number
	// but a wrong one, which is worse.
	{
		pages: ['Crystalline Hunllef', 'The Gauntlet'],
		as: ['The Gauntlet', 'Gauntlet', 'Crystalline Hunllef']
	},
	{
		pages: ['Corrupted Hunllef', 'Corrupted Gauntlet'],
		as: ['Corrupted Gauntlet', 'Corrupted Hunllef']
	},

	// The Colosseum's rewards are the last boss's drops, not the arena's.
	{ pages: ['Sol Heredit', 'Fortis Colosseum'], as: ['Fortis Colosseum', 'Sol Heredit'] },

	{
		pages: ['Royal Titans', 'The Royal Titans', 'Branda the Fire Queen'],
		as: ['The Royal Titans', 'Royal Titans', 'Branda the Fire Queen', 'Eldric the Ice King']
	},

	// The raids, which may well read nothing however they are asked for. Their uniques are stated per
	// raid and move with points and team size, so there is often no per-kill figure on the page to
	// take. That is a fact about raids rather than a wrong page name, and it is the same reason
	// Wintertodt cannot be scored.
	{ pages: ['Chambers of Xeric', 'Chambers of Xeric/Loot'], as: ['Chambers of Xeric'] },
	{ pages: ['Theatre of Blood', 'Theatre of Blood/Loot'], as: ['Theatre of Blood'] },
	{ pages: ['Tombs of Amascut', 'Tombs of Amascut/Loot'], as: ['Tombs of Amascut'] }
];

/** Kept however common: a guaranteed drop is a collection log slot like any other. */
const FLOOR = 1;

/** "drops Black tourmaline core with rarity 2 × 1/1,000 in quantity 1" */
const ROW = /drops (.+?) with rarity (.+?) in quantity/g;

/**
 * The per-kill odds a rarity is written as, however the wiki wrote it.
 *
 * The multiplied form is the one that matters and the one that is easy to get wrong. "2 × 1/1,000" is
 * two separate rolls on one kill, which is neither one in a thousand nor one in five hundred: it is
 * one in five hundred and a quarter, because the two rolls can both land. Read as one in a thousand
 * every Grotesque Guardians drop would look twice as lucky as it was.
 *
 * @returns the "1 in N", or null when the row says something this cannot read
 */
export function readRarity(text)
{
	const clean = String(text ?? '')
		.replace(/&#\d+;|&[a-z]+;/g, ' ')
		.replace(/\[\d+\]/g, '')
		.replace(/,/g, '')
		.trim();

	if (/always/i.test(clean))
	{
		return 1;
	}

	const fraction = /(?:([0-9]+(?:\.[0-9]+)?)\s*[x×]\s*)?([0-9]+(?:\.[0-9]+)?)\s*\/\s*([0-9]+(?:\.[0-9]+)?)/i
		.exec(clean);

	if (fraction)
	{
		const rolls = fraction[1] ? Number(fraction[1]) : 1;
		const top = Number(fraction[2]);
		const bottom = Number(fraction[3]);

		if (!(top > 0) || !(bottom > 0) || !(rolls > 0))
		{
			return null;
		}

		// 1 - (1 - p)^rolls, the chance of it landing at least once.
		const chance = 1 - Math.pow(1 - top / bottom, rolls);
		return chance > 0 ? Math.round((1 / chance) * 100) / 100 : null;
	}

	const percent = /([0-9]+(?:\.[0-9]+)?)\s*%/.exec(clean);
	if (percent && Number(percent[1]) > 0)
	{
		return Math.round((100 / Number(percent[1])) * 100) / 100;
	}

	return null;
}

/** The drops a rendered page states, as name and odds. */
export function readPage(html)
{
	const found = new Map();

	for (const [, item, rarity] of html.matchAll(ROW))
	{
		const name = item.replace(/&#\d+;|&[a-z]+;/g, ' ').replace(/\s+/g, ' ').trim();
		const denominator = readRarity(rarity);

		if (!name || !denominator || denominator < FLOOR)
		{
			continue;
		}

		// A page can list the same item on more than one table. The likelier row wins, rather than
		// two rows being multiplied into something rarer than either.
		const already = found.get(name);
		if (!already || denominator < already)
		{
			found.set(name, denominator);
		}
	}

	return found;
}

async function rendered(page)
{
	const url = `${WIKI}?action=parse&page=${encodeURIComponent(page)}&prop=text&format=json`;
	const response = await fetch(url, { headers: { 'user-agent': AGENT } });

	if (!response.ok)
	{
		throw new Error(`${page}: the wiki said ${response.status}`);
	}

	const body = await response.json();
	const html = body?.parse?.text?.['*'];

	if (!html)
	{
		throw new Error(`${page}: the wiki returned no text`);
	}

	return html;
}

async function itemIds()
{
	const response = await fetch(ITEMS, { headers: { 'user-agent': AGENT } });
	if (!response.ok)
	{
		throw new Error(`The item list said ${response.status}`);
	}

	const ids = new Map();
	for (const item of Object.values(await response.json()))
	{
		// The lowest id wins, since the duplicates are placeholders and noted copies of the real one.
		const at = String(item.name).toLowerCase();
		const id = Number(item.id);
		if (!ids.has(at) || id < ids.get(at))
		{
			ids.set(at, id);
		}
	}

	return ids;
}

async function main(pages)
{
	const wanted = pages.length > 0
		? pages.map(page => ({ pages: [page], as: [page] }))
		: MISSING;

	const ids = await itemIds();
	const out = existsSync(OUT) ? JSON.parse(readFileSync(OUT, 'utf8')) : {};

	for (const { pages: candidates, as } of wanted)
	{
		// Tried in turn until one of them states some drops. A boss is written up under whichever name
		// the wiki settled on, and its table is as often on the thing that dropped it as on the fight:
		// the Colosseum's rewards are Sol Heredit's, Wintertodt's are a supply crate's.
		let found = null;

		for (const page of candidates)
		{
			let drops;

			try
			{
				drops = readPage(await rendered(page));
			}
			catch (error)
			{
				// A page that is not there is one of the guesses being wrong, which is what the rest of
				// the list is for.
				continue;
			}

			if (drops.size > 0)
			{
				found = { page, drops };
				break;
			}
		}

		if (!found)
		{
			console.log(`${as[0]}: nothing read from ${candidates.join(', ')}.`);
			continue;
		}

		const rows = [...found.drops].map(([name, d]) =>
		{
			const id = ids.get(name.toLowerCase());

			// The name is what the service matches on and the id is what the plugin matches on, so a
			// row carries both, and one the item list has never heard of is still useful to the half
			// that reads names.
			return id ? { name, d, i: id } : { name, d };
		});

		for (const name of as)
		{
			out[name] = rows;
		}

		console.log(`${found.page}: ${rows.length} drops, as ${as.join(', ')}`);
		for (const row of rows.slice(0, 8))
		{
			console.log(`   1 in ${row.d}  ${row.name}${row.i ? '' : '  (no item id)'}`);
		}
	}

	writeFileSync(OUT, JSON.stringify(out, null, '\t') + '\n');
	console.log(`\nWritten to ${OUT}. Now run: node scripts/generate-rates.mjs`);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href)
{
	await main(process.argv.slice(2));
}
