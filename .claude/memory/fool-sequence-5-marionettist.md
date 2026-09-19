---
name: fool-sequence-5-marionettist
description: "What Fool's Sequence 5 (Marionettist) actually contains in the plugin, why it is the port's largest single ability, and which mod primitives already exist for it"
metadata: 
  node_type: memory
  type: project
  originSessionId: e9502c28-4c5b-41d9-90ce-ba899412a4a0
  modified: 2026-09-06T19:08:01.648Z
---

Fool Seq. 5 (Marionettist) is the last unported piece of the Fool pathway and the largest single
ability of the whole port. Three abilities in the plugin's roster:

- **`MarionettistControl`** — 1 828 LOC, larger than several entire pathways elsewhere.
- **`MarionetteSwapMenu`** — 60 LOC, a thin `ActiveAbility` that only opens a GUI listing the
  caster's live marionettes and delegates the swap back to the control object (both share one
  constructor-injected instance).
- **`ThreadSight`** — 82 LOC, a clean permanent passive: soul-particle "threads" over nearby
  entities, viewer-only, plus revealing invisible entities. Trivial to port.

**What `MarionettistControl` does:** fixation (thread-lock, ≤5 m, duration by mastery) → stupor
(Slowness X / Nausea / Fatigue) → conversion, where the target is dealt 10 000 damage, its
inventory captured, and it is respawned as a **Citizens NPC** wearing the target's captured skin
(read through **PacketEvents**) and name, inheriting max HP. "Law of Convergence": converting a
Beyonder target actually **demotes their real Sequence** — the marionette steals power for good,
not cosmetically. The owner can possess/depossess a marionette (Shift+click): position swap, full
inventory swap, appearance swap, an NBT-tagged item injected as the only way back out, plus a
second NBT item for the swap menu. A master ticker refreshes owner-only glow and runs "stranded"
bookkeeping — a marionette drifting past the control range (100–200 blocks, tiered by mastery)
starts a 10-minute death clock. Release needs a confirmation GUI.

**Why it is hard:** Citizens (NPCs) and PacketEvents (skin/profile reading) have no Fabric
equivalent at all, and the `triumphteam` GUIs would wait on phase 11. Nothing in the mod
currently models "a controllable puppet that used to be someone".

**What already exists to build on** (all written during Seq. 6–8 of this same pathway):
`Disguise` (name + skin swap through faked player-info packets — exactly the appearance half),
`SelectiveVisibility` (per-viewer entity hide/show), sessions with upkeep, `PendingChoices` as the
stand-in for menus until phase 11, and `TheftRegistry` as the precedent for a timed ledger that
takes a power away and gives it back.

**Why:** the scouting cost a full subagent pass over the plugin; the roster and the mechanics are
readable in `C:\Users\Yura\IdeaProjects\mysteries-above\src\main\java\me\vangoo\pathways\fool\`,
but the distillation is not.

**Ported 06.09.2026** (design agreed with the user over four grilling rounds). Answers: mob targets
are **seized alive**, player targets become a custom `Avatar` entity, possession is **true control**
with the body left standing, the Law of Convergence stays **permanent**, and the strand clock keeps
the plugin's 10 wall-clock minutes — but death now condenses the Characteristic over 30 s.
`MarionetteSwapMenu` deleted; `Possession` took its roster slot. Control surface:
[[marionette-control-surface]].

**How to apply:** the ported code compiles, tests green, and the client boots — but **nothing here
has been played yet**, and this is the largest untested surface in the port. See
[[in-game-verification-with-two-clients]]; the first pass should check conversion, the HUD row, the
wheel switch, possession drift and the 30-second condensation, in that order. See also
[[pathway-abilities-should-be-hard-and-lore-heavy]].
