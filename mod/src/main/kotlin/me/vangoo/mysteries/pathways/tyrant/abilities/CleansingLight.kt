package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.Blocks

/**
 * Tyrant, Seq. 7 (Seafarer): **Cleansing Light**.
 *
 * A small, everyday water trick: clean water knocks the flame off the caster and anyone burning
 * nearby, and puts out loose fire around them. Deliberately modest — this is the lore's "clean
 * a surface" magic, not a combat power.
 */
class CleansingLight : ActiveAbility(AbilityIdentity.of("tyrant_cleansing_light")) {

    override val displayName: Component = Component.literal("Очищувальне Світло")

    override val spiritualityCost: Int = 15

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Чиста вода збиває полум'я з вас і всіх, хто горить поруч, і гасить вогонь у радіусі.\n" +
            "▪ Радіус ${RADIUS.toInt()} бл. — побутова магія, не для бою",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        caster.clearFire()

        val box = caster.boundingBox.inflate(RADIUS)
        for (entity in context.level.getEntitiesOfClass(LivingEntity::class.java, box) { it !== caster }) {
            if (entity.isOnFire) entity.clearFire()
        }

        val center = caster.blockPosition()
        val r = RADIUS.toInt()
        for (x in -r..r) for (y in -r..r) for (z in -r..r) {
            val pos = center.offset(x, y, z)
            if (context.level.getBlockState(pos).`is`(Blocks.FIRE)) {
                context.level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())
            }
        }

        splash(context, center)
        return AbilityOutcome.done("Очищувальне світло гасить вогонь довкола вас")
    }

    private fun splash(context: CastContext, center: BlockPos) {
        val x = center.x + 0.5
        val y = center.y + 0.5
        val z = center.z + 0.5
        context.level.sendParticles(ParticleTypes.SPLASH, x, y, z, 24, RADIUS, 1.0, RADIUS, 0.1)
        context.level.playSound(
            null, x, y, z,
            SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0f, 1.2f,
        )
    }

    private companion object {
        const val RADIUS = 4.0
        const val COOLDOWN_SECONDS = 8
    }
}
