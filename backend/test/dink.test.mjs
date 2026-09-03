/**
 * Reading Dink's notifications back out of a Discord channel.
 *
 * The messages here are shaped like the real ones, fields and wording copied from a channel rather
 * than invented, because everything this file tests is a guess about somebody else's format.
 *
 * Run with: node --test backend/test
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { parseDinkMessages } from '../src/dink.js';

/** A collection log notification, as Dink writes one. */
const CLOG = {
	id: '1',
	timestamp: '2026-08-28T18:09:00.000Z',
	embeds: [{
		description: '**JordoGerbo** has added [Black tourmaline core](https://oldschool.runescape.wiki)'
			+ ' to their collection',
		fields: [
			{ name: 'Completed', value: '257/1712 (15.0%)' },
			{ name: 'Rank', value: 'Bronze' },
			{ name: 'Source', value: 'Dusk' },
			{ name: 'Kill Count', value: '118' }
		]
	}]
};

/** A pet notification, which is a different shape entirely. */
const PET = {
	id: '2',
	timestamp: '2026-08-29T20:09:00.000Z',
	embeds: [{
		description: 'JordoGerbo has a funny feeling like they\'re being followed',
		fields: [
			{ name: 'Name', value: 'Noon' },
			{ name: 'Status', value: 'New!' },
			{ name: 'Milestone', value: '178 killcount from Grotesque Guardians' },
			{ name: 'Rarity', value: '1 in 3000.0 (0.0333%)' },
			{ name: 'Luck', value: 'Top 5.7% (Lucky)' }
		]
	}]
};

const only = message => parseDinkMessages([message]).drops[0];

test('reads a collection log notification', () =>
{
	const drop = only(CLOG);

	assert.equal(drop.rsn, 'JordoGerbo');
	assert.equal(drop.itemName, 'Black tourmaline core');
	assert.equal(drop.source, 'Dusk');
	assert.equal(drop.killCount, 118);

	// This one states no rarity, so there is nothing to score it by from the message alone.
	assert.equal(drop.denominator, null);
});

test('reads a pet notification, which was skipped entirely before', () =>
{
	const drop = only(PET);

	assert.equal(drop.rsn, 'JordoGerbo');

	// The wording never says which pet. Only the Name field does.
	assert.equal(drop.itemName, 'Noon');

	// And the kill count and the monster are written into one field together.
	assert.equal(drop.killCount, 178);
	assert.equal(drop.source, 'Grotesque Guardians');
	assert.equal(drop.denominator, 3000);
});

test('takes the pet wording in either person', () =>
{
	for (const line of [
		'Zezima has a funny feeling like they are being followed',
		'Zezima have a funny feeling like you are being followed',
		'Zezima has a funny feeling like you would have been followed'])
	{
		const drop = only({ id: 'x', embeds: [{ description: line, fields: [{ name: 'Name', value: 'Vorki' }] }] });
		assert.equal(drop?.itemName, 'Vorki', line);
	}
});

test('leaves a pet with no name alone rather than recording an unnamed something', () =>
{
	const { drops, skipped } = parseDinkMessages([{
		id: '3',
		embeds: [{ description: 'Zezima has a funny feeling like they are being followed', fields: [] }]
	}]);

	assert.equal(drops.length, 0);
	assert.equal(skipped, 1);
});

test('reads a kill count written with thousands separators', () =>
{
	const drop = only({
		id: '4',
		embeds: [{
			description: 'Zezima has a funny feeling like they are being followed',
			fields: [
				{ name: 'Name', value: 'Vorki' },
				{ name: 'Milestone', value: '1,204 killcount from Vorkath' }
			]
		}]
	});

	assert.equal(drop.killCount, 1204);
	assert.equal(drop.source, 'Vorkath');
});

test('counts everything that is not a drop as skipped', () =>
{
	const { drops, skipped, names } = parseDinkMessages([
		CLOG,
		PET,
		{ id: '5', embeds: [{ description: 'JordoGerbo has levelled Slayer to 92' }] },
		{ id: '6', content: 'anyone up for a raid' }
	]);

	assert.equal(drops.length, 2);
	assert.equal(skipped, 2);
	assert.deepEqual(names, { JordoGerbo: 2 });
});

test('keys a drop on the message id, so importing twice changes nothing', () =>
{
	assert.equal(only(PET).id, 'dink-2');
	assert.equal(only(CLOG).id, 'dink-1');
});

/** Tempoross, whose count field is not called Kill Count. */
const COMPLETION = {
	id: '7',
	timestamp: '2026-01-09T17:17:00.000Z',
	embeds: [{
		description: '**JordoGerbo** has added [Fish barrel](https://oldschool.runescape.wiki) to their'
			+ ' collection',
		fields: [
			{ name: 'Completed', value: '264/1712 (15.4%)' },
			{ name: 'Rank', value: 'Bronze' },
			{ name: 'Source', value: 'Reward pool (Tempoross)' },
			{ name: 'Completion Count', value: '3' }
		]
	}]
};

/** A loot notification, which is not a drop but knows things about one. */
const LOOT = {
	id: '8',
	timestamp: '2026-05-14T09:05:00.000Z',
	embeds: [{
		description: '**JordoGerbo** has looted: \n\n1 x [Bottomless compost bucket](https://wiki) (793K)'
			+ '\nFrom: [Hespori](https://wiki)',
		fields: [
			{ name: 'Kill Count', value: '5' },
			{ name: 'Total Value', value: '794K gp' },
			{ name: 'Item Rarity', value: '1 in 35.0 (2.86%)' }
		]
	}]
};

test('reads a count field however Dink named it', () =>
{
	// Dink names it after what was done: a kill, a player kill, a pickpocket, or a completion for
	// everything else. Tempoross, Wintertodt, the raids and the Gauntlet are all completions.
	assert.equal(only(COMPLETION).killCount, 3);

	for (const name of ['Kill Count', 'Player Kill Count', 'Pickpocket Count', 'Completion Count'])
	{
		const drop = only({
			id: 'x',
			embeds: [{
				description: 'Zezima has added Thing to their collection',
				fields: [{ name, value: '42' }]
			}]
		});

		assert.equal(drop.killCount, 42, name);
	}
});

test('a loot notification is detail, never a drop', () =>
{
	const { drops, details } = parseDinkMessages([LOOT]);

	// Loot fires for anything valuable, and most valuable things are not a collection log slot.
	assert.equal(drops.length, 0);

	assert.equal(details.length, 1);
	assert.equal(details[0].rsn, 'JordoGerbo');
	assert.deepEqual(details[0].items, ['Bottomless compost bucket']);
	assert.equal(details[0].source, 'Hespori');
	assert.equal(details[0].killCount, 5);
	assert.equal(details[0].denominator, 35);
});

test('a rarity is only an item\'s when it was the only item', () =>
{
	const { details } = parseDinkMessages([{
		id: '9',
		embeds: [{
			description: 'Zezima has looted: \n\n1 x Dragon axe (60K)\n2 x Shark (1K)\nFrom: Callisto',
			fields: [{ name: 'Item Rarity', value: '1 in 128.0' }]
		}]
	}]);

	assert.deepEqual(details[0].items, ['Dragon axe', 'Shark']);

	// Dink's figure is the rarest thing in the whole drop, so with two items it describes neither.
	assert.equal(details[0].denominator, null);
});
