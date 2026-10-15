---
name: death-pathway-deferred
description: Death pathway (phase 08, 15.10.2026) — what the user chose and what was deliberately deferred to later phases
metadata:
  type: project
---

Death (Seq. 9–5) was ported 15.10.2026 with four user decisions:
- Spirit World creatures (Living Shadow, Death Knight…) wait for phase 12's creature engine; Internal
  Underworld occupants come from the **vanilla type** of the absorbed servant instead.
- Door to the Underworld swallows into a **real dimension** `mysteries_above:underworld` (data, like the spirit world).
- Corpse Guise shows **a real vanilla zombie shell** to others (possession's hide trick).
- Voice of the Dead's "soul gives a recipe" branch is **deferred until brewing exists**; only the living
  interrogation is ported.

**Why:** these depended on systems the mod does not have yet (creature engine, recipes, Rampage).

**How to apply:** when phase 12 or brewing lands, revisit `SpiritPact` (pact), `InternalUnderworld`
(occupant table) and `VoiceOfTheDead` (dead branch; `DeathTraces` already keeps the souls). The user
also restated 15.10.2026: never repeat the plugin's ability logic 1:1 — invent something cooler with
mod capabilities. See [[pathway-abilities-should-be-hard-and-lore-heavy]], [[spirit-world-dimension]].
