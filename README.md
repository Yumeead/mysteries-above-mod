# Mysteries Above

A Fabric mod for Minecraft 26.2, written in Kotlin and based on *Lord of the Mysteries*. You drink a potion,
become a Beyonder, and climb a Pathway from Sequence 9 to Sequence 0, picking up new powers on the way.

It started as a Spigot/Paper plugin, [mysteries-above](https://github.com/Yumeead/mysteries-above). The plugin
was frozen on 26.08.2026 and everything happens here now, but it is still the reference for rules, balance and
lore.

## Why move off Spigot

Six of the plugin's nine runtime dependencies (MythicMobs, Citizens, BetterModel, glowingentities, triumph-gui,
packetevents) existed only to fake things a mod can just do: its own entities, models and screens. Its three
"pocket worlds" stood in for real dimensions. A lot of the abilities were shaped by the same limits, with a
chest menu where an item in your hand should be, a polling loop where an event should be, and a teleport
where an actual journey should be.

So this isn't a line-by-line translation. Each ability keeps its pathway, Sequence, name and lore from the
plugin, and gets rebuilt where the mod can do it properly. The Spirit World is a real dimension you walk
into through a door. A ritual is candles and offerings on an actual altar. When Fool's Paper Figurine
Substitution takes a hit for you, your body turns into a paper figure in your exact pose, the blow cuts
through it, and you step out of thin air a few blocks away.

## What works today

- The core loop: potion, advancement, casting. Spirituality, mastery, and sanity loss that you hear and see
  (whispers, a heartbeat, the screen draining of colour).
- Nine pathways are playable from Sequence 9 to 5: White Tower, Sun, Door, Error, Visionary, Tyrant,
  Justiciar, Fool and Death. The other 13 already have potions, characteristics and colours, but no
  abilities yet.
- Two custom dimensions, the Spirit World and the Underworld.
- An ability wheel (hold R) and a grimoire (B) for groups and hotkeys, plus a small HUD.
- A creature engine that replaces MythicMobs: the plugin's 64 creatures, townsfolk and guards with daily
  routines, wild Beyonders, and a three-act boss. Creatures are datapack entries running on vanilla mob
  bodies, and they react to threat and to your Sequence.
- Structures, 163 ingredient items and four animated creature models ship inside the jar, so there is no
  separate resource pack or datapack to install.
- Loot in vanilla chests leans toward the place (shipwrecks favour Tyrant ingredients) and toward the
  pathway and Sequence of whoever opens the chest.
- Sun, Door and Fool have been reworked so their abilities show themselves in the world (light, doors you
  can see into, paper and threads) instead of printing to chat.

Game tests cast every ability of every pathway, at every Sequence and in every mode, on a real server. If a
power throws, the build fails.

Player-facing text is Ukrainian for now. Item names have English translations, but ability descriptions
and in-game messages don't yet.

## What's next

The port itself is almost done. Persistence and the final retest still need full playthroughs with two
clients, and after that comes the first release. Beyond that, roughly in this order:

- Move the remaining six pathways off chat and onto the same in-world visuals as Sun, Door and Fool. Bloom
  comes after that, carefully, because Iris shader packs don't run custom shaders.
- Potion brewing with real recipes, and foraging as an actual plant block.
- The underground market (the Gathering) with its broker NPC.
- Churches and secret orders. They were left out of the port on purpose and will be designed from scratch.
- Generated cities with living NPCs.
- Abilities for the 13 stub pathways.
- Sequences 4 to 0. The plugin never got past 5 on any pathway, so there's nothing to port here and all of
  it has to be designed.

## Layout

| Module | Contents | Builds without Minecraft |
|---|---|---|
| `core` | pure game rules: progression, balance, state, creature AI rules | yes |
| `mod` | the Fabric side: abilities, effects, mixins, persistence, UI | no |

`core` has no Loom plugin and no Minecraft dependency, so if a platform type leaks in, it simply won't
compile. Inside `mod`, `src/main` is shared code and `src/client` holds rendering and screens, so a client
class used on the server is a compile error rather than a crash on a dedicated server.

## Building

You need Java 25. If it's missing, Gradle downloads it through the Foojay resolver.

```bash
./gradlew build              # everything, game tests included
./gradlew :core:test         # rules only, fast, no game
./gradlew :mod:runGameTest   # server game tests: creatures, loot, every ability
./gradlew :mod:runClient     # dev client
./gradlew :mod:runServer     # dev server
```

To try it in game as an operator:

```
/mysteries potion Tyrant 9       # get a potion
/mysteries mastery 100           # max out mastery so you can advance
/mysteries creature spawn <id>   # summon a creature
```

Versions are taken from `FabricMC/fabric-example-mod` (branch `26.2`): Minecraft 26.2, Fabric Loader 0.19.3,
Fabric API 0.158.0+26.2, Loom 1.17-SNAPSHOT, Kotlin 2.4.10. There's no mappings line, because 26.x isn't
obfuscated.

Architecture notes, conversion conventions, toolchain traps and past decisions are in `CLAUDE.md` (in
Ukrainian).
