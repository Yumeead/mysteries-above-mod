package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.pathways.justiciar.Zone
import me.vangoo.mysteries.pathways.justiciar.rootInPlace
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

/**
 * Justiciar, Seq. 6 (Judge): **Verdict**.
 *
 * Five sentences, one gesture each — chosen by what the Judge holds, not by cycling a hidden
 * mode with sneak-and-click as the plugin did. The same fix Door already applied to its own
 * menu-substitute problem: a tool in hand instead of a state nobody else can see.
 */
class Verdict : ActiveAbility(AbilityIdentity.of("justiciar_verdict")) {

    override val displayName: Component = Component.literal("Вердикт")

    override val spiritualityCost: Int = 130

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Виносить вирок за тим, що у вас у руці:\n" +
            "▪ Перо — Вигнання: відкидає ворогів геть\n" +
            "▪ Ґрати — Обмеження: в'язниця на місці на ${RESTRICTION_TICKS / 20} с\n" +
            "▪ Ланцюг — Ув'язнення: знерухомлює ціль на ${IMPRISONMENT_TICKS / 20} с\n" +
            "▪ Чахлик — Смерть: $DEATH_DAMAGE шкоди цілі\n" +
            "▪ Тризуб — Випарення: небесна кара по цілі",
    )

    override fun cast(context: CastContext): AbilityOutcome = when (context.caster.mainHandItem.item) {
        Items.IRON_BARS -> restriction(context)
        Items.IRON_CHAIN -> imprisonment(context)
        Items.WITHER_ROSE -> death(context)
        Items.TRIDENT -> evaporation(context)
        else -> exile(context)
    }

    private fun exile(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val center = context.origin
        val targets = context.targeting.playersNear(EXILE_RADIUS)
        if (targets.isEmpty()) return AbilityOutcome.failed("Немає цілей поблизу")

        for (target in targets) {
            val direction = target.position().subtract(center).let { if (it.lengthSqr() < 1.0e-4) Vec3(0.0, 0.0, 1.0) else it.normalize() }
            target.deltaMovement = direction.scale(EXILE_DISTANCE / 10.0).add(0.0, 1.5, 0.0)
            target.hurtMarked = true
            target.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0))
            context.level.playSound(null, target.x, target.y, target.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.0f, 0.5f)
            context.messaging.actionBar(target, Component.literal("⚖ Вас вигнано!"))
        }
        context.level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 1.0, center.z, 1, 0.0, 0.0, 0.0, 0.0)
        context.level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0f, 0.5f)
        return AbilityOutcome.done("Вигнання виконано: ${targets.size} ціл(ей)")
    }

    private fun restriction(context: CastContext): AbilityOutcome {
        val zone = Zone(context.origin, context.level, RESTRICTION_RADIUS)
        val trapped = zone.playersInside().map { it.uuid }.filter { it != context.caster.uuid }.toMutableSet()

        val loop = context.scheduling.every(PUSHBACK_PERIOD_TICKS, 0) {
            zone.drawBoundary(ParticleTypes.WITCH, points = 24)
            for (player in zone.playersInside()) {
                if (player.uuid == context.caster.uuid) continue
                trapped += player.uuid
            }
            for (id in trapped.toList()) {
                val player = context.level.server.playerList.getPlayer(id) ?: continue
                if (zone.contains(player.position())) continue
                val push = zone.center.subtract(player.position()).let { if (it.lengthSqr() < 1.0e-4) Vec3(0.0, 0.0, 1.0) else it.normalize() }
                player.deltaMovement = push.scale(0.6)
                player.hurtMarked = true
                context.messaging.actionBar(player, Component.literal("⛔ Ви не можете покинути зону обмеження!"))
            }
        }
        context.scheduling.after(RESTRICTION_TICKS) {
            loop.cancel()
            trapped.forEach { id ->
                context.level.server.playerList.getPlayer(id)?.let { context.messaging.actionBar(it, Component.literal("🔓 Ви звільнені!")) }
            }
            context.level.sendParticles(ParticleTypes.PORTAL, zone.center.x, zone.center.y + 1.0, zone.center.z, 100, 5.0, 5.0, 5.0, 0.1)
        }

        context.level.playSound(null, zone.center.x, zone.center.y, zone.center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 0.8f)
        context.messaging.actionBar(context.caster, Component.literal("⚖ Зону обмеження створено! Захоплено: ${trapped.size}"))
        return AbilityOutcome.done("Зона обмеження встановлена")
    }

    private fun imprisonment(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(30.0) ?: return AbilityOutcome.failed("Наведіть на ціль")

        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, IMPRISONMENT_TICKS, 255))
        target.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, IMPRISONMENT_TICKS, 250))
        target.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, IMPRISONMENT_TICKS, 5))
        context.rootInPlace(target, IMPRISONMENT_TICKS, boundaryParticle = ParticleTypes.CRIT, boundarySound = SoundEvents.CHAIN_PLACE)

        context.messaging.actionBar(target.uuid, Component.literal("⚖ Ви ув'язнені! (${IMPRISONMENT_TICKS / 20} с)"))
        context.messaging.actionBar(context.caster, Component.literal("⚖ ${target.name.string} ув'язнено!"))
        return AbilityOutcome.done("Ув'язнення виконано")
    }

    private fun death(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(30.0) ?: return AbilityOutcome.failed("Наведіть на ціль")
        target.hurtServer(context.level, context.level.damageSources().indirectMagic(context.caster, context.caster), DEATH_DAMAGE)

        val pos = target.position()
        context.level.sendParticles(ParticleTypes.SOUL, pos.x, pos.y + 1.0, pos.z, 50, 0.3, 0.8, 0.3, 0.05)
        context.level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 2.0f, 0.5f)
        context.messaging.actionBar(context.caster, Component.literal("⚖ Вирок винесено: ${(DEATH_DAMAGE / 2).toInt()} сердець шкоди"))
        return AbilityOutcome.done("Смертний вирок виконано")
    }

    private fun evaporation(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(EVAPORATION_RADIUS) ?: return AbilityOutcome.failed("Наведіть на ціль")

        target.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 60, 0))
        target.hurtServer(context.level, context.level.damageSources().indirectMagic(context.caster, context.caster), EVAPORATION_DAMAGE)

        val pos = target.position()
        context.level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 2.0, pos.z, 40, 0.5, 1.0, 0.5, 0.1)
        context.level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 1.0, pos.z, 100, 1.0, 1.0, 1.0, 0.05)
        context.level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.0f, 1.0f)
        context.messaging.actionBar(context.caster, Component.literal("⚖ Блискавка вразила ціль!"))
        return AbilityOutcome.done("Небесна кара виконана")
    }

    private companion object {
        const val EXILE_RADIUS = 20.0
        const val EXILE_DISTANCE = 50.0
        const val RESTRICTION_RADIUS = 10.0
        const val RESTRICTION_TICKS = 400
        const val PUSHBACK_PERIOD_TICKS = 5
        const val IMPRISONMENT_TICKS = 200
        const val DEATH_DAMAGE = 16.0f
        const val EVAPORATION_RADIUS = 20.0
        const val EVAPORATION_DAMAGE = 10.0f
        const val COOLDOWN_SECONDS = 45
    }
}
