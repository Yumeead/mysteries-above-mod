package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.effect.burst
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

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
 *
 * ## The bottle kindles before it breaks
 *
 * For half a second light gathers into the Priest's hand, and only then the water bursts out —
 * vanilla's own splash of a thrown potion, in gold. The bottle and the spirituality are spent at
 * the splash, not at the cast: a priest struck down mid-kindling has lost nothing.
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

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) return AbilityOutcome.failed("Пляшка вже розгоряється")
        if (waterSlot(context.caster) < 0) return AbilityOutcome.failed("Немає пляшки води — освячувати нічого")

        val pending = context.cast
        val messaging = context.messaging
        val beyonders = context.beyonders
        val color = context.effects.pathwayColor
        val light = ModParticles.light(color)
        val damage = damage(context.sequence)

        // The bottle kindles in the hand first; it is spent only at the splash, so a cast cut short
        // (death, leaving) costs neither the water nor the spirituality.
        context.session {
            repeat(KINDLE_TICKS / KINDLE_PERIOD_TICKS) { step ->
                val priest = owner ?: return@session
                kindle(priest, light, step)
                waitTicks(KINDLE_PERIOD_TICKS)
            }
            val priest = owner ?: return@session
            val slot = waterSlot(priest)
            if (slot < 0) {
                messaging.hint(priest, Component.literal("Пляшка води зникла з рук"), Fizzle.SPARK)
                return@session
            }
            priest.inventory.removeItem(slot, 1)
            pending.complete()
            splash(priest, color)

            val level = priest.level()
            level.getEntitiesOfClass(LivingEntity::class.java, AABB.ofSize(priest.position(), RADIUS * 2, RADIUS * 2, RADIUS * 2))
                .filter { it.position().distanceToSqr(priest.position()) <= RADIUS * RADIUS }
                .forEach { target ->
                    when {
                        target.isProfane(beyonders) -> {
                            smite(level, priest, beyonders, target, damage)
                            hiss(target)
                        }
                        // The caster is always one's own — even if they took a dark pathway themselves.
                        target === priest || target.isFaithful(beyonders) -> cleanse(target)
                    }
                }
        }
        return AbilityOutcome.Awaiting()
    }

    /** The slot with **water**, not a potion: an exact stack match filters out both potions and empty bottles. */
    private fun waterSlot(player: ServerPlayer): Int =
        player.inventory.findSlotMatchingItem(PotionContents.createItemStack(Items.POTION, Potions.WATER))

    /**
     * The bottle kindles: light gathers into the hand, a little more each step. The hand is the one
     * held out before the eyes — where the priest sees their own bottle, and the others see it raised.
     */
    private fun kindle(priest: ServerPlayer, light: ParticleOptions, step: Int) {
        val level = priest.level()
        val look = priest.lookAngle
        val right = Vec3(-look.z, 0.0, look.x).normalize()
        val hand = priest.eyePosition.add(look.scale(HELD_OUT)).add(right.scale(HELD_ASIDE)).add(0.0, -HELD_LOW, 0.0)
        val random = level.random
        repeat(KINDLE_LIGHTS + step) {
            val dir = Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize()
            val from = hand.add(dir.scale(KINDLE_RADIUS))
            // `count = 0`: an exact velocity into the hand — the light is drawn in, not scattered.
            level.sendParticles(light, from.x, from.y, from.z, 0, -dir.x, -dir.y, -dir.z, KINDLE_RADIUS / 7)
        }
        level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5f, 1.2f + step * 0.2f)
    }

    /**
     * The splash: vanilla's own burst of a thrown potion, in gold — its sound and its swirl come
     * with it — and a shell of light running out over the radius.
     */
    private fun splash(priest: ServerPlayer, color: RgbColor) {
        val level = priest.level()
        level.levelEvent(LevelEvent.PARTICLES_INSTANT_POTION_SPLASH, priest.blockPosition(), color.packed)
        burst(level, priest.position().add(0.0, SPLASH_HEIGHT, 0.0), SPLASH_START, color, SPLASH_POINTS, RADIUS / 7)
    }

    /** Holy water on the unclean: steam and a hiss, as water on a hot stone. */
    private fun hiss(target: LivingEntity) {
        val level = target.level() as? ServerLevel ?: return
        level.sendParticles(
            ParticleTypes.CLOUD, target.x, target.y + target.bbHeight * 0.6, target.z,
            STEAM, target.bbWidth * 0.4, target.bbHeight * 0.3, target.bbWidth * 0.4, 0.02,
        )
        level.playSound(null, target.x, target.y, target.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.2f)
    }

    private fun damage(sequence: Sequence): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private companion object {
        const val RADIUS = 6.0
        const val BASE_DAMAGE = 8
        const val COOLDOWN_SECONDS = 20

        const val KINDLE_TICKS = 10
        const val KINDLE_PERIOD_TICKS = 2
        const val KINDLE_LIGHTS = 3
        const val KINDLE_RADIUS = 0.5
        /** Where a bottle is held out before the eyes. */
        const val HELD_OUT = 0.6
        const val HELD_ASIDE = 0.3
        const val HELD_LOW = 0.35
        const val SPLASH_HEIGHT = 0.6
        const val SPLASH_START = 0.4
        const val SPLASH_POINTS = 36
        const val STEAM = 6
    }
}
