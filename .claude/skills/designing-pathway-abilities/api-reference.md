# Ability API Reference (mysteries-above)

All paths under `src/main/java/me/vangoo/`. Read a real sibling ability before writing — these are condensed pointers, not a substitute.

## Base classes — pick one (`domain/abilities/core/`)

| Base class | Use for | You implement |
|---|---|---|
| `ActiveAbility` | triggered by item/click | `performExecution(ctx)` (+ optional `preExecution`, `getSequenceCheckTarget`) |
| `PermanentPassiveAbility` | always-on buff | `onActivate`, `tick`, `onDeactivate` |
| `ToggleablePassiveAbility` | on/off toggle (no cost/cooldown) | `onEnable`, `tick`, `onDisable` |

Every ability also implements: `getName()`, `getDescription(Sequence)`, `getSpiritualityCost()`, `getCooldown(Sequence)`.
Example sources: `TheftAbility` (active, deferred + choice menu), `Telepathy` (active, sneak-monitor), `PhysicalEnhancement` (permanent passive, HP/effects), `BattleHypnotism` (delayed fire).

## Execution pipeline (`Ability.execute` is `final`)
cooldown check → `canExecute` → `preExecution` → optional sequence-resistance roll (`getSequenceCheckTarget`) → `performExecution` → `postExecution`.

- Return `AbilityResult.success()` / `.successWithMessage(s)` / `.failure(s)` / `.invalidTarget(s)` / `.insufficientResources(s)`.
- **Deferred pattern** (menus, waiting for input): return `AbilityResult.deferred(msg)` and, after the user acts, call `AbilityResourceConsumer.consumeResources(this, casterBeyonder, context)` yourself. Cooldown/cost are NOT charged on deferred results until you consume.
- Opt into resistance: override `getSequenceCheckTarget(ctx)` to return the target — stronger Beyonders may resist automatically.

## Scaling (`domain/services/SequenceScaler`)
`scaleValue(base, sequence, strategy)` (protected on `Ability`) or `SequenceScaler.calculateMultiplier(seqLevel, strategy)`.
Power grows as sequence level drops (Seq 0 strongest). Strategies:

| Strategy | %/level | Seq 0 total | Use for |
|---|---|---|---|
| `WEAK` | 5% | +45% | minor utility |
| `MODERATE` | 15% | +135% | typical duration/range |
| `STRONG` | 30% | +270% | strong effects |
| `DIVINE` | 60% | +540% | signature power (e.g. HP) |
| `PENALTY_LINEAR/SEVERE/EXTREME` | 20/50/100% | — | high-risk drawbacks |

Scale **down** cost/cooldown with sequence (cheaper/faster when stronger): `base / calculateMultiplier(...)` — see `Telepathy.getCooldown`.

## Context sub-interfaces (`ctx.xxx()`) — common methods
- `ctx.effects()` — `playSound`, `playSoundForPlayer`, `spawnParticle`, `playBeamEffect`, `playHelixEffect`, `playSphereEffect`, `playCircleEffect`, `playLineEffect`, `playWaveEffect`, `playExplosionRingEffect`, `playTrailEffect`.
- `ctx.targeting()` — `getTargetedEntity(range)`, `getTargetedPlayer(range)`, `getNearbyEntities(r)`, `getNearbyPlayers(r)`.
- `ctx.entity()` — `damage`, `heal`, `teleport`, `applyPotionEffect`, `removePotionEffect`, `consumeItem`, `giveItem`, `setVelocity`, `setSprinting`, `giveExperience`, `setHidden`.
- `ctx.messaging()` — `sendMessage(uuid, String)`, `sendMessageToActionBar(uuid, Component)`, `spawnTemporaryHologram`, `spawnFollowingHologramForPlayer`.
- `ctx.beyonder()` — `getBeyonder(uuid)`, `isBeyonder`, `updateSanityLoss(uuid, delta)`, `isAbilityActivated`.
- `ctx.playerData()` — rich read-only target intel: `isOnline`, `hasItem`, `getMainHandItem`, `getEnderChestContents`, `getMinedAmount`, `getPlayerKills`, `getBedSpawnLocation`, `getLastDeathLocation`, etc.
- `ctx.scheduling()` — `scheduleDelayed(task, ticks)`, `scheduleRepeating(task, delay, period)`, `runAsync`.
- `ctx.ui()` — `monitorSneaking(uuid, ticks, Consumer<Boolean>)`; `ctx.ui().openChoiceMenu(...)` for selectable lists.
- Caster helpers on `ctx`: `getCasterId()`, `getCasterPlayer()`, `getCasterBeyonder()`, `getCasterLocation()`, `getCasterEyeLocation()`.

## Registration (required to make it usable)
1. Put the class in `domain/pathways/<pathway>/abilities/`.
2. In `domain/pathways/<Pathway>.java` `initializeAbilities()`, add to the right sequence:
   `sequenceAbilities.put(<seqLevel>, List.of(... , new MyAbility()));`
   (merge into the existing `List.of` for that sequence; don't overwrite it.)

## Effect/juice checklist (what makes it feel good)
- Sound + particle on cast (`preExecution`), and a distinct one on hit/resolve.
- Feedback to **both** caster and target (action bar `Component` for punchy notices).
- A clear failure message for every guard (`getTargetedEntity` empty, not a Beyonder, missing items, self-target).
- Ukrainian for all player-facing strings (match siblings).
- Scale at least one dimension (duration/range/power/amplifier) by sequence.

## Build check
`mvn -q -DskipTests compile` to verify it compiles before declaring done.
