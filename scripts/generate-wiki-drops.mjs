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
	{ page: 'Grotesque Guardians', as: ['Grotesque Guardians', 'Dusk', 'Dawn'] },
	{ page: 'The Hueycoatl', as: ['The Hueycoatl', 'Hueycoatl'] },
	// The Wintertodt page states no drops of its own: what it gives comes out of a supply crate, and
	// the crate has the table.
	{ page: 'Supply crate', as: ['Wintertodt', 'Supply crate'] },
	{ page: 'Tempoross', as: ['Tempoross', 'Reward pool'] }
];

/** Anything commoner than this is not what anybody means by a spoon. */
const FLOOR = 12;

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
		? pages.map(page => ({ page, as: [page] }))
		: MISSING;

	const ids = await itemIds();
	const out = existsSync(OUT) ? JSON.parse(readFileSync(OUT, 'utf8')) : {};

	for (const { page, as } of wanted)
	{
		const drops = readPage(await rendered(page));

		if (drops.size === 0)
		{
			console.log(`${page}: nothing read. The page may be named differently, or laid out differently.`);
			continue;
		}

		const rows = [...drops].map(([name, d]) =>
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

		console.log(`${page}: ${rows.length} drops, as ${as.join(', ')}`);
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
