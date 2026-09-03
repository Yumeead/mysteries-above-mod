package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.core.particles.DustParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Visionary, Seq. 8 (Telepathist): **Intent Reader**.
 *
 * For twenty seconds, everyone nearby wears their intentions as a colour: red for aggression,
 * blue for watching, yellow for fleeing, white for indifference — read off which way they are
 * looking, what they are holding, and how hurt they are.
 *
 * ## Scheduled directly, no session
 *
 * The loop is a fixed twenty seconds with nothing to sustain — no upkeep, no early switch-off —
 * so it is [me.vangoo.mysteries.ability.context.Scheduling.every] cancelling itself on a plain
 * counter, not a session built for open-ended power.
 */
class IntentReader : ActiveAbility(AbilityIdentity.of("visionary_intent_reader")) {

    override val displayName: Component = Component.literal("Зчитування намірів")

    override val spiritualityCost: Int = 75

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Протягом ${DURATION_SECONDS}с показує наміри оточуючих кольоровою аурою.\n" +
            "▪ Червоний — атака, синій — спостереження, жовтий — втеча, білий — спокій\n" +
            "▪ Радіус $RADIUS бл.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        context.messaging.actionBar(
            context.caster,
            Component.literal("👁 Ви бачите справжні наміри істот (${DURATION_SECONDS}с)..."),
        )
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f,
        )

        val lastIntent = HashMap<UUID, Intent>()
        var elapsed = 0
        lateinit var handle: TaskHandle
        handle = context.scheduling.every(periodTicks = READ_PERIOD_TICKS, delayTicks = 0) {
            elapsed += READ_PERIOD_TICKS
            val caster = context.caster
            if (elapsed > DURATION_TICKS || !caster.isAlive) {
                handle.cancel()
                return@every
            }

            val nearby = context.targeting.livingNear(RADIUS)
            val seen = HashSet<UUID>()
            for (entity in nearby) {
                seen += entity.uuid
                val intent = classify(entity, caster.position())
                visualize(context, entity, intent)
                val previous = lastIntent.put(entity.uuid, intent)
                if (previous != intent) notify(context, entity, intent, previous)
            }
            lastIntent.keys.retainAll(seen)
        }

        return AbilityOutcome.done("Ви читаєте наміри оточуючих")
    }

    private fun classify(suspect: LivingEntity, casterPos: Vec3): Intent {
        val toCaster = casterPos.subtract(suspect.position()).normalize()
        val direction = suspect.lookAngle
        val dot = direction.dot(toCaster)

        if (suspect is Mob && suspect.target != null) return Intent.AGGRESSIVE
        if (suspect is Player && dot > 0.85 && isHoldingWeapon(suspect)) return Intent.AGGRESSIVE

        val lowHealth = suspect.health / suspect.maxHealth < 0.3f
        if (dot < -0.5) {
            if (suspect is Player && suspect.isSprinting) return Intent.FLEEING
            if (lowHealth) return Intent.FLEEING
        }

        if (dot > 0.7) return Intent.OBSERVING
        return Intent.NEUTRAL
    }

    private fun isHoldingWeapon(player: Player): Boolean {
        val name = player.mainHandItem.item.toString()
        return listOf("sword", "axe", "bow", "trident").any { name.contains(it, ignoreCase = true) }
    }

    private fun visualize(context: CastContext, entity: LivingEntity, intent: Intent) {
        val at = entity.eyePosition.add(0.0, 0.5, 0.0)
        context.level.sendParticles(
            DustParticleOptions(intent.color, intent.scale),
            at.x, at.y, at.z, intent.count, intent.spread, intent.spread, intent.spread, 0.0,
        )
    }

    private fun notify(context: CastContext, entity: LivingEntity, current: Intent, previous: Intent?) {
        val name = if (entity is Player) entity.name.string else entity.type.description.string
        val message = when {
            current == Intent.AGGRESSIVE ->
                Component.literal("⚠ $name готується до атаки!")
            current == Intent.FLEEING && previous == Intent.AGGRESSIVE ->
                Component.literal("⬇ $name відступає.")
            current == Intent.OBSERVING ->
                Component.literal("👀 $name спостерігає.")
            else -> return
        }
        context.messaging.actionBar(context.caster, message)
        if (current == Intent.AGGRESSIVE) {
            context.level.playSound(
                null, context.origin.x, context.origin.y, context.origin.z,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 0.5f,
            )
        }
    }

    private enum class Intent(val color: Int, val scale: Float, val count: Int, val spread: Double) {
        AGGRESSIVE(0xDC1414, 1.2f, 8, 0.25),
        OBSERVING(0x3278FF, 1.0f, 6, 0.2),
        FLEEING(0xFFDC32, 1.0f, 6, 0.25),
        NEUTRAL(0xF0F0F0, 0.8f, 4, 0.15),
    }

    private companion object {
        const val DURATION_SECONDS = 20
        const val DURATION_TICKS = DURATION_SECONDS * TICKS_PER_SECOND
        const val RADIUS = 15.0
        const val COOLDOWN_SECONDS = 30
        const val READ_PERIOD_TICKS = 10
    }
}
