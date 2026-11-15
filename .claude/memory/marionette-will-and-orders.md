---
name: marionette-will-and-orders
description: "User decisions 15.11.2026 on what puppets do: no will of their own, the meaning of follow/come/guard/attack-my-target, weapons and kits, gather/arm removed, arrow switch to the threads ring"
metadata:
  node_type: memory
  type: project
  originSessionId: 3a97717d-b68b-4de6-8290-7794a6e8000f
  modified: 2026-11-15T11:19:36.195Z
---

Decided by the user on 15.11.2026 (implemented the same day on `feat/fool-wheel-rework`, not yet committed):

- A seized mob loses its own intelligence and stands still until ordered. Only its body's fighting stays.
- Follow: walks after the owner forever, on foot, jumping obstacles and following where the owner jumped. No teleports.
- Come to me: walks to where the owner stood at the moment of the order, then stands.
- Guard: follows; attacks only whoever attacked the owner.
- Attack my target: follows; attacks whoever the owner hits. Uses the weapons it has (best blade, bow, crossbow) and
  Beyonder powers: a mob its own body's (creature kit), a player-made puppet the kit of its pathway and Sequence,
  "as mythic creatures work".
- Gather and Arm were removed: the user saw no real use for them.
- The «Нитки» text tab is gone: an arrow card right of the ring switches rings (rest 300 ms, click or Tab).
- The ability description and the puppet card live at the left of the screen; pin time is 4 s.

**Why:** the user wants puppets to read as bodies without will that obey the Marionettist, like the novel, and the
wheel to be quicker and less wordy.

**How to apply:** do not re-add a will (wandering, own targeting) or the two removed orders without asking. Still to
verify in the game: a player-made puppet fighting with its kit (no automated test), phantoms ignore walking orders
(`ponytail:` in `PuppetGoal`). Related: [[marionette-control-surface]], [[player-text-no-dots-arrows-dashes]],
[[fool-and-wheel-rework-plan]], [[in-game-verification-with-two-clients]].
