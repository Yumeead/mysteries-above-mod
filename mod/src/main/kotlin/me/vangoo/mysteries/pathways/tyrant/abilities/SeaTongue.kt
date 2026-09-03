package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.EntityTypeTags
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player

/**
 * Tyrant, Seq. 5 (Ocean Songster): **Sea Tongue**.
 *
 * The Songster speaks the language of the deep, and it listens: sea life nearby is strengthened
 * and turns on the chosen prey, while every enemy around glows through water and walls.
 */
class SeaTongue : ActiveAbility(AbilityIdentity.of("tyrant_sea_tongue")) {

    override val displayName: Component = Component.literal("Мова Моря")

    override val spiritualityCost: Int = 50

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви говорите мовою глибин: морська живність поблизу стає на ваш бік і кидається на вашу " +
            "ціль, а вороги поруч проступають крізь воду й стіни.\n" +
            "▪ Радіус ${radius(sequence)} бл., тривалість ${duration(sequence)} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val radius = radius(context.sequence).toDouble()
        val ticks = duration(context.sequence) * 20

        val prey = context.targeting.lookingAt(SIGHT_RANGE)
        var allies = 0

        for (entity in context.level.getEntitiesOfClass(LivingEntity::class.java, caster.boundingBox.inflate(radius)) { it !== caster }) {
            if (isAquatic(entity)) {
                entity.addEffect(MobEffectInstance(MobEffects.STRENGTH, ticks, 0))
                entity.addEffect(MobEffectInstance(MobEffects.SPEED, ticks, 1))
                if (entity is Mob) entity.target = prey
                allies++
            } else if (entity is Monster || entity is Player) {
                entity.addEffect(MobEffectInstance(MobEffects.GLOWING, ticks, 0, true, false, true))
            }
        }

        val center = caster.position()
        context.level.sendParticles(ParticleTypes.BUBBLE, center.x, center.y + 0.5, center.z, 30, radius * 0.3, 0.4, radius * 0.3, 0.05)
        context.effects.bodyAura(caster, RISE_TICKS, counterFlow = ParticleTypes.BUBBLE)
        context.level.playSound(null, center.x, center.y, center.z, SoundEvents.DOLPHIN_PLAY, SoundSource.PLAYERS, 1.4f, 0.8f)

        context.messaging.actionBar(caster.uuid, Component.literal("Глибина відгукнулась: союзників — $allies"))
        return AbilityOutcome.done("Ви говорите мовою глибин")
    }

    /** Water-dwellers plus a handful of amphibious kin the vanilla tag alone misses. */
    private fun isAquatic(entity: LivingEntity): Boolean =
        entity.type.builtInRegistryHolder().`is`(EntityTypeTags.AQUATIC) ||
            entity.type == net.minecraft.world.entity.EntityTypes.TURTLE ||
            entity.type == net.minecraft.world.entity.EntityTypes.AXOLOTL ||
            entity.type == net.minecraft.world.entity.EntityTypes.DROWNED ||
            entity.type == net.minecraft.world.entity.EntityTypes.GUARDIAN ||
            entity.type == net.minecraft.world.entity.EntityTypes.ELDER_GUARDIAN

    private fun radius(sequence: Sequence): Int = scale(BASE_RADIUS, sequence, SequenceScaler.ScalingStrategy.MODERATE)
    private fun duration(sequence: Sequence): Int = scale(BASE_DURATION_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_RADIUS = 16
        const val BASE_DURATION_SECONDS = 20
        const val SIGHT_RANGE = 25.0
        const val RISE_TICKS = 40
        const val COOLDOWN_SECONDS = 30
    }
}
