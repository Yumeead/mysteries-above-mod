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
| `PermanentPassiveAbility(identity)` | always-on | optional `onActivate`, `onDeactivate` |
| `ToggleablePassiveAbility(identity)` | on/off switch held by the player | `onEnable`, `onDisable` |

Every ability also declares `displayName`, `description(sequence)`, and — if active — `spiritualityCost` and `cooldownSeconds(sequence)`.

**Several ways to use one power = modes, not a map in the ability.** Declare `modes(sequence)` (`AbilityMode(id, name)`, stable lowercase ids) and read the picked one with `mode(context)`; the wheel, the book and the hotkeys pick it, and `context.modes.cycle(...)` keeps crouch-and-cast as a fallback. A power that is already running and can change mode on the spot overrides `switchMode(context)` and returns `true` (see `WindFlight`). Never keep your own `HashMap<UUID, Mode>`.

**`icon` is part of the ability's shape, not decoration.** It is what the wheel, the HUD and the book draw. The default is a shard by type (`AMETHYST_SHARD` active, `ECHO_SHARD` toggle, `PRISMARINE_SHARD` passive), dyed in the pathway colour by the roster builder. Override it with a one-liner when a vanilla item *says* what the power does — `Record` is a `WRITABLE_BOOK`, `DivinationArts` an `AMETHYST_CLUSTER`. Return a fresh `ItemStack` from a getter; an icon that declares its own `DYED_COLOR` keeps it.

**A tick is a choice, not an inheritance.** Add `, Ticking` to the class header when the ability really needs a clock, and implement `tick(context: AbilityContext)` plus `tickPeriodTicks`. Ask first whether an event would do: `IllusoryScales` polled the water twice a second until it learned to wait for `IncomingDamage`; `ResistanceToHorror` still ticks, and rightly — a debuff expires on its own and there is no event to wait for.

**A ticking passive declares its period, it doesn't count one.** `tickPeriodTicks` (1 = every tick, 20 = once a second) — the ticker skips the off-beats. Never keep a `runs++ % 20` counter in a field; it desyncs on relog.

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

**`Failed(msg)` is a hint, not a notice.** The player sees a fizzle at the hand (sparks gather
and gutter out); `msg` appears under the crosshair only if they turned hints on. So write it
to explain the refusal, and make the refusal **visible in the world** where the world can show
it (holy light hits the ceiling, not "the sky is closed"). `Done(msg)` still reaches the action
bar until the pathway is reworked — new and reworked abilities return `Done()` with no text:
the effect is the message.

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
| `messaging` | `hint(player, text, fizzle)` for a power that ended or did not come; `tell` / `actionBar` only where the words **are** the power (an answer, a message). By player **or by UUID** |
| `scheduling` | `after(ticks)`, `every(ticks)`, sessions — see below |
| `events` | `on<E> { … }` — react to what happens in the world; see below |
| `effects` | shapes that last or glow: `bodyAura`, `beam` (a solid shaft of light in hazy air — width 0 is a thin ray, 1 a pillar out of the sky; it lands `BEAM_REACH_TICKS` after the call and lights the air near where it lands; a Sun shaft out of the sky comes from the sun — `sunward`, `SUN_REACH`, `lightReaches` in `HolyLight.kt`), `flare` (a real light block that dims and goes out), `flash` (whitens the screens of those who look) |

**A context service is the only way in, not the convenient way in.** If a service covers
what you need, call it — never the vanilla call underneath. Every member above already
passed the phase-03 filter (a method earns its place only by adding something over one
vanilla call), so going around one means writing that work a second time, worse. Measured
on phase 08: `Messaging` was documented as optional, and one pathway produced half a dozen
`level.server.playerList.getPlayer(id)?.sendSystemMessage(…)` — a hand-rolled copy of
`ServerMessaging.find`, one of which silently dropped the message when the *caster* logged
out. In particular: `messaging.tell/actionBar` over `player.sendSystemMessage`,
`scheduling` over your own task loop, `targeting` over your own `clip`, `cooldowns` over
your own map, `effects` over your own repeating particle task.

**Into a session or an event handler goes the service, not the context.** The context
carries one caster's identity and lives one call; the services carry nobody's and live as
long as the server. `val messaging = context.messaging` before `context.session { … }`
obeys the "don't capture the context" rule rather than dodging it — `RitualMagic.Rite`
carries `scheduling`, `beyonders` and `messaging` exactly that way.

What the rule does **not** forbid is vanilla with no service over it: `caster.health`,
`caster.addEffect(…)`, `level.sendParticles(…)`, `level.playSound(…)`,
`target.hurtServer(…)`. Don't wrap those — that is how the plugin grew
`IDataContext.getFoodLevel(UUID)`.

A one-off puff at the moment of a cast is still `level.sendParticles(…)` at the call site; a
shape gets into `effects` by lasting or by being asked for by a second pathway. For glow, sow
`ModParticles.light(color)` (additive: it whitens where it is thick) in the `count = 0` form —
the deltas then arrive as its exact velocity. The light brakes as it flies and carries about seven
times its speed, so a pulse meant to reach `r` blocks is sent at `r / 7`. Within four blocks of the
camera a light stops growing on screen, so a pulse from the caster's chest does not fill their view.

Shapes in `mysteries/effect` that are called directly (instant, or not yet asked for twice):

| shape | what |
|---|---|
| `burst(level, centre, radius, color, points, speed)` | a shell of light bursting outwards — a pulse, a flare-up on someone, a broken aura |
| `wave(level, centre, radius, color)` + `waveArrival(distance, radius)` | a ring of light running out and braking; the second says on which tick its front passes a distance, so what it touches can flare as it is touched |
| `ward(target, color)` | the target held out: a burst in the **defender's** colour plus the shield sound |
| `worldText(scheduling, level, at, text, ticks)` | words hanging in the world that fade — where a power that **knows** answers |
| `ModParticles.lightGlyph(color)` | light that **rides a player**: send it with the crown as the position and the offset in the deltas (`count = 0`), redraw every 2 ticks — a halo, a sun over the shoulder, a burning fist |

## Registration

1. Put the class in `mod/src/main/kotlin/me/vangoo/mysteries/pathways/<pathway>/abilities/`.
2. Add it to that pathway's `roster` map at the right sequence level, in
   `pathways/<pathway>/<Pathway>.kt`. `Pathway` takes its roster through the constructor —
   there is no `initializeAbilities()` to override, and the sequence names live in the same
   factory, not in a separate manager.
3. A brand-new pathway also goes into `ModPathways` — nothing else looks pathways up.
4. `ModPathwaysTest` then covers it for free: unique identities, declared type vs. base class,
   positive tick period, ten sequence names.

## Try it in game

The whole loop runs from one command (operator branches need level 2):

```
/mysteries potion Tyrant 9     # видає зілля; випий його
/mysteries abilities           # список із кулдаунами, ▶ позначає обране
/mysteries select <identity>
/mysteries cast <identity>     # або клавіша R — вона кастує обране
/mysteries mastery 100         # добити засвоєння, щоб піти на наступну Послідовність
/mysteries forget              # скинути статус Потойбічного
```

Spirituality starts at 0 and regenerates once a second (`SpiritualityCalculator.REGEN_RATES`
— 1/s at Sequence 9), so a fresh Beyonder waits before the first cast. That's the plugin's
balance, not a bug.

## Sessions (stateful abilities that live in time)

A session is a **coroutine**, not a class holding a task handle. `context.session { … }` opens one keyed by *this player + this ability*.

```kotlin
context.session {
    try {
        val end = sustain(periodTicks = 2, upkeep = periodicCost) { player ->
            trail(player)                       // owner is resolved fresh each beat
        }
        if (end == SessionEnd.EXHAUSTED) messaging.hint(ownerId, /* … */, Fizzle.EMPTY)
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

## Events (reacting to the world)

```kotlin
override fun onActivate(context: AbilityContext) {
    val owner = context.caster.uuid                 // a UUID, never the ServerPlayer

    context.events.on<IncomingDamage> { damage ->
        if (damage.victim.uuid != owner) return@on
        if (!damage.victim.isInWater) return@on
        damage.scale(1.0 - 0.20)                    // or damage.cancel()
    }
}
```

`on<E>(durationTicks = EVENT_FOREVER) { … }` returns a `Subscription` you only need if you
want to unsubscribe early (a one-shot reaction cancels itself from inside the handler).

1. **Never unsubscribe in `onDeactivate`.** The registry drops your subscriptions on
   activation, loss of the ability, logout and server stop. **Activation replaces
   subscriptions** — the same rule as "a re-cast replaces the session" — so subscribing in
   `onActivate` cannot double up after a respawn.
2. **Never capture the `ServerPlayer`.** A handler outlives the tick that made it, and death
   gives the player a new entity. Capture `caster.uuid` and read everything else off the
   event.
3. **The event is an intent, not a fact.** `IncomingDamage` fires at the top of `hurtServer`:
   armour, shields and vanilla's invulnerability *frames* have not been applied yet. Only
   outright invulnerability is filtered out for you. A hit counter will over-count rapid hits.
4. **Only add a new event type when something subscribes to it.** An event nobody listens for
   is the same empty interface the context refuses to grow. A new one costs one class in
   `mysteries/event` and one line in `GameEventBridge`.
5. **Mixins are the last resort, not the first.** Search order: a ready Fabric API hook → your
   own `Block`/`Item` → a `PassiveTicker` beat → and only then a mixin. Damage is the one
   thing that got there, because `ALLOW_DAMAGE` returns a `boolean` and cannot change the
   number. If you do write one: `@Inject` at `HEAD`/`RETURN` only, mandatory `require = 1`,
   zero logic in the body.

## Effect/juice checklist

- Sound + particle on cast, a distinct one on hit/resolve.
- Feedback to **both** caster and target — **in the world**, not the chat or the action bar
  (user's decision, 22.10.2026): the light, the sound and the target's reaction are the message.
- Heavy powers get three beats: gather (3–8 ticks, the power visibly pulls into the hand),
  release (glow peak, real light via `effects.flare`, sound), aftermath (2–3 s of residue).
  Reflex powers stay instant.
- Every guard returns `AbilityOutcome.Failed` with a hint that explains it — the player sees the
  fizzle, the words are for those who turned hints on.
- Ukrainian for every player-facing string; `Component.literal(...)`, no `§` codes.
- Scale at least one dimension by sequence.

## Build check

`./gradlew :mod:compileKotlin` (add `:core:test` if you touched the rules half, `:mod:test` if you touched the scheduler or sessions).
