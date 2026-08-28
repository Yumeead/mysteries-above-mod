package me.vangoo.mysteries.effect

import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Покрив на тілі: пасма, що зринають від ніг до плечей і **ходять разом із ціллю**.
 *
 * Перша фігура словника ефектів. Форма загальна — «жива речовина огортає когось», —
 * а чим саме вона є, каже колір: вода Мореплавця, світло Сонця, попіл Смерті. Тому
 * тут немає нічого водяного, крім типового [counterFlow], який кожен кличе свій.
 *
 * ## Чому не коло під ногами
 *
 * Перевірено 28.08.2026 і відкинуто: рівна геометрична фігура читається як шар поверх
 * гри, а не як частина світу. Пасмо з окремих порошинок лишається піксельним — тобто
 * своїм у грі, де все інше теж піксель.
 *
 * Порошинка тут власна ([ModParticles.SPIRIT_MOTE]), а не ванільний `DUST`: спалах на
 * початку й згасання в кінці кожна відіграє сама, тож пасмо не мусить нічого гасити
 * вручну — воно лише сіє.
 *
 * ## Чому фігура планує себе сама
 *
 * Сесія належить здібності й гасне разом із нею; покрив — ні. Тому він бере власне
 * завдання в [Scheduling] і сам себе скасовує, коли вийшов строк. Здібність за нього
 * не відповідає й лічильника тактів не бачить.
 */
fun bodyAura(
    scheduling: Scheduling,
    target: LivingEntity,
    durationTicks: Int,
    color: RgbColor,
    /** Що зривається вниз, поки пасма йдуть угору. Без зустрічного руху це обертання, не течія. */
    counterFlow: ParticleOptions? = null,
) {
    require(durationTicks > 0) { "Тривалість покриву мусить бути додатною: $durationTicks" }

    val mote = ModParticles.mote(color)
    val follow = follower(target)
    var step = 0

    lateinit var handle: TaskHandle
    handle = scheduling.every(PERIOD_TICKS) {
        if (step * PERIOD_TICKS >= durationTicks) return@every handle.cancel()
        draw(follow() ?: return@every, mote, counterFlow, step)
        step++
    }
}

/**
 * Як знаходити ціль щотакту.
 *
 * Гравця не кешувати: після смерті чи переходу між вимірами сервер створює **новий**
 * `ServerPlayer`, і покрив лишився б на об'єкті, якого вже ніхто не бачить. Решта
 * сутностей живуть тим самим об'єктом, тож їх досить питати, чи вони ще у світі.
 */
private fun follower(target: LivingEntity): () -> LivingEntity? {
    if (target !is ServerPlayer) return { target.takeIf { !it.isRemoved } }
    val id = target.uuid
    val players = target.level().server.playerList
    return { players.getPlayer(id) }
}

private fun draw(target: LivingEntity, mote: ParticleOptions, counterFlow: ParticleOptions?, step: Int) {
    val level = target.level() as? ServerLevel ?: return
    val foot = target.position()
    val height = target.bbHeight * TOP

    for (strand in 0 until STRANDS) {
        // Пасма зсунуті по фазі, тож у будь-яку мить вони на різній висоті: однакова
        // фаза дала б кільце, що стрибає вгору, тобто те саме коло, лише вертикальне.
        val climb = (step * RISE_PER_STEP + strand.toFloat() / STRANDS) % 1f
        val angle = TAU * (climb * TURNS + strand.toFloat() / STRANDS)
        val radius = RADIUS * (1f - NARROWING * climb)
        level.sendParticles(
            mote,
            foot.x + radius * cos(angle),
            foot.y + climb * height,
            foot.z + radius * sin(angle),
            1, JITTER, JITTER, JITTER, 0.0,
        )
    }

    if (counterFlow != null && step % COUNTER_EVERY_STEPS == 0) {
        val angle = TAU * (step * COUNTER_TURN)
        level.sendParticles(
            counterFlow,
            foot.x + RADIUS * cos(angle),
            foot.y + height * COUNTER_HEIGHT,
            foot.z + RADIUS * sin(angle),
            1, 0.0, 0.0, 0.0, 0.0,
        )
    }
}

private const val TAU = (2.0 * PI).toFloat()

/** Кожні два тіки: 1,5 порошинки на тік — покрив густий, але не спам. */
private const val PERIOD_TICKS = 2
private const val STRANDS = 3
/** Повний підйом — за 20 тактів, тобто дві секунди. */
private const val RISE_PER_STEP = 0.05f
private const val TURNS = 1.5f
private const val RADIUS = 0.55f
/** Догори покрив стягується до плечей, а не йде колоною. */
private const val NARROWING = 0.35f
private const val TOP = 1.05f
private const val JITTER = 0.02

private const val COUNTER_EVERY_STEPS = 5
private const val COUNTER_TURN = 0.37f
private const val COUNTER_HEIGHT = 0.85f
