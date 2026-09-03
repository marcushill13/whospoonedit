# Who Spooned It?

Records how lucky each collection log drop was, and compares it with your group.

You get an item, and the plugin works out what kill count you were on and how rare it was. Ten kills
for a 1 in 5,000 is a story. Six thousand kills for the same thing is not. A group can then argue
about it properly, with numbers.

## On its own

Install it and it starts recording. No group, no code, nothing sent anywhere. Your own drops, scored,
with the spooniest at the top.

## With a group

One person makes a group and shares a six character code. Everyone else pastes it in.

* A leaderboard of who has been spooned the most, with a gold, silver and bronze spoon for the top
  three and a plain number for everyone else
* **Who spooned it?**, a search that shows everyone in the group who has an item, luckiest first
* Click anybody to see their drops, newest first or biggest spoon first

## How lucky is worked out

For an item with a drop rate of 1 in N, obtained on kill C, the plugin works out the share of players
who would already have it by that point:

```
1 - (1 - 1/N)^C
```

Low is lucky. Getting something at the point where only 3% of people have it means you were luckier
than 97% of the accounts that went for it. That puts a pet and a clue reward on one scale, which is
the only way a leaderboard across different bosses means anything.

This is deliberately the same figure the Dink plugin puts in its Discord messages. A group that has
been reading "Top 40% (Lucky)" for months should not be told a different number by this.

## What is and is not counted

**Boss and monster drops** are scored from the kill count the game announces.

**Clue rewards** are scored against how many caskets of that tier have been opened.

**Pets** are scored like anything else, whether the plugin saw one drop or read it out of a Dink
message afterwards.

**Skilling pets are not scored.** They are rolled per action at a rate that changes with your level,
and nothing exposes how many logs you have chopped. They are recorded and left unscored rather than
given a made up number.

Anything else without a kill count is kept and shown, but never ranked. A drop with nothing to judge
it against is not the same as an unlucky one.

Where a Dink message states a rarity, that figure is used, so a group that has been reading those
numbers for months is not told something different about a drop it has already argued about. Where it
states none, the rate is looked up instead, from the same data the plugin scores live drops with. A
monster nothing has rates for leaves its drops unscored rather than guessed at, and importing again
once the data covers it fills them in.

## Claiming older drops

Everything from before you installed this is real, unrecorded, and worth having. So a drop can be put
to the group, and needs more than half of the other members to believe it. You cannot vote on your
own. Carried claims go on the board marked as claimed, so a total that was voted in is never mistaken
for one the plugin watched happen.

## Bringing in history from Discord

Optional, and off unless you go looking for it.

If your group posts Dink notifications to a Discord channel, that channel is already a complete record
of who got what and on which kill. The plugin can read it once and bring those drops in.

Whoever made the group invites a bot, types `/spoons link <code>` in that channel, and presses the
button in the plugin. The bot asks for View Channel and Read Message History and nothing else, and it
never reads anything until that button is pressed. What it found is shown before anything is kept,
including any names in the channel that are not in your group, which are ignored.

A channel of any size is read in pieces, with a count of how far it has got, because a clan with years
of Dink messages has more history than can be read in one go. That is one press however deep the
channel is. A read that gives out partway says what it managed, and going again brings in the rest
rather than a second copy of what is already in.

## What this sends, and to whom

The plugin talks to a small service so a group can see the same leaderboard. A RuneLite plugin only
ever sees its own client, so there is no way to do that locally.

**Sent, and only once you have joined a group:**

* your RuneScape name, so the leaderboard has something to call you
* each collection log item you get, the kill count it landed on, and how rare it is

**Not sent:** anything about your account, anything at all before you join a group, and nothing you
already had unless you press the button offering to share it. Joining does not hand over a collection
log built up over years.

**How lucky a drop was is worked out on the server**, not here. The plugin reports what dropped and on
which kill. It does not get to say how impressive that was.

The service is a Cloudflare Worker and its source is in `backend/` in this repository. A group that
would rather run its own can deploy it and change the address in the plugin's settings.

## Honest about cheating

This is trust based. A modified client can claim a kill count it never reached, and the server has no
way to tell. Scoring on the server stops the obvious version of it and keeps an honest client's
numbers right, but it is not proof of anything.

For a group of friends comparing collection logs that is the right trade. It is worth knowing rather
than discovering.

## Data

Drop rates come from the [Dink plugin](https://github.com/pajlads/DinkPlugin), used under the BSD
2-Clause Licence and credited in `NOTICE.md`. Clue reward rates are read from the
[OSRS Wiki](https://oldschool.runescape.wiki) at build time by `scripts/generate-clue-rewards.mjs`,
used under CC BY-NC-SA 3.0. Both are bundled, so the plugin never calls out to either while it runs.
