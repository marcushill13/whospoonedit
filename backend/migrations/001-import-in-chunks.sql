-- Reading a Discord channel in chunks, for databases made before it did.
--
-- CREATE TABLE IF NOT EXISTS leaves an existing table exactly as it was, so schema.sql alone does not
-- reach a deployment that is already running. Run this once against it:
--
--   wrangler d1 execute spoons --remote --file=migrations/001-import-in-chunks.sql
--
-- Safe to run against a fresh database too: the columns are already there and SQLite will say so
-- rather than doing any harm.

ALTER TABLE groups ADD COLUMN discord_cursor TEXT;
ALTER TABLE groups ADD COLUMN discord_sweep_newest INTEGER;
ALTER TABLE groups ADD COLUMN discord_read_through INTEGER;
