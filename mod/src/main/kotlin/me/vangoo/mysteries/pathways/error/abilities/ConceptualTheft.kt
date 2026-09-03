package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.beyonder.TheftRegistry
import me.vangoo.mysteries.effect.ModParticles
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/**
 * The shared layer of Error's Sequence 5 — what all six conceptual thefts have in common.
 *
 * ## Why six abilities and not one with a menu
 *
 * The plugin put all of it behind one power with a six-slot chest GUI, and here that would be a
 * menu on top of a menu: the mod already has the Beyonder's wheel, slots and a HUD, so six
 * entries cost the player **fewer** clicks than one entry plus a screen. Each of them also gets
 * what a shared class could never give: its own price, its own cooldown, its own card in the HUD
 * and its own line of description.
 *
 * The plugin had the opposite economics — its ability list was itself a paginated chest — which
 * is why the menu was the right answer *there* and the wrong one here.
 *
 * ## The slot is shared even though the abilities are not
 *
 * A Dream Stealer holds **one** lasting theft at a time, across all of them and across
 * `PowerTheft` too. That rule lives in [TheftRegistry], not in any of the six: a slot enforced
 * per-ability would not be a slot.
 *
 * Instant thefts — a thought, a blow, an ideal — take no slot and are gated by their cooldown
 * alone. What is taken from them is gone the moment it is used, so there is nothing to hold.
 *
 * ## Synthetic keys for things that are not abilities
 *
 * A dream, a heartbeat, the ability to walk — none of them is an `AbilitySpec`, so the registry
 * holds them under a made-up identity. Nothing tries to give them back: the effects on the victim
 * are self-expiring by construction (potion effects, transient attribute modifiers, subscriptions
 * with a term), and the record exists purely to keep the slot honest.
 */
object ConceptualTheft {

    /** Eighty metres — the Dream Stealer's reach, as the wiki states it. */
    const val RANGE: Double = 80.0

    const val COST: Int = 70
    const val COOLDOWN_SECONDS: Int = 90

    fun key(name: String): AbilityIdentity = AbilityIdentity.of("error_conceptual_$name")

    /**
     * Whoever the thief is looking at.
     *
     * The gaze is the parameter here for the same reason it is everywhere else on this pathway:
     * it exists in the world, and the mark can see it happening.
     */
    fun mark(context: CastContext): LivingEntity? = context.targeting.lookingAt(RANGE)

    /** Refuses the cast while the single slot is taken; `null` means it is free. */
    fun slotTaken(context: CastContext): AbilityOutcome? =
        if (TheftRegistry.heldBy(context.caster.uuid) == null) {
            null
        } else {
            AbilityOutcome.failed("Ваш слот зайнятий: спершу витратьте те, що вже вкрали")
        }

    /** Claims the single slot for a lasting theft. */
    fun hold(context: CastContext, victim: LivingEntity, what: AbilityIdentity, millis: Long) {
        TheftRegistry.steal(context.caster.uuid, victim.uuid, what, millis, millis)
    }

    /** The thread along which something leaves its owner. */
    fun reach(context: CastContext, target: LivingEntity) {
        val mote = ModParticles.mote(context.effects.pathwayColor)
        val from = target.position().add(0.0, target.bbHeight * 0.6, 0.0)
        val to = context.origin.add(0.0, HAND_HEIGHT, 0.0)
        val steps = (from.distanceTo(to) / THREAD_STEP).toInt().coerceIn(1, MAX_THREAD_STEPS)
        for (i in 0..steps) {
            val point: Vec3 = from.lerp(to, i.toDouble() / steps)
            context.level.sendParticles(mote, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.0)
        }
        context.level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 0.6f, 1.3f,
        )
    }

    /** Tells the mark what left them, when they are a person who can be told. */
    fun tell(context: CastContext, target: LivingEntity, what: String) {
        if (target is ServerPlayer) context.messaging.actionBar(target, Component.literal(what))
    }

    /**
     * Hangs the mod's only sleep hook.
     *
     * A stolen dream has to stop its owner sleeping, and Fabric hands that over ready-made
     * (`EntitySleepEvents.ALLOW_SLEEPING`) — step one of the search order, no mixin. It is
     * registered once for the process rather than per cast, because a Fabric event cannot be
     * unregistered; whether it bites is decided by the registry, which is where the theft lives.
     */
    fun install() {
        EntitySleepEvents.ALLOW_SLEEPING.register { player, _ ->
            if (dreamStolenFrom(player)) Player.BedSleepingProblem.OTHER_PROBLEM else null
        }
    }

    /** Whether someone is currently holding this player's dream. */
    fun dreamStolenFrom(player: Player): Boolean =
        TheftRegistry.isSuppressed(player.uuid, DREAM)

    val DREAM: AbilityIdentity = key("dream")

    private const val HAND_HEIGHT = 1.2
    private const val THREAD_STEP = 0.5
    private const val MAX_THREAD_STEPS = 160
}
