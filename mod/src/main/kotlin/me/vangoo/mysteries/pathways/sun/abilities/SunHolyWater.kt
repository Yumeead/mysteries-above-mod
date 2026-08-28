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
 * Sun, Посл. 7 (Сонячний Первосвященник): **Свята вода Сонця**.
 *
 * Освячує пляшку води в інвентарі — і вона вивільняється сплеском екзорцизму навколо:
 * скверні шкода, союзникам — знята скверна з тіла.
 *
 * ## Єдина здібність шляху з матеріальною ціною
 *
 * Решта Сонця платить лише духовністю. Ця витрачає **пляшку води** — звичайну,
 * ванільну, ту саму, що робиться в котлі. Це навмисно: сила, яка чистить поле бою від
 * дебафів, мусить мати запас, який колись скінчиться, і поповнюється він руками, а не
 * очікуванням.
 *
 * Пляшка шукається `Inventory.findSlotMatchingItem` по **точному** стеку
 * `PotionContents.createItemStack(POTION, WATER)`: зіллям зілля не є, а вода — це вода.
 */
class SunHolyWater : ActiveAbility(AbilityIdentity.of("sun_holy_water")) {

    override val displayName: Component = Component.literal("Свята вода Сонця")

    override val spiritualityCost: Int = 60

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Освячує пляшку води з інвентаря й вивільняє сплеск екзорцизму навколо.\n" +
            "▪ Радіус: ${RADIUS.toInt()} бл.\n" +
            "▪ Скверні: ${damage(sequence)} шкоди\n" +
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
                // Кастер завжди свій — навіть якщо сам пішов темним шляхом.
                target === context.caster || target.isFaithful(context.beyonders) ->
                    cleaned += cleanse(target)
            }
        }

        return AbilityOutcome.done("Сплеск екзорцизму: $burned опалено, знято $cleaned дебаф(и)")
    }

    /** Слот із **водою**, не зіллям: точний збіг стеку відсіює й зілля, і порожні пляшки. */
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
