package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/**
 * Visionary, Seq. 5 (Dreamwalker): **Guidance**.
 *
 * A mental leash on someone asleep: for five minutes they cannot stray past ten blocks from the
 * Dreamwalker without being pulled back. Cast again from anywhere to release them early — the
 * same `sessionRunning`-first idiom `Blessing` uses, so cancelling never has to re-find a target.
 *
 * The plugin also blocked the target from entering a vehicle while leashed. There is no vehicle
 * event on the mod's own bus yet ([me.vangoo.mysteries.event.GameEvent] currently carries only
 * damage, casts and mob targeting), so that one enforcement detail is left for whenever a
 * vehicle event earns its place there — the pull itself does not need it to work.
 */
class Guidance : ActiveAbility(AbilityIdentity.of("visionary_guidance")) {

    override val displayName: Component = Component.literal("Керівництво")

    override val spiritualityCost: Int = COST

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Проникає у підсвідомість сплячого: ${DURATION_SECONDS / 60} хв ціль не відходить далі ніж на $MAX_DISTANCE бл.\n" +
            "▪ Повторний каст будь-де розриває зв'язок достроково",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.done("Ви розірвали ментальний зв'язок")
        }

        val target = context.targeting.lookingAt(CAST_RANGE) as? Player
            ?: return AbilityOutcome.failed("Ціль має бути гравцем")
        if (!target.isSleeping) return AbilityOutcome.failed("Ціль повинна спати, щоб встановити зв'язок")

        val targetId = target.uuid
        val homeDimension = target.level().dimension()

        context.messaging.actionBar(context.caster, Component.literal("Ви встановили ментальний контроль над ${target.name.string}"))
        context.messaging.actionBar(target.uuid, Component.literal("Ви відчуваєте дивний сон, ніби хтось веде вас за руку..."))
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.5f,
        )
        context.level.sendParticles(
            ParticleTypes.HAPPY_VILLAGER, target.x, target.y + 1.0, target.z, 20, 0.5, 0.5, 0.5, 0.0,
        )

        val messaging = context.messaging

        context.session {
            try {
                while (true) {
                    val caster = owner ?: return@session
                    val server = caster.level().server
                    val leashed = server.playerList.getPlayer(targetId) ?: return@session

                    if (leashed.level().dimension() != homeDimension) {
                        leashed.hurtServer(leashed.level() as net.minecraft.server.level.ServerLevel, leashed.damageSources().magic(), 2.0f)
                        messaging.actionBar(targetId, Component.literal("Зв'язок розірвано через зміну виміру!"))
                        messaging.actionBar(caster, Component.literal("Зв'язок з ціллю розірвано (інший світ)"))
                        return@session
                    }

                    val distance = leashed.position().distanceTo(caster.position())
                    if (distance > MAX_DISTANCE) restrain(messaging, leashed, caster.position(), distance)

                    if (ticksAlive >= DURATION_TICKS) {
                        messaging.actionBar(targetId, Component.literal("Ви відчуваєте полегшення. Ментальний поводок зник."))
                        messaging.actionBar(caster, Component.literal("Дія «Керівництва» завершилась."))
                        return@session
                    }
                    waitTicks(RESTRAINT_CHECK_TICKS)
                }
            } finally {
                // Nothing external to clean up: no vanilla effect was applied, only the pull itself,
                // which naturally stops once the loop does.
            }
        }

        return AbilityOutcome.done("")
    }

    private fun restrain(
        messaging: me.vangoo.mysteries.ability.context.Messaging,
        leashed: net.minecraft.server.level.ServerPlayer,
        casterPos: Vec3,
        distance: Double,
    ) {
        val direction = casterPos.subtract(leashed.position()).normalize()
        val strength = (0.5 + (distance - MAX_DISTANCE) * 0.05).coerceAtMost(2.5)
        leashed.deltaMovement = Vec3(direction.x * strength, 0.3, direction.z * strength)
        leashed.hurtMarked = true

        leashed.level().playSound(
            null, leashed.x, leashed.y, leashed.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 0.5f,
        )
        leashed.level().sendParticles(
            ParticleTypes.REVERSE_PORTAL, leashed.x, leashed.y + 1.0, leashed.z, 15, 0.5, 1.0, 0.5, 0.0,
        )
        messaging.actionBar(leashed, Component.literal("Незрима сила тягне вас назад!"))
    }

    private companion object {
        const val MAX_DISTANCE = 10
        const val DURATION_SECONDS = 300
        const val DURATION_TICKS = DURATION_SECONDS * TICKS_PER_SECOND
        const val CAST_RANGE = 10.0
        const val COST = 400
        const val COOLDOWN_SECONDS = 600
        const val RESTRAINT_CHECK_TICKS = 10
    }
}
