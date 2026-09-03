/**
 * Working out how rare a drop was when the message that carried it did not say.
 *
 * Run with: node --test backend/test
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { rateFor, clueTier } from '../src/index.js';

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
