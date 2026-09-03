-- Who Spooned It, the small service groups share.
--
-- A RuneLite plugin only ever sees its own client, so comparing collection logs across a group needs
-- somewhere for everyone's drops to meet. That is all this is.

CREATE TABLE IF NOT EXISTS groups (
	-- Read aloud in Discord, so the alphabet leaves out anything that sounds like something else.
	code          TEXT PRIMARY KEY,

	name          TEXT NOT NULL,
	creator_rsn   TEXT NOT NULL,

	-- Proves who may rename or delete the group. Never sent to anyone else.
	creator_token TEXT NOT NULL,

	created_at    INTEGER NOT NULL,

	-- The channel its Dink messages go to, set by /spoons link in Discord.
	--
	-- Only ever which channel. Whether that channel may be read is decided by the plugin, where the
	-- creator token is: a slash command cannot prove who typed it, because Discord knows a Discord
	-- account and not a RuneScape one.
	discord_channel_id TEXT,

	-- Where a Discord read has got to, for a plugin that does not carry its own cursor.
	--
	-- A channel is read in chunks, because reading a clan's whole history in one answer takes longer
	-- than the plugin waits and more subrequests than a Worker is given. A plugin that knows about
	-- chunks drives the loop itself and never touches these; an older one advances one chunk per press,
	-- and this is what remembers where it was between presses.
	discord_cursor TEXT,

	-- The newest message a sweep in progress saw, taken at its start and promoted below when it ends.
	discord_sweep_newest INTEGER,

	-- The newest message a finished sweep saw. A later sweep stops here rather than reading a year of
	-- history again to find last week's drops.
	discord_read_through INTEGER
);

CREATE TABLE IF NOT EXISTS members (
	group_code  TEXT NOT NULL REFERENCES groups(code) ON DELETE CASCADE,
	rsn         TEXT NOT NULL,
	token       TEXT NOT NULL,
	joined_at   INTEGER NOT NULL,

	-- Running totals, kept beside the drops rather than summed on every read.
	--
	-- The leaderboard is the most-read thing here, and building it by scanning every drop anyone has
	-- ever logged would read thousands of rows each time somebody glances at it. These three make that
	-- one row per member. The drops remain the truth; these are rebuilt from them whenever one lands.
	spoons      INTEGER NOT NULL DEFAULT 0,
	scored      INTEGER NOT NULL DEFAULT 0,

	-- Mean share across their scored drops: the "how spooned is this account" figure. Low is lucky.
	-- Stored times 10000 because SQLite integers compare and sum without surprises.
	avg_share   INTEGER NOT NULL DEFAULT 5000,

	PRIMARY KEY (group_code, rsn)
);

CREATE TABLE IF NOT EXISTS drops (
	-- Made by the plugin, so a resend after a disconnect lands on the same row rather than a second
	-- one. For an import it is the Discord message id, which makes re-importing the same export
	-- harmless.
	id           TEXT PRIMARY KEY,

	group_code   TEXT NOT NULL REFERENCES groups(code) ON DELETE CASCADE,
	rsn          TEXT NOT NULL,

	item_name    TEXT NOT NULL,
	item_id      INTEGER NOT NULL DEFAULT -1,

	-- What dropped it, and the kill count it landed on. Both may be missing: a clue reward has no
	-- monster, and some sources never announce a count.
	source       TEXT,
	kill_count   INTEGER,

	-- The "1 in N", times 100 so a rate like 128.5 survives being an integer.
	denominator  INTEGER,

	-- Share of players who would already have it by that kill count, times 10000. Low is lucky.
	-- Worked out here rather than taken from the client: a plugin reports what dropped and on which
	-- kill, it does not get to say how impressive that was.
	share        INTEGER,

	obtained_at  INTEGER NOT NULL,
	recorded_at  INTEGER NOT NULL,

	-- Typed in by the player rather than seen happening, and shown as such wherever it appears.
	claimed      INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS drops_by_group ON drops (group_code, rsn);

-- The "who spooned it" search: every holder of one item across a group, luckiest first.
CREATE INDEX IF NOT EXISTS drops_by_item ON drops (group_code, item_name);

-- A drop somebody says they got, which the plugin never saw.
--
-- Everything from before a group installed this is in this position: real, unrecorded, and worth
-- having on the board. The answer is not to take somebody's word for it and not to refuse it either,
-- but to let the people who would know decide. A claim is put to the rest of the group and needs more
-- than half of them behind it.
--
-- Accepted claims become ordinary drops, marked as claimed wherever they appear. A total that was
-- voted in should never be mistaken for one that was watched happening.
CREATE TABLE IF NOT EXISTS claims (
	id           TEXT PRIMARY KEY,

	group_code   TEXT NOT NULL REFERENCES groups(code) ON DELETE CASCADE,

	-- Who says they got it. Taken from the token, never from the request.
	rsn          TEXT NOT NULL,

	item_name    TEXT NOT NULL,
	item_id      INTEGER NOT NULL DEFAULT -1,
	source       TEXT,
	kill_count   INTEGER,
	denominator  INTEGER,

	-- A link to a screenshot, if they have one. Kept as a link rather than a picture: the drop already
	-- happened, so there is nothing to capture, and storing other people's images means deciding how
	-- long to keep them and who may see them.
	evidence     TEXT,
	note         TEXT,

	created_at   INTEGER NOT NULL,
	settled_at   INTEGER,

	-- pending, accepted or rejected.
	status       TEXT NOT NULL DEFAULT 'pending'
);

CREATE INDEX IF NOT EXISTS claims_by_group ON claims (group_code, status);

CREATE TABLE IF NOT EXISTS votes (
	claim_id TEXT NOT NULL REFERENCES claims(id) ON DELETE CASCADE,
	rsn      TEXT NOT NULL,

	-- 1 for yes, 0 for no. Changing your mind overwrites rather than adding a second voice.
	approve  INTEGER NOT NULL,
	voted_at INTEGER NOT NULL,

	PRIMARY KEY (claim_id, rsn)
);
