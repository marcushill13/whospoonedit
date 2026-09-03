/**
 * Keeping, and letting go of, the place in a Discord channel.
 *
 * The bug this is written against: a look that reached the end of the channel with nothing worth
 * keeping left the place parked at the oldest end. The plugin then says "nothing to bring in" and
 * never commits, so nothing ever cleared it, and every press afterwards read from the end and found
 * nothing there. A group could not be imported again at all.
 *
 * Run with: node --test backend/test
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import { rememberPlace } from '../src/index.js';

/** Just enough of D1 to see what was written, without writing it anywhere. */
function db()
{
	const ran = [];

	return {
		ran,
		DB: {
			prepare(sql)
			{
				return {
					bind(...values)
					{
						return { run: async () => ran.push({ sql, values }) };
					}
				};
			}
		}
	};
}

const message = { timestamp: '2026-08-29T20:09:00.000Z' };
const sets = (ran, column) => ran.some(({ sql }) => sql.includes(column));

test('a look that reaches the end lets go of the place', async () =>
{
	const env = db();

	await rememberPlace('ABC', {
		startingOut: true,
		chunk: { before: null, done: true },
		messages: [message],
		dryRun: true
	}, env);

	assert.equal(env.ran.length, 1);
	assert.ok(sets(env.ran, 'discord_cursor = NULL'));

	// But claims nothing about having read or scored the channel. It brought nothing in.
	assert.equal(sets(env.ran, 'discord_read_through'), false);
	assert.equal(sets(env.ran, 'discord_scored_with'), false);
});

test('an import that reaches the end says so, and says what it scored against', async () =>
{
	const env = db();

	await rememberPlace('ABC', {
		startingOut: true,
		chunk: { before: null, done: true },
		messages: [message],
		dryRun: false
	}, env);

	assert.ok(sets(env.ran, 'discord_read_through'));
	assert.ok(sets(env.ran, 'discord_scored_with'));

	// The mark is the newest message of the sweep, taken at its start.
	assert.ok(env.ran[0].values.includes(Date.parse(message.timestamp)));
});

test('a sweep still going remembers where it got to', async () =>
{
	const env = db();

	await rememberPlace('ABC', {
		startingOut: true,
		chunk: { before: '12345', done: false },
		messages: [message],
		dryRun: false
	}, env);

	assert.ok(sets(env.ran, 'discord_cursor = ?'));
	assert.ok(env.ran[0].values.includes('12345'));

	// Nothing is finished, so nothing is stamped as finished.
	assert.equal(sets(env.ran, 'discord_read_through'), false);
});

test('a plugin holding its own cursor is not given a second one to disagree with', async () =>
{
	const env = db();

	await rememberPlace('ABC', {
		paged: true,
		startingOut: true,
		chunk: { before: '12345', done: false },
		messages: [message],
		dryRun: false
	}, env);

	assert.equal(env.ran[0].values.includes('12345'), false);
	assert.ok(env.ran[0].values.includes(null));
});
