---
name: designing-pathway-abilities
description: Use when designing or implementing a new Beyonder ability for a Sequence of a Pathway (Error, Door, Justiciar, Visionary, WhiteTower) — making it lore-accurate, balanced, and effect-rich
---

# Designing Pathway Abilities

## Overview
Create abilities that are **lore-accurate** (true to the sequence's identity), **distinct** (not a clone of a sibling), **balanced** (scaled by sequence), and **juicy** (sound, particles, two-sided feedback). The API and code patterns are easy to copy from existing abilities — the hard part is judgment: picking the right power for the right sequence and grounding it. This skill is that judgment.

**Core principle: never invent the sequence mapping or the lore from memory. Derive both from the code and the reference.** A confident wrong guess about which sequence a power belongs to produces a lore-*inaccurate* ability that looks correct.

## Porting from the plugin (most abilities here are ports)

The rule has two halves that pull in opposite directions. Keep both.

**Copy what the ability *is*.** Pathway, sequence, place in the roster, name, lore, and the
shape of the power come from the plugin as they are — that is the project's canon, and a port
has no licence to rewrite it. Read the plugin's file before writing anything.

**Rebuild what the ability *does*.** The plugin was written against Spigot, and much of its
implementation is not design — it is a workaround for something Spigot could not do. Ask it of
every mechanic: *is this form dictated by design, or by a limitation that no longer exists here?*
Where it is the second, build the real thing. The ported ability should end up **feeling more
interesting and more grounded in the world** than the original, not merely translated into Kotlin.

**Crutches are replaced, not carried across.** A chest GUI standing in for a choice, a polling
task standing in for an event, a private ledger standing in for state the game already keeps, a
number standing in for a shape, a teleport standing in for a journey — each of those is a debt
the plugin paid, not a decision it made. Pay it off here:

| Crutch in the plugin | What it should become in the mod |
|---|---|
| menu asking the player to pick | a parameter that exists **in the world** — item in hand, gaze, altar, lodestone compass |
| tick loop polling for a moment | the event itself (`context.events`), or a vanilla hook |
| own bookkeeping of state | vanilla state the game already stores (`lastHurtByMob`, heightmaps, attributes, tags) |
| effect approximated by particles | the real mechanic — real blocks, real light, real dimension |
| number where the fiction wants a shape | the shape (a door you walk through, an altar you light, a world you cross) |

Measured examples with before/after line counts live in `CLAUDE.md` → «Шар здібностей: плагін —
це список сил, а не їхня реалізація». The cost of this rule is real and accepted: parity can no
longer be checked by diffing two files, so the roster and the names are what must match.

## The mapping trap (read this first)
`PathwayManager` stores each pathway's names in a list ordered **index 0 → 9 = Sequence 0 → 9**. The sequence level *is* the list index. Sequence 9 = weakest (first potion), Sequence 0 = strongest.

Real failure this skill exists to prevent: an agent asked for "Sequence 7 Visionary" built a **Hypnotist** ability — but in this plugin Sequence 7 is **Psychiatrist**; Hypnotist is Sequence 6. It guessed the mapping instead of reading it. The result was confidently lore-wrong.

→ **Always look up the target sequence's name in `pathways-reference.md` (and confirm against live `PathwayManager`) before designing anything.**

## Process

1. **Resolve identity.** Open `pathways-reference.md`. Find the exact name + theme for `<pathway>` at `<sequence level>`. If the user said a name ("the Hypnotist"), translate it to its level via the table — don't assume.
2. **Read the plugin's original, then ground the lore.** For a port, open the plugin's own file first (`C:\Users\yuryp\IdeaProjects\mysteries-above`) — it is the spec for *what the ability is*, and the record of what its implementation was working around (see «Porting from the plugin»). Then `Glob` `mod/src/main/kotlin/me/vangoo/mysteries/pathways/<pathway>/` and read 2–3 sibling abilities. Match their tone and Ukrainian voice. Use the reference's design hook + canonical LotM powers. If unsure of a canonical power, WebSearch the LotM wiki — don't fabricate.
3. **Check for duplication.** Confirm the power isn't already implemented in a sibling (or at a nearby sequence). If it overlaps, pick a different facet of the sequence's identity.
4. **Design spec FIRST — present to the user, do not write code yet.** One short block: name (Ukrainian), stable `identity`, sequence, base class, what it does, target/range, cost & cooldown, scaling dimension+strategy, the effect/feedback beats. Get a thumbs-up or adjust. (See "Don't skip the spec" below.)
5. **Implement** per `api-reference.md`: right base class, contexts, `SequenceScaler`, guards with clear failure messages, juice checklist.
6. **Register** in the pathway's roster map at the correct sequence (merge into the existing list — `Pathway` takes the roster through its constructor; there is no `initializeAbilities()`).
7. **Mirror into creature kits** — NOT YET APPLICABLE. Creature ability kits do not
   inherit player abilities and are balanced independently, but the creature engine is
   phase 12 and does not exist yet. When it lands, restore this step. Until then note
   COMBAT-relevant abilities so they can be mirrored in one pass later.
8. **Verify**: `./gradlew :mod:compileKotlin`. Report the result honestly.

## Balance heuristics
- Cost/cooldown scale **down** as sequence strengthens: `base / SequenceScaler.calculateMultiplier(seq, ...)`.
- Effect magnitude scales **up**: `scaleValue(base, seq, strategy)`. Signature powers → `DIVINE`/`STRONG`; utility → `WEAK`/`MODERATE`; drawbacks → `PENALTY_*`.
- Sanity-check cost/cooldown against sibling abilities at the same sequence, not against absolutes.
- Powerful control on other Beyonders → override `contestedTarget(context)` so higher sequences can resist.

## Don't skip the spec
Jumping straight to a Kotlin file is the most common failure: it bakes in a wrong sequence, a duplicate, or off-theme flavor that's expensive to unwind. The spec is one paragraph and catches all three. Present it and wait — unless the user explicitly said "just build it."

## Red flags — STOP
- "Sequence 7 is the Hypnotist" (or any sequence→name from memory) → **read the table.**
- "I'll register it later" → unusable until registered; do it.
- "Particles/sound are optional" → juice is the point; add cast + resolve effects and two-sided feedback.
- "This is basically like \<existing ability\>" → that's a duplicate; pick another facet.
- "I'll just write the class" → write the spec first and confirm.
- "The plugin does it this way, so I'll do the same" → the plugin is the **spec**, not the
  implementation. Copy what the ability *is*; rebuild what it *does* wherever Spigot's limit is
  the only reason it looked like that.
- "A menu / a polling tick / my own ledger worked there, keep it" → that is a crutch being
  carried across. Replace it with a parameter in the world, an event, or vanilla state.
- "Creature kits will pick this up automatically" → they won't. The engine is phase 12;
  until then, record combat-relevant abilities for a later mirroring pass.

## Files
- `pathways-reference.md` — authoritative sequence↔name↔theme tables for all 5 pathways.
- `api-reference.md` — base classes, the `core`/`mod` split, outcomes, contexts, scaling, sessions, registration, juice checklist, build command.
