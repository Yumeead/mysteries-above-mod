package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Door, Seq. 7 (Astrologer): **Spiritual Vision**.
 *
 * Look at someone for longer than a moment and you will see how much life is in them. Look for
 * five seconds and you will see whose they are.
 *
 * ## This is not the White Tower's `EnhancedMind`
 *
 * Both powers "look and learn", but their verbs differ, and that is exactly why they coexist:
 *
 * | | Enhanced Mind (WT 9) | Spiritual Vision (Door 7) |
 * |---|---|---|
 * | what it reads | anything, blocks included | the living only |
 * | when | instantly, while sneaking | it builds up with the gaze |
 * | who sees it | the Reader alone | **the aura is visible in the world** |
 * | what it gives | health, armour, pathway | health as colour, pathway after 5 s |
 *
 * The main difference is the third row: the health aura hangs on the target, so everyone nearby
 * sees it. An Astrologer studying someone gives themselves away by it.
 *
 * ## The gaze accumulates, and the counter has to live somewhere
 *
 * There is one ability instance for the whole server, so the counter is kept in a map by owner —
 * exactly as in the plugin. But there is one map here instead of three: the plugin tracked the
 * gaze duration, "who has already been given an aura" and "whose pathway has already been named"
 * separately, and all three had to be cleaned in step. One [Gaze] record per owner is enough: a
 * change of target resets it entirely, and none of the three states can drift from the others.
 */
class SpiritualVision :
    ToggleablePassiveAbility(AbilityIdentity.of("door_spiritual_vision")),
    Ticking {

    /** Four times a second: the aura should trail rather than blink. */
    override val tickPeriodTicks: Int = 5

    override val displayName: Component = Component.literal("Духовне бачення")

    /** Whom the owner is studying right now and for how many ticks. */
    private class Gaze(val target: UUID, var ticks: Int, var named: Boolean = false)

    private val gazes = HashMap<UUID, Gaze>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Погляд, що затримався, читає живе.\n" +
            "▪ ${AURA_TICKS / TICKS_PER_SECOND} с — аура здоров'я; її бачать усі поруч\n" +
            "▪ ${REVEAL_TICKS / TICKS_PER_SECOND} с — шлях і Послідовність цілі\n" +
            "▪ Дальність ${RANGE.toInt()} бл.; відвели погляд — лічильник з нуля",
    )

    override fun onEnable(context: AbilityContext) {
        gazes.remove(context.caster.uuid)
        chime(context, SoundEvents.BEACON_ACTIVATE, 1.8f)
    }

    override fun onDisable(context: AbilityContext) {
        gazes.remove(context.caster.uuid)
        chime(context, SoundEvents.BEACON_DEACTIVATE, 1.5f)
    }

    override fun tick(context: AbilityContext) {
        val owner = context.caster.uuid
        val target = context.targeting.lookingAt(RANGE)
        if (target == null) {
            gazes.remove(owner)
            return
        }

        val gaze = gazes[owner]?.takeIf { it.target == target.uuid }
            ?: Gaze(target.uuid, 0).also { gazes[owner] = it }
        gaze.ticks += tickPeriodTicks

        if (gaze.ticks < AURA_TICKS) return
        aura(context, target)
        context.messaging.actionBar(
            context.caster,
            Component.literal("${target.name.string} · ❤ ${target.health.roundToInt()}/${target.maxHealth.roundToInt()}"),
        )

        if (gaze.ticks < REVEAL_TICKS || gaze.named) return
        gaze.named = true
        reveal(context, target)
    }

    /**
     * An aura in the colour of health rather than of the pathway — and this is the only place in
     * the mod where a shape's colour is not the pathway's. Here it **carries meaning**: green is
     * whole, red is near death. The pathway colour would say the same thing already written in
     * the HUD.
     */
    private fun aura(context: AbilityContext, target: LivingEntity) {
        val level = target.level() as? ServerLevel ?: return
        val mote = ModParticles.mote(healthColor(target))
        repeat(AURA_MOTES) {
            val angle = level.random.nextDouble() * TAU
            val radius = AURA_RADIUS + level.random.nextDouble() * AURA_SPREAD
            level.sendParticles(
                mote,
                target.x + radius * Math.cos(angle),
                target.y + level.random.nextDouble() * target.bbHeight,
                target.z + radius * Math.sin(angle),
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }
    }

    private fun reveal(context: AbilityContext, target: LivingEntity) {
        val beyonder = context.beyonders.of(target.uuid)
        val note = if (beyonder == null) {
            "✦ ${target.name.string}: жодних ознак потойбічності"
        } else {
            "✦ ${target.name.string}: ${beyonder.pathway.name} " +
                "${beyonder.sequenceLevel} (${beyonder.pathway.sequenceName(beyonder.sequenceLevel)})"
        }
        context.messaging.tell(context.caster, Component.literal(note))
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 0.8f,
        )
    }

    private fun healthColor(target: LivingEntity): RgbColor {
        val share = if (target.maxHealth <= 0f) 0f else target.health / target.maxHealth
        return when {
            share > 0.75f -> RgbColor.of(0, 255, 100)
            share > 0.50f -> RgbColor.of(150, 255, 0)
            share > 0.25f -> RgbColor.of(255, 200, 0)
            else -> RgbColor.of(255, 50, 50)
        }
    }

    private fun chime(context: AbilityContext, sound: net.minecraft.sounds.SoundEvent, pitch: Float) {
        val at = context.origin
        context.level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 0.5f, pitch)
    }

    private companion object {
        const val RANGE = 30.0
        const val AURA_TICKS = 1 * TICKS_PER_SECOND
        const val REVEAL_TICKS = 5 * TICKS_PER_SECOND
        const val AURA_MOTES = 2
        const val AURA_RADIUS = 0.25
        const val AURA_SPREAD = 0.15
        const val TAU = 2.0 * Math.PI
    }
}
