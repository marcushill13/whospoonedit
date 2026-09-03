# Third-party data and code

## npc-drops.json

`src/main/resources/com/spoon/npc-drops.json` is taken from the
[Dink plugin](https://github.com/pajlads/DinkPlugin) by pajlads, used under the BSD 2-Clause
Licence. It maps each NPC to the items it drops and how rare they are.

Used rather than rebuilt for two reasons. It is a large, carefully maintained dataset that would
take a long time to reproduce and longer to keep correct. And most groups arriving here have been
reading Dink's luck figures in Discord for months, sharing its source data means this plugin's
numbers agree with the ones they already trust, rather than being subtly different for no reason
anyone could explain.

Underlying drop rates come from the [OSRS Wiki](https://oldschool.runescape.wiki), CC BY-NC-SA 3.0.

## extra-drops.json

`src/main/resources/com/spoon/extra-drops.json` is read from the
[OSRS Wiki](https://oldschool.runescape.wiki) by `scripts/generate-wiki-drops.mjs`, under
CC BY-NC-SA 3.0. It covers monsters Dink's data does not, Grotesque Guardians among them, and is laid
over Dink's file rather than merged into it so that theirs stays as they published it.

## clue-rewards.json

`src/main/resources/com/spoon/clue-rewards.json` is read from the
[OSRS Wiki](https://oldschool.runescape.wiki) by `scripts/generate-clue-rewards.mjs`, under
CC BY-NC-SA 3.0. Dink's data covers monsters, and a clue reward has no monster.

## Item names

`scripts/generate-rates.mjs` and `scripts/generate-wiki-drops.mjs` read item names from
[osrsreboxed-db](https://github.com/0xNeffarion/osrsreboxed-db), the maintained fork of
[osrsbox-db](https://github.com/osrsbox/osrsbox-db), under GPL-3.0. Only names and ids are used, at
build time, and none of it is shipped.

The service needs them because the plugin does not: a plugin can ask the running game what an item is
called, and a Worker reading a Discord message has only the name written in it.
