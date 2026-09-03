-- Remembering which rate data a group's history was scored against.
--
-- Run once against an existing deployment:
--
--   wrangler d1 execute spoons --remote --file=migrations/002-rescore-when-the-rates-change.sql
--
-- It starts empty, which reads as "scored against something older than the current data", so the
-- next import re-reads the channel once and fills in every drop that could not be scored before.

ALTER TABLE groups ADD COLUMN discord_scored_with TEXT;
