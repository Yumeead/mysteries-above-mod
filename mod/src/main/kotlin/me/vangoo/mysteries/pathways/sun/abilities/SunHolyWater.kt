package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sun, Seq. 7 (Solar High Priest): **Holy Water of the Sun**.
 *
 * Consecrates a bottle of water in the inventory — and it bursts out as a splash around: damage
 * to the unclean, and everything harmful lifted off allies.
 *
 * ## The pathway's only ability with a material price
 *
 * The rest of the Sun pays only in spirituality. This one spends a **bottle of water** — an
 * ordinary, vanilla one, the same as is made in a cauldron. That is deliberate: a power that
 * clears a battlefield of debuffs must have a supply that will run out one day, and it is
 * replenished by hand rather than by waiting.
 *
 * The bottle is found with `Inventory.findSlotMatchingItem` against the **exact** stack
 * `PotionContents.createItemStack(POTION, WATER)`: a potion is not a potion here, and water is
 * water.
 */
class SunHolyWater : ActiveAbility(AbilityIdentity.of("sun_holy_water")) {

    override val displayName: Component = Component.literal("Свята вода Сонця")

    override val spiritualityCost: Int = 60

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Освячує пляшку води з інвентаря — і б’є сплеском святої води навколо себе.\n" +
            "▪ Радіус: ${RADIUS.toInt()} бл.\n" +
            "▪ Нечисті: ${damage(sequence)} шкоди\n" +
            "▪ Решті: знімає всі шкідливі ефекти\n" +
            "▪ Ціна: одна пляшка води",
    )

    override fun canCast(context: CastContext): Boolean = waterSlot(context) >= 0

    override fun cast(context: CastContext): AbilityOutcome {
        val slot = waterSlot(context)
        if (slot < 0) return AbilityOutcome.failed("Немає пляшки води — освячувати нічого")
        context.caster.inventory.removeItem(slot, 1)

        splash(context)

        var burned = 0
        var cleaned = 0
        context.targeting.livingNear(RADIUS).forEach { target ->
            when {
                target.isProfane(context.beyonders) -> {
                    smite(context.level, context.caster, context.beyonders, target, damage(context.sequence))
                    burned++
                }
                // The caster is always one's own — even if they took a dark pathway themselves.
                target === context.caster || target.isFaithful(context.beyonders) ->
                    cleaned += cleanse(target)
            }
        }

        return AbilityOutcome.done("Свята вода: опалено $burned, знято шкідливих ефектів: $cleaned")
    }

    /** The slot with **water**, not a potion: an exact stack match filters out both potions and empty bottles. */
    private fun waterSlot(context: CastContext): Int =
        context.caster.inventory.findSlotMatchingItem(
            PotionContents.createItemStack(Items.POTION, Potions.WATER),
        )

    private fun splash(context: CastContext) {
        val mote = ModParticles.mote(context.effects.pathwayColor)
        val at = context.origin
        for (ring in 1..RINGS) {
            val r = RADIUS * ring / RINGS
            repeat(RING_POINTS) { i ->
                val angle = TAU * i / RING_POINTS + ring * 0.3
                context.level.sendParticles(
                    mote,
                    at.x + r * cos(angle), at.y + 0.4, at.z + r * sin(angle),
                    1, 0.0, 0.0, 0.0, 0.0,
                )
            }
        }
        context.level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 1.0, at.z, 40, 0.8, 0.5, 0.8, 0.1)
        context.level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.2f, 1.4f,
        )
    }

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private companion object {
        const val RADIUS = 6.0
        const val BASE_DAMAGE = 8
        const val COOLDOWN_SECONDS = 20
        const val RINGS = 3
        const val RING_POINTS = 20
        const val TAU = 2.0 * Math.PI
    }
}
