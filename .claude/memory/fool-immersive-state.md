---
name: fool-immersive-state
description: "Fool pathway on the rendering engine (paper + threads), 14.11.2026 — what is built, what is left"
metadata:
  node_type: memory
  type: project
  originSessionId: e968f9dc-fcf4-4fa0-adfd-d6887b960c98
  modified: 2026-11-14T14:30:41.762Z
---

Branch `feat/fool-immersive`, committed 14.11.2026 in six commits starting at c55cd8d: all 16 own Fool powers speak through the world
(packets Sheet, Effigy, Thread, Spool, Face, Words, Glow; PrivateSight seam). Documented in CLAUDE.md,
section «Папір і нитки».

Left: two-client play — a puppet made from a player (conversion, possession pull, the Spool when it dies),
Shapeshifting's Face TAKE/DROP on a real disguise, someone else watching the threads. `FoolClientGameTest`
sends Spool and Face TAKE itself because one player cannot earn them.

**Why:** a single-player client test cannot reach the player-on-player paths; [[in-game-verification-with-two-clients]].
**How to apply:** before calling the Fool done, run two clients and check those paths; merge to main when the user asks
(done: engine, Seq 9–7, Marionettist, Faceless, test, docs).
Related: [[immersive-abilities-direction]], [[door-slice]], [[ability-visuals-must-not-clutter]].
