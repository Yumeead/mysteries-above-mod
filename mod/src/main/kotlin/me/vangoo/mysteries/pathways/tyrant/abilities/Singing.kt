package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.grantAttribute
import me.vangoo.mysteries.ability.modifierId
import me.vangoo.mysteries.ability.revokeAttribute
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tyrant, Seq. 5 (Ocean Songster): **Singing**.
 *
 * The Songster's voice strikes on a spiritual level — walls, distance and covered ears do not
 * save anyone, so there is deliberately no line-of-sight check. One power, three voices, a
 * sneaking cast cycles between them.
 */
class Singing : ActiveAbility(AbilityIdentity.of("tyrant_singing")) {

    override val displayName: Component = Component.literal("Спів")

    override val spiritualityCost: Int = 160

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Голос б'є духовно — крізь стіни й затулені вуха, у радіусі ${RADIUS.toInt()} бл.\n" +
            "▪ Чарівний спів: вороги марніють, ваша шкода +${(CHARM_AMPLIFY * 100 - 100).toInt()}%\n" +
            "▪ Какофонія: моби кидаються один на одного, гравці слабнуть\n" +
            "▪ Громовий Рев: усіх відкидає, моби тікають, гравців накриває пітьма\n" +
            "▪ Shift+каст — перемкнути голос",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val owner = caster.uuid

        if (caster.isShiftKeyDown) {
            val next = modes.getOrDefault(owner, Mode.CHARM).next()
            modes[owner] = next
            caster.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.4f)
            return AbilityOutcome.Awaiting(Component.literal("Голос: ${next.label}"))
        }

        val listeners = context.level.getEntitiesOfClass(LivingEntity::class.java, caster.boundingBox.inflate(RADIUS)) {
            it !== caster && it.position().distanceToSqr(caster.position()) <= RADIUS * RADIUS
        }

        when (modes.getOrDefault(owner, Mode.CHARM)) {
            Mode.CHARM -> charm(context, listeners)
            Mode.CACOPHONY -> cacophony(context, listeners)
            Mode.ROAR -> roar(context, listeners)
        }
        return AbilityOutcome.done("Ваш голос б'є духовно навколо")
    }

    private fun charm(context: CastContext, listeners: List<LivingEntity>) {
        for (target in listeners) {
            target.addEffect(MobEffectInstance(MobEffects.NAUSEA, EFFECT_TICKS, 0))
            target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, EFFECT_TICKS, 1))
        }

        val caster = context.caster
        val modifier = modifierId(identity.id, "attack_damage")
        caster.grantAttribute(Attributes.ATTACK_DAMAGE, modifier, CHARM_AMPLIFY - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
        context.scheduling.after(CHARM_AMPLIFY_TICKS) { caster.revokeAttribute(Attributes.ATTACK_DAMAGE, modifier) }

        context.effects.bodyAura(caster, EFFECT_TICKS, counterFlow = ParticleTypes.NOTE)
        val at = caster.position()
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.4f, 0.8f)
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS, SoundSource.PLAYERS, 1.2f, 1.2f)
    }

    private fun cacophony(context: CastContext, listeners: List<LivingEntity>) {
        val mobs = listeners.filterIsInstance<Mob>()
        for (target in listeners) {
            if (target !is ServerPlayer) continue
            target.addEffect(MobEffectInstance(MobEffects.NAUSEA, EFFECT_TICKS, 1))
            target.addEffect(MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS, 1))
            target.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, EFFECT_TICKS, 1))
        }
        // Sanity gives out: each mob lunges at a neighbour instead of the singer.
        mobs.forEachIndexed { i, mob -> if (mobs.size > 1) mob.target = mobs[(i + 1) % mobs.size] }

        val caster = context.caster
        val at = caster.position().add(0.0, 1.0, 0.0)
        context.level.sendParticles(ParticleTypes.ANGRY_VILLAGER, at.x, at.y, at.z, 20, 1.6, 0.6, 1.6, 0.02)
        context.effects.bodyAura(caster, EFFECT_TICKS)
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.PLAYERS, 1.6f, 0.5f)
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4f, 0.6f)
    }

    private fun roar(context: CastContext, listeners: List<LivingEntity>) {
        val caster = context.caster
        val center = caster.position()

        for (target in listeners) {
            if (target is ServerPlayer) {
                target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, EFFECT_TICKS, 1))
                target.addEffect(MobEffectInstance(MobEffects.DARKNESS, EFFECT_TICKS, 0))
            } else if (target is Mob) {
                target.target = null // awe strips the mob of its target entirely
            }

            var push = target.position().subtract(center)
            push = Vec3(push.x, 0.0, push.z)
            push = if (push.lengthSqr() < 1.0e-4) Vec3(caster.lookAngle.x, 0.0, caster.lookAngle.z) else push.normalize()
            push = push.scale(ROAR_KNOCKBACK)
            target.deltaMovement = target.deltaMovement.add(push.x, 0.35, push.z)
            target.hurtMarked = true
        }

        context.level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y + 1.0, center.z, 6, RADIUS * 0.3, 0.5, RADIUS * 0.3, 0.0)
        context.level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.8f, 0.7f)
        context.level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.4f, 1.0f)
    }

    private enum class Mode(val label: String) {
        CHARM("Чарівний спів"), CACOPHONY("Какофонія"), ROAR("Громовий Рев");

        fun next(): Mode = entries[(ordinal + 1) % entries.size]
    }

    private companion object {
        const val RADIUS = 12.0
        const val EFFECT_TICKS = 180
        const val CHARM_AMPLIFY = 1.3
        const val CHARM_AMPLIFY_TICKS = 200
        const val ROAR_KNOCKBACK = 1.3
        const val COOLDOWN_SECONDS = 40

        /** Which voice each player last picked. Not backed by any vanilla state. */
        val modes = ConcurrentHashMap<UUID, Mode>()
    }
}
