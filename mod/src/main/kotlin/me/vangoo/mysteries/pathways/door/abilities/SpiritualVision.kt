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
 * Door, Посл. 7 (Астролог): **Духовне бачення**.
 *
 * Дивись на когось довше за мить — і побачиш, скільки в ньому життя. Дивись п'ять
 * секунд — і побачиш, чий він.
 *
 * ## Це не `EnhancedMind` Білої Вежі
 *
 * Обидві сили «дивляться й дізнаються», але дієслово в них різне, і саме тому вони
 * співіснують:
 *
 * | | Загострений розум (WT 9) | Духовне бачення (Door 7) |
 * |---|---|---|
 * | що читає | будь-що, включно з блоками | лише живих |
 * | коли | миттєво, під присідання | накопичується поглядом |
 * | хто бачить | лише сам Читач | **аура видима у світі** |
 * | що дає | життя, броня, шлях | життя кольором, шлях за 5 с |
 *
 * Головна різниця — третій рядок: аура здоров'я висить на цілі, тож поруч її бачать усі.
 * Астролог, який когось розглядає, себе цим і видає.
 *
 * ## Погляд накопичується, і лічильник мусить десь жити
 *
 * Екземпляр здібності один на весь сервер, тож лічильник тримається мапою за власником —
 * рівно як у плагіні. Але мап тут одна замість трьох: плагін вів окремо тривалість
 * погляду, «кому вже намальовано ауру» і «чий шлях уже названо», і всі три доводилось
 * чистити узгоджено. Одного запису [Gaze] на власника досить: зміна цілі скидає його
 * цілком, і жоден із трьох станів не може розійтись із рештою.
 */
class SpiritualVision :
    ToggleablePassiveAbility(AbilityIdentity.of("door_spiritual_vision")),
    Ticking {

    /** Чотири рази на секунду: аура має тягнутись, а не блимати. */
    override val tickPeriodTicks: Int = 5

    override val displayName: Component = Component.literal("Духовне бачення")

    /** Кого зараз розглядає власник і скільки тіків. */
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
     * Аура кольору здоров'я, а не шляху — і це єдине місце в моді, де колір фігури не
     * шляховий. Тут він **несе значення**: зелене — ціле, червоне — при смерті. Колір
     * шляху сказав би те саме, що вже написано в HUD.
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
