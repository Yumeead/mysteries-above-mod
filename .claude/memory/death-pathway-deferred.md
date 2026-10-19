---
name: death-pathway-deferred
description: "Death pathway (phase 08, 15.10.2026) — what was deliberately deferred; phase 12 (19.10.2026) closed the Spirit World parts, the recipe branch still waits for brewing"
metadata:
  node_type: memory
  type: project
  originSessionId: 79b66e7c-6cb8-4770-aba7-252056a6e309
  modified: 2026-10-19T20:06:21.379Z
---

Death (Seq. 9–5) was ported 15.10.2026 with four user decisions:
- Spirit World creatures (Living Shadow, Death Knight…) waited for phase 12's creature engine.
- Door to the Underworld swallows into a **real dimension** `mysteries_above:underworld` (data, like the spirit world).
- Corpse Guise shows **a real vanilla zombie shell** to others (possession's hide trick).
- Voice of the Dead's "soul gives a recipe" branch is **deferred until brewing exists**; only the living
  interrogation is ported.

**Closed in phase 12 (19.10.2026):** `SpiritPact` strikes pacts with Spirit World beings (registry tag
`#mysteries_above:spirit_world`, lore prices from `SpiritGuideLore`); `InternalUnderworld` reads an occupant by
the creature it was and releases that same creature (attachment `inner_creature`).

**Still open:** `VoiceOfTheDead` dead branch — waits for brewing (`DeathTraces` already keeps the souls).

**How to apply:** when brewing lands, revisit `VoiceOfTheDead`. The user restated 15.10.2026: never repeat the
plugin's ability logic 1:1 — invent something cooler with mod capabilities. See
[[pathway-abilities-should-be-hard-and-lore-heavy]], [[spirit-world-dimension]].
