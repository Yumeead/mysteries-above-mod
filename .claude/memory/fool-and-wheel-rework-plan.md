---
name: fool-and-wheel-rework-plan
description: "Plan agreed 14.11.2026 for Air Bullet, R-wheel walk+pin, threads tab in the wheel, Danger Intuition arrows, Shapeshifting face registry, Fool divination rework — decisions and order"
metadata:
  node_type: memory
  type: project
  originSessionId: f34172fa-f0b6-47ef-a5df-5751ac2339c5
  modified: 2026-11-14T16:26:58.298Z
---

Plan doc «Дурень і колесо: план переробки»: https://claude.ai/artifact/4RLkvr5wxXZgEAK5xkRzKP (Claude Docs).
**Implemented 14.11.2026 on branch `feat/fool-wheel-rework`, all 7 steps committed** (eaa8dbe … 1c49759), each with a
client game test (`-Ponly=wheel|air|danger|threads|faces|divination`). Open-list answers: walking needed no mixin;
SceneColor copy works on OpenGL and Vulkan and contains entities; swap places is in the novel; the disguise skin bug was
the player-info profile id not being on the wire (see [[shapeshifting-skin-open-question]]).
Still left: Iris check of the air bullet; two-client play (player-made puppet in the wheel, a mask seen by another
client for real, divination backlash felt by a real target).

User decisions (14.11.2026):
- Air Bullet: smooth refraction (exception like the light beam), real flight ~8 bl/tick, damage on arrival, stops at walls.
  Needs a `SceneColor` copy first (spike gate); fallback = rim + wind streaks (also the Iris look).
- R wheel: walking allowed whenever the wheel is open; holding R 6 s pins it (ring fills from 1.5 s), R/Esc closes.
- Marionettes: a «Нитки» tab inside the R wheel (not a separate menu), 3/4 entity models (real player skin),
  hover = mini card + action column; ALL 12 actions incl. swap places (lore check pending), give/take item,
  gather items, speak as puppet, use block. Cutting a thread = death. G stays for pointing orders; sneak+LMB cut goes.
- Danger Intuition: projectile check at spawn with predicted trajectory, bows/crossbows/creeper/TNT count,
  pixel arrows around the crosshair with an off switch in `HudSettingsScreen` (default on).
- Shapeshifting: face registry of everyone a player has seen (line of sight ≤ 32 bl, polled 1/s, recorded for all
  players); first sighting shows the Faceless a private paper-scrap effect; gallery when cast with no target.
- Divination (Fool): screen «Питання»; coin (gold nugget), spirit pendulum (iron chain, uses the face registry),
  dream divination (bed, user said yes despite overlap with TravellersDoor); risk by Sequence gap; astrology → Door.

Order (done): wheel → SceneColor spike → Air Bullet → Danger Intuition → threads tab → face registry → divination.

**Why:** these were the user's explicit picks after two rounds of options; re-asking wastes their time.
**How to apply:** implement from the doc; check its «Відкрите» list. Related: [[marionette-control-surface]],
[[shapeshifting-skin-open-question]], [[fool-immersive-state]], [[light-shaft-beam]].
