-- Who Spooned It — the small service groups share.
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
	discord_channel_id TEXT
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
