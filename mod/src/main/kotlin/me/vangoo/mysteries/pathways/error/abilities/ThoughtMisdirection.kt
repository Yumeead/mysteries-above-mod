package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.event.MobTargeting
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Inventory
import kotlin.math.cos
import kotlin.random.Random

/**
 * Error, Seq. 8 (Swindler): **Thought Misdirection**.
 *
 * A cone of false information in front of the Swindler. Creatures inside it stop seeing a target
 * in the caster and go for each other; players briefly lose track of what they are holding. Zero
 * damage — only the wrong intent is paid for.
 *
 * ## The lie holds itself
 *
 * The plugin kept the lie with a repeating task: every ten ticks it walked the list of mobs and
 * re-pointed anyone who had gone back to the caster. That is polling standing in for a moment,
 * and the moment exists — [MobTargeting]. One subscription with a term does the whole job, and
 * the mob is redirected the instant it tries to come back, not up to half a second later.
 *
 * ## Only the selected slot moves, never the items
 *
 * The scramble changes which slot is active and swaps the hands. Items are never moved between
 * slots: that is duplication surface, and a confusion power must not be able to lose anyone's
 * inventory.
 */
class ThoughtMisdirection : ActiveAbility(AbilityIdentity.of("error_thought_misdirection")) {

    override val displayName: Component = Component.literal("Підміна думок")

    override val spiritualityCost: Int = 30

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Конус хибної інформації перед вами.\n" +
            "▪ Конус ${CONE_RANGE.toInt()} бл., ${CONE_ANGLE_DEGREES.toInt()}°, " +
            "триває ${durationTicks(sequence) / TICKS_PER_SECOND} с\n" +
            "▪ Створіння б'ються між собою замість вас\n" +
            "▪ Гравці плутають, що тримають у руках\n" +
            "▪ Відкат $COOLDOWN_SECONDS с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val inCone = inCone(context)
        if (inCone.isEmpty()) return AbilityOutcome.failed("Перед вами нікого, чиї думки можна підмінити")

        val caster = context.caster
        val duration = durationTicks(context.sequence)
        val mobs = inCone.filterIsInstance<Mob>()

        mobs.forEach { mob -> mob.target = nearestOther(mobs, mob) }
        if (mobs.isNotEmpty()) {
            context.events.on<MobTargeting>(duration) { event ->
                if (event.target !== caster) return@on
                val liar = mobs.firstOrNull { it === event.mob } ?: return@on
                event.deny()
                liar.target = nearestOther(mobs, liar)
            }
        }

        inCone.filterIsInstance<ServerPlayer>()
            .filter { Random.nextDouble() < SCRAMBLE_CHANCE }
            .forEach { victim -> scramble(context, victim) }

        cone(context)
        return AbilityOutcome.done("Ви підмінили думки: ${inCone.size}")
    }

    /** Everything alive inside the cone of gaze; the caster is excluded by `targeting` itself. */
    private fun inCone(context: CastContext): List<LivingEntity> {
        val halfAngleCos = cos(Math.toRadians(CONE_ANGLE_DEGREES / 2.0))
        val look = context.look
        return context.targeting.livingNear(CONE_RANGE).filter { entity ->
            val toEntity = entity.position().subtract(context.eye)
            toEntity.lengthSqr() > 1.0E-6 && toEntity.normalize().dot(look) >= halfAngleCos
        }
    }

    /** The nearest other creature of the cone — the one who got framed. */
    private fun nearestOther(mobs: List<Mob>, self: Mob): LivingEntity? =
        mobs.filter { it !== self }.minByOrNull { it.distanceToSqr(self) }

    /**
     * Confusion in the hands: a different active slot plus a swap of main and off hand.
     *
     * The selected slot lives on the client too, so the change has to be told to it — otherwise
     * the server and the player would disagree about what is in hand.
     */
    private fun scramble(context: CastContext, victim: ServerPlayer) {
        val inventory = victim.inventory
        val slot = (inventory.selectedSlot + 1 + Random.nextInt(Inventory.getSelectionSize() - 1)) %
            Inventory.getSelectionSize()
        inventory.selectedSlot = slot
        victim.connection.send(ClientboundSetHeldSlotPacket(slot))

        val main = victim.getItemInHand(InteractionHand.MAIN_HAND)
        victim.setItemInHand(InteractionHand.MAIN_HAND, victim.getItemInHand(InteractionHand.OFF_HAND))
        victim.setItemInHand(InteractionHand.OFF_HAND, main)

        context.messaging.actionBar(victim, Component.literal("Ваші думки сплутались…"))
        context.level.playSound(
            null, victim.x, victim.y, victim.z,
            SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 2.0f,
        )
        context.level.sendParticles(
            victim, ParticleTypes.WITCH, true, true,
            victim.x, victim.eyeY, victim.z, 20, 0.5, 0.5, 0.5, 0.0,
        )
    }

    /** The lie is visible as a fan: motes spreading along the edges of the cone. */
    private fun cone(context: CastContext) {
        val mote = ModParticles.mote(context.effects.pathwayColor)
        val half = Math.toRadians(CONE_ANGLE_DEGREES / 2.0)
        var step = 0
        while (step <= CONE_ARCS) {
            val angle = -half + 2 * half * step / CONE_ARCS
            val dir = context.look.yRot(angle.toFloat())
            var distance = 1.0
            while (distance <= CONE_RANGE) {
                val point = context.eye.add(dir.scale(distance))
                context.level.sendParticles(mote, point.x, point.y, point.z, 1, 0.05, 0.05, 0.05, 0.0)
                distance += CONE_STEP
            }
            step++
        }
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0f, 1.1f,
        )
    }

    private fun durationTicks(sequence: Sequence): Int =
        scale(BASE_DURATION_TICKS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val CONE_RANGE = 10.0
        const val CONE_ANGLE_DEGREES = 90.0
        const val COOLDOWN_SECONDS = 30
        const val BASE_DURATION_TICKS = 6 * TICKS_PER_SECOND
        const val SCRAMBLE_CHANCE = 0.7
        const val CONE_ARCS = 6
        const val CONE_STEP = 1.0
    }
}
