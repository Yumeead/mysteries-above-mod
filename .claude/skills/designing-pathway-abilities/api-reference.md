# Ability API Reference (mysteries-above-mod)

Kotlin/Fabric, not the plugin's Java/Spigot. Two halves, split across the module boundary:

| Half | Where | Sees Minecraft |
|---|---|---|
| what an ability **is and costs** — `AbilitySpec` | `core/…/domain/abilities/` | no |
| what an ability **does** — `Ability` | `mod/src/main/…/mysteries/ability/` | yes |

`Ability` implements `AbilitySpec`, so the domain can hold a Beyonder's ability list without ever seeing a platform type. Read a real sibling before writing — these are pointers, not a substitute.

## Base classes — pick one (`mysteries/ability/Ability.kt`)

| Base class | Use for | You implement |
|---|---|---|
| `ActiveAbility(identity)` | triggered by the player | `cast(context: CastContext): AbilityOutcome` |
| `PermanentPassiveAbility(identity)` | always-on | `tick(context: AbilityContext)` (+ optional `onActivate`, `onDeactivate`, `tickPeriodTicks`) |
| `ToggleablePassiveAbility(identity)` | on/off switch held by the player | `onEnable`, `onDisable`, `tick` (+ `tickPeriodTicks`) |

Every ability also declares `displayName`, `description(sequence)`, and — if active — `spiritualityCost` and `cooldownSeconds(sequence)`.

**A passive declares its period, it doesn't count one.** `tickPeriodTicks` (1 = every tick, 20 = once a second) — the ticker skips the off-beats. Never keep a `runs++ % 20` counter in a field; it desyncs on relog.

**`identity` is explicit and stable.** In the plugin it was derived from the Ukrainian name; here it goes into saves and packets, so it must not travel with the text. Same identity across sequences = the stronger version replaces the weaker one on advancement.

**A toggle that must extinguish itself** (spirituality ran out, time expired) is NOT `ToggleablePassiveAbility` — the switch lives outside the ability and can't be reached from inside. Use `ActiveAbility` plus a session — the session body decides when to go out, and its `finally` removes whatever it applied.

## Execution pipeline (`AbilityExecutor`, not the base class)

```
beyonder found → cooldown check → beyonder.authorize(spec) → canCast(context)
              → contestedTarget roll → cast(context) → settle
```

The ability implements only `canCast` and `cast`. Ordering is the executor's business.

`cast`, `canCast` and `contestedTarget` receive a **`CastContext`** — `AbilityContext` plus the ticket. Passive hooks (`tick`, `onEnable`, `onDisable`, `onActivate`, `onDeactivate`) receive a plain `AbilityContext`: no ticket, because there is nothing to pay or settle.

### Outcomes

| Return | Meaning | Who pays |
|---|---|---|
| `AbilityOutcome.Done(msg?)` | the effect happened | executor settles: spirituality, mastery, cooldown, sanity roll |
| `AbilityOutcome.Awaiting(msg?)` | started, waiting on the player | nobody yet — **you** call `context.cast.complete()` when it lands |
| `AbilityOutcome.Failed(msg)` | nothing happened | nobody |

**`Awaiting` replaces the plugin's `deferred` + `AbilityResourceConsumer`.** There is nothing to remember: hold `context.cast` (the `PendingCast`, *not* the whole context — that carries one caster's identity), and call `complete()` when the effect actually fires. Never called → the cast was free. Called twice → `check()` fails inside the ticket. The invariant is a type now, not a `КРИТИЧНО` comment.

### Resistance by Sequence

Override `contestedTarget(context): LivingEntity?` to opt in. Only another Beyonder resists; ordinary mobs don't. A successful resist burns the cooldown but not the spirituality.

## Scaling (`core/…/domain/services/SequenceScaler`)

`scale(base, sequence, strategy)` — protected on `Ability`. Never write your own multipliers.

| Strategy | %/level | Seq 0 total | Use for |
|---|---|---|---|
| `WEAK` | 5% | +45% | minor utility |
| `MODERATE` | 15% | +135% | typical duration/range |
| `STRONG` | 30% | +270% | strong effects |
| `DIVINE` | 60% | +540% | signature power |
| `PENALTY_LINEAR/SEVERE/EXTREME` | 20/50/100% | — | high-risk drawbacks |

Scale cost/cooldown **down** as the sequence strengthens: `base / SequenceScaler.calculateMultiplier(seq, …)`.

## What's on the context — and what deliberately isn't

`AbilityContext` is **not** a façade over Minecraft. The caster is a live `ServerPlayer`, so `caster.health`, `caster.isInWater`, `caster.addEffect(…)`, `level.sendParticles(…)` are called **directly**. Do not add a context method that wraps a single vanilla call — that's how the plugin ended up with `IDataContext.getFoodLevel(UUID)`.

The context carries only what the mod itself owns:

| Member | Gives you |
|---|---|
| `caster`, `beyonder`, `level`, `sequence`, `origin`, `eye`, `look` | the caster, both halves |
| `ability` | the spec that is running — the key of its session and the source of its prices |
| `cast` (**`CastContext` only**) | the `PendingCast` — see `Awaiting` above |
| `beyonders` | `of(uuid)`, `isBeyonder`, `online` — other players' domain state |
| `cooldowns` | `remainingSeconds`, `set`, `clear`, `lockAll` |
| `targeting` | `livingNear(r)`, `playersNear(r)`, `lookingAt(range)`, `playerLookingAt(range)` |
| `messaging` | `tell` / `actionBar`, by player **or by UUID** (target may be offline or in another dimension) |
| `scheduling` | `after(ticks)`, `every(ticks)`, sessions — see below |

Not there yet, arriving with its own phase — don't invent a stand-in:

| | phase |
|---|---|
| `events` — domain events via mixins | 06 |
| `effects` — particle-shape vocabulary | 07 |
| `ui` — choice screens and input | 11 |
| `summons` — data-driven creatures | 12 |

Until phase 07, emit particles yourself via `level.sendParticles(…)`.

## Registration

1. Put the class in `mod/src/main/kotlin/me/vangoo/mysteries/pathways/<pathway>/`.
2. Add it to that pathway's roster map at the right sequence level. `Pathway` takes its
   roster through the constructor — there is no `initializeAbilities()` to override.

## Sessions (stateful abilities that live in time)

A session is a **coroutine**, not a class holding a task handle. `context.session { … }` opens one keyed by *this player + this ability*.

```kotlin
context.session {
    try {
        val end = sustain(periodTicks = 2, upkeep = periodicCost) { player ->
            trail(player)                       // owner is resolved fresh each beat
        }
        if (end == SessionEnd.EXHAUSTED) messaging.actionBar(ownerId, /* … */)
    } finally {
        owner?.removeEffect(MobEffects.SPEED)   // runs on every exit, cancel included
    }
}
```

`sustain(periodTicks, upkeep, upkeepPeriodTicks, durationTicks) { player -> … }` is the whole 22-session pattern in one call: it beats, holds upkeep, and returns `SessionEnd.OWNER_GONE` / `EXHAUSTED` / `EXPIRED` when the power should go out on its own.

Inside `SessionScope`: `ownerId`, `owner` (`ServerPlayer?`), `beyonder`, `ticksAlive`, `periodicCost`, `waitTicks(n)`, `payUpkeep(amount = periodicCost)`.

1. **No registry field.** `SessionHost` keys sessions by owner + ability; re-casting replaces and cancels the previous one for you.
2. **Cleanup goes in `finally`.** Cancellation (re-cast, logout, server stop) arrives as `CancellationException` and `sustain` does not return from it. Don't write teardown after the call.
3. **Never cache `owner` across beats.** Death or a dimension change gives the player a new `ServerPlayer`; ask `owner` again each time.
4. **Don't capture `AbilityContext` in a session** — it carries one caster's identity and lives one call. `PendingCast` is fine; that's what it's for.
5. Upkeep is all-or-nothing: `payUpkeep` either debits the full amount or debits nothing and returns `false`. Declare the price as `periodicCost(sequence)` on the spec.

For non-session timing use `context.scheduling.after(ticks) { … }` / `.every(ticks) { … }`. There is no `runAsync`: everything is already on the server thread, and real background work is an explicit `withContext(Dispatchers.IO)` inside a session.

## Effect/juice checklist

- Sound + particle on cast, a distinct one on hit/resolve.
- Feedback to **both** caster and target — action bar for punchy notices.
- A clear failure message for every guard, as `AbilityOutcome.Failed`.
- Ukrainian for every player-facing string; `Component.literal(...)`, no `§` codes.
- Scale at least one dimension by sequence.

## Build check

`./gradlew :mod:compileKotlin` (add `:core:test` if you touched the rules half, `:mod:test` if you touched the scheduler or sessions).
