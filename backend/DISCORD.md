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
