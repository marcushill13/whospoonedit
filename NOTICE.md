# Third-party data and code

## npc-drops.json

`src/main/resources/com/spoon/npc-drops.json` is taken from the
[Dink plugin](https://github.com/pajlads/DinkPlugin) by pajlads, used under the BSD 2-Clause
Licence. It maps each NPC to the items it drops and how rare they are.

Used rather than rebuilt for two reasons. It is a large, carefully maintained dataset that would
take a long time to reproduce and longer to keep correct. And most groups arriving here have been
reading Dink's luck figures in Discord for months — sharing its source data means this plugin's
numbers agree with the ones they already trust, rather than being subtly different for no reason
anyone could explain.

Underlying drop rates come from the [OSRS Wiki](https://oldschool.runescape.wiki), CC BY-NC-SA 3.0.
