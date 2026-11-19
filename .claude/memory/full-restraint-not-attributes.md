---
name: full-restraint-not-attributes
description: "Any ability that holds a target in place must use Restraints.hold (game refuses movement, jump, items, powers), never maxed potions, attributes or teleport anchors"
metadata:
  node_type: memory
  type: feedback
  originSessionId: a1f50d20-1129-4989-bcf3-303976040181
  modified: 2026-11-19T21:14:51.452Z
---

Immobilizing a target means the game refuses it movement, jumping, item use and abilities: `Restraints.hold(body, ticks)`
(`mod/src/main/kotlin/me/vangoo/mysteries/ability/Restraints.kt`). Never Slowness 255 + Jump Boost 250, never maxed
attribute modifiers, never a teleport-back anchor loop, never `cooldowns.lockAll` as a stand-in for "cannot cast".

**Why:** the user, 19.11.2026, on Brand of Restraint and the Verdict's Imprisonment: "не атрибути повишати на макс
рівень, а повне знерухомлення … Такий підхід має бути в любих подібних здібностях." Potions and anchors leak (swirls,
rubber-banding, hands still free) and are a plugin crutch.

**How to apply:** every new or reworked power that roots, binds, stuns or paralyses calls `Restraints.hold`. Plain slows
(Slowness I–V) are not restraint and stay effects. Details and the six migrated powers: `.claude/rules/ability-layer.md`
(«Повне знерухомлення»). Related: [[justiciar-effects-design]], [[pathway-abilities-should-be-hard-and-lore-heavy]].
