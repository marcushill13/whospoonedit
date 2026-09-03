# The Discord bot

Optional. A group works without it; this is only for bringing in the history a group already has
sitting in its Dink channel.

## Why it is split in two

Discord supplies **which channel**. The plugin supplies **who may read it**.

A slash command cannot prove who typed it, Discord knows a Discord account, not a RuneScape one, so
`/spoons link` does nothing but record the channel. The import itself is authorised from the plugin
by whoever holds the group's creator token, which is the only thing that proves anything.

That way someone who knows a group code can point the bot at a channel, and still cannot read it.

## Setting it up

The application is at https://discord.com/developers.

1. **Bot** tab: enable the **Message Content** intent. Self-serve below 100 servers; verification is
   only needed above that.
2. Set the token as a secret. It is never committed and never in the source:

   ```
   wrangler secret put DISCORD_BOT_TOKEN
   ```

3. Point Discord's **Interactions Endpoint URL** at
   `https://spoons.marcushill3313.workers.dev/discord/interactions`. Discord will refuse it unless the
   signature check works, which is the point of it.
4. Register the command:

   ```
   curl -X POST https://spoons.marcushill3313.workers.dev/discord/register \
     -H "X-Bot-Token: <the same token>"
   ```

   It answers with the invite link.

## Using it

1. Invite the bot to the server. It asks for **View Channel** and **Read Message History**, and
   nothing else.
2. In the channel Dink posts to: `/spoons link ABC123`
3. In the plugin, as the group's creator: **Import from Discord**

Nothing is read until step 3, and the import shows what it found before it commits to anything.

A group is linked to one channel at a time, so a second server is `/spoons link` and import again.
Nothing already brought in comes in twice: drops are keyed on Discord's own message ids.

## Why a channel is read in pieces

A clan's whole history is more than one answer can carry. The plugin waits ten seconds before it
decides the service has died, and a Worker may only make fifty subrequests while answering one
request, of which every hundred messages of history is one.

So a read is bounded, by pages and by the clock, and hands back a cursor. The plugin asks again with
it until the channel is finished, which is why the panel counts up rather than sitting still. A plugin
too old to know about cursors is not left out: the service keeps its place in the channel instead, and
each press of the button moves it on a chunk.

That place is kept in three columns added to `groups`. A database made before them needs them:

```
wrangler d1 execute spoons --remote --file=migrations/001-import-in-chunks.sql
```
