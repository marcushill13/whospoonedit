/**
 * Reading a rarity off a wiki drop table.
 *
 * The multiplied form is the whole reason this is tested: "2 x 1/1,000" is two rolls on one kill, and
 * reading it as one in a thousand would make every Grotesque Guardians drop look twice as lucky as it
 * was. The strings here are the forms the wiki actually writes, including the ones from the Grotesque
 * Guardians page.
 *
 * Run with: node --test backend/test
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { readRarity, readPage } from '../../scripts/generate-wiki-drops.mjs';

test('reads a plain fraction', () =>
{
	assert.equal(readRarity('1/250'), 250);
	assert.equal(readRarity('1/1,000'), 1000);
	assert.equal(readRarity('1/128'), 128);
});

test('combines a multiplied rarity into the odds of it landing at all', () =>
{
	// Two rolls at one in a thousand. Not one in a thousand, and not one in five hundred either,
	// because both rolls can land.
	assert.equal(readRarity('2 × 1/1,000'), 500.25);
	assert.equal(readRarity('2 x 1/500'), 250.25);

	// Which is always a little rarer than dividing would suggest.
	assert.ok(readRarity('2 × 1/1,000') > 500);
});

test('ignores the footnote markers the wiki hangs off them', () =>
{
	assert.equal(readRarity('2 × 1/500[8]'), 250.25);
	assert.equal(readRarity('1/750[8]'), 750);
});

test('reads the other forms a rarity is written in', () =>
{
	assert.equal(readRarity('Always'), 1);
	assert.equal(readRarity('2%'), 50);
});

test('says nothing rather than a wrong number', () =>
{
	assert.equal(readRarity('Varies'), null);
	assert.equal(readRarity(''), null);
	assert.equal(readRarity('1/0'), null);
	assert.equal(readRarity(null), null);
});

test('reads the drops out of a rendered page', () =>
{
	// The shape the wiki renders a drop table row as.
	const html = `
		<img alt="Grotesque Guardians drops Granite maul with rarity 2 × 1/250 in quantity 1">
		<img alt="Grotesque Guardians drops Black tourmaline core with rarity 2 × 1/1,000 in quantity 1">
		<img alt="Grotesque Guardians drops Granite dust with rarity Always in quantity 30">
	`;

	const drops = readPage(html);

	assert.equal(drops.get('Granite maul'), 125.25);
	assert.equal(drops.get('Black tourmaline core'), 500.25);

	// A guaranteed drop is kept. There was a floor here that threw them away, and Granite dust is a
	// collection log slot: everybody has it by their first kill, which is a true thing to say about it
	// and better than showing nothing and looking broken.
	assert.equal(drops.get('Granite dust'), 1);
});

test('takes the likelier row when a page lists an item twice', () =>
{
	const html = `
		<img alt="X drops Granite ring with rarity 1/500 in quantity 1">
		<img alt="X drops Granite ring with rarity 1/2,000 in quantity 1">
	`;

	assert.equal(readPage(html).get('Granite ring'), 500);
});
