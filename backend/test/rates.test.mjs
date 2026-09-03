/**
 * Working out how rare a drop was when the message that carried it did not say.
 *
 * Run with: node --test backend/test
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { rateFor, clueTier, filedUnder, fillFromLoot } from '../src/index.js';

test('finds a monster drop by the name a message would carry', () =>
{
	// The case the plugin's own test is pinned to, and the one Dink's message in the screenshot
	// agreed with: iron boots off a Cockatrice is one in a hundred and twenty-eight.
	assert.equal(rateFor('Cockatrice', 'Iron boots'), 128);

	assert.equal(rateFor('Vorkath', 'Draconic visage'), 5000);
	assert.equal(rateFor('Zulrah', 'Tanzanite fang'), 1024);
});

test('finds pets, which no price list would have', () =>
{
	assert.equal(rateFor('Vorkath', 'Vorki'), 3000);
	assert.equal(rateFor('Scurrius', 'Scurry'), 3000);
});

test('reads through the punctuation everything spells differently', () =>
{
	assert.equal(rateFor('alchemical hydra', "Hydra's claw"), rateFor('Alchemical Hydra', 'Hydra s claw'));
	assert.equal(rateFor('Nex', 'Torva full helm (damaged)'), 258);
	assert.ok(rateFor("Vet'ion", 'Voidwaker blade') > 0);
});

test('knows a casket from a monster, however the game worded it', () =>
{
	assert.equal(clueTier('Clue Scroll (Hard)'), 'hard');
	assert.equal(clueTier('Reward Casket (Master)'), 'master');
	assert.equal(clueTier('elite Treasure Trails'), 'elite');
	assert.equal(clueTier('Vorkath'), null);
});

test('scores a clue reward against its own tier', () =>
{
	assert.ok(rateFor('Reward Casket (Master)', '3rd age longsword') > 0);

	// The same item out of a tier that never gives it is not a rate of some other number.
	assert.equal(rateFor('Reward Casket (Beginner)', '3rd age longsword'), null);
});

test('says nothing rather than guessing', () =>
{
	// A monster the data has never heard of is not a monster with an average drop rate.
	assert.equal(rateFor('Grotesque Guardians', 'Black tourmaline core'), null);

	assert.equal(rateFor('Vorkath', 'A thing that is not a drop'), null);
	assert.equal(rateFor(null, 'Iron boots'), null);
	assert.equal(rateFor('Vorkath', null), null);
});

test('tries every name a source might be filed under', () =>
{
	// "Reward pool (Tempoross)" is what the game called the thing that gave the item, and the table
	// knows the pool and it knows Tempoross. It does not know the whole string.
	const tried = [...filedUnder('Reward pool (Tempoross)')];

	assert.ok(tried.includes('reward pool tempoross'));
	assert.ok(tried.includes('tempoross'));
	assert.ok(tried.includes('reward pool'));
});

test('a boss whose name contains a tier word is still a boss', () =>
{
	// Recognising a casket by a word appearing anywhere means "Theatre of Blood: Hard Mode" looks
	// like a hard clue. Offering the tier first must not stop the real source being tried.
	const tried = [...filedUnder('Theatre of Blood: Hard Mode')];

	assert.equal(tried[0], 'clue hard');
	assert.ok(tried.includes('theatre of blood hard mode'));
});

test('fills a drop from the loot notification beside it', () =>
{
	const drop = {
		rsn: 'JordoGerbo',
		itemName: 'Bottomless compost bucket',
		source: null,
		killCount: null,
		denominator: null,
		obtainedAt: Date.parse('2026-05-14T09:05:00.000Z')
	};

	fillFromLoot([drop], [{
		rsn: 'JordoGerbo',
		items: ['Bottomless compost bucket'],
		source: 'Hespori',
		killCount: 5,
		denominator: 35,
		at: Date.parse('2026-05-14T09:05:02.000Z')
	}]);

	assert.equal(drop.source, 'Hespori');
	assert.equal(drop.killCount, 5);
	assert.equal(drop.denominator, 35);
});

test('will not borrow details from the same item a year later', () =>
{
	const drop = {
		rsn: 'JordoGerbo',
		itemName: 'Bottomless compost bucket',
		source: null,
		killCount: null,
		denominator: null,
		obtainedAt: Date.parse('2026-05-14T09:05:00.000Z')
	};

	fillFromLoot([drop], [{
		rsn: 'JordoGerbo',
		items: ['Bottomless compost bucket'],
		source: 'Hespori',
		killCount: 900,
		at: Date.parse('2027-05-14T09:05:00.000Z')
	}]);

	// A different kill of the same boss is a different drop, and its count is not this one's.
	assert.equal(drop.source, null);
	assert.equal(drop.killCount, null);
});

test('never talks over what the drop already said', () =>
{
	const drop = {
		rsn: 'Zezima',
		itemName: 'Iron boots',
		source: 'Cockatrice',
		killCount: 65,
		denominator: null,
		obtainedAt: 1000
	};

	fillFromLoot([drop], [{
		rsn: 'Zezima',
		items: ['Iron boots'],
		source: 'Something else',
		killCount: 9999,
		denominator: 128,
		at: 1200
	}]);

	assert.equal(drop.source, 'Cockatrice');
	assert.equal(drop.killCount, 65);

	// Only the hole is filled.
	assert.equal(drop.denominator, 128);
});
