package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Спільний шар шляху Дверей: **сама двері**.
 *
 * Просять її двоє — `DoorOpening` (обидва кінці проходу крізь стіну) і `TravellersDoor`
 * (вхід у простір).
 *
 * ## Що з неї прибрано і чому
 *
 * Друга редакція намагалась зробити двері помітними, додаючи шари: рама в два шари
 * завглибшки, вир із трьох рукавів, ванільні `PORTAL`, що падають до центру, `END_ROD`
 * по порогу, `REVERSE_PORTAL` з-під нього. Помітною вона стала, стильною — ні:
 *
 * | що прибрано | чому |
 * |---|---|
 * | другий шар рами | дві площини одного синього читались як **дві** рами, а не як товщина |
 * | вир із трьох рукавів | у полотні крутилось те, що ні на що не вказує |
 * | `PORTAL` і `REVERSE_PORTAL` | ванільний фіолетовий не має нічого спільного з кольором шляху |
 * | `END_ROD` по порогу | білий там само: одна яскрава пляма з чужої палітри псує решту |
 *
 * Лишилась **одна арка** й **одне полотно**, і обидва — в кольорі шляху.
 *
 * ## Двері не з'являються, а **вимальовуються**
 *
 * Друга редакція масштабувала готову арку від нуля: двері виростали з підлоги, як
 * повзунок. Тут інакше — лінія **тече** від обох кутів порога вгору стійками й далі по
 * дузі, доки половинки не зійдуться на вершині. Це та сама тривалість, але читається
 * як «щось креслить двері», а не як «щось збільшується».
 *
 * Крива [progress] — `1 − (1−t)²`: початок швидкий, зустріч на вершині повільна.
 * Зачинення дзеркальне, тож двері згасають від порога, а вершина тримається довше за все.
 *
 * ## Колір — палітра, а не один синій
 *
 * `RgbColor.shades` дає п'ять світлостей **одного** відтінку, і арка розкладає їх по
 * висоті: біля порога темніше, на вершині світліше. Це не декор — це те, що робить
 * плоский контур об'ємним, і працює однаково для будь-якого з 22 шляхів.
 *
 * ## І рама, і полотно — знак, не іскра
 *
 * Обидва малює [ModParticles.SPIRIT_GLYPH]: фіксоване коротке життя, нуль фізики. Рух,
 * який видно — хвиля світлості в рамі, повільне осідання в полотні — рахується самим
 * кодом, кадр за кадром, а не фізикою частинки: `SPIRIT_MOTE` тут не годиться, бо
 * ванільний конструктор із ненульовою швидкістю домішує ±0,4 бл./тік випадкового
 * розкиду до будь-якого вектора (CLAUDE.md, пастка 6), і кероване падіння в ньому б
 * потонуло.
 *
 * ⚠ Знак чіпляється до гравця, якщо в пакеті ненульовий зсув (див. `SpiritGlyphParticle`).
 * Двері ставлять його формою `count = 1` з нульовими дельтами — це фігура світу.
 */
internal fun doorway(
    scheduling: Scheduling,
    level: ServerLevel,
    /** Точка на підлозі під дверима. */
    footing: Vec3,
    /** Горизонтальний напрямок «крізь двері»; довжина не має значення. */
    facing: Vec3,
    durationTicks: Int,
    color: RgbColor,
) {
    require(durationTicks > 0) { "Двері мусять простояти хоч тік: $durationTicks" }

    val forward = horizontal(facing)
    // Вектор уздовж полотна: рама малюється в площині, перпендикулярній до проходу.
    val across = forward.cross(UP).normalize()
    val palette = color.shades(SHADES, SHADE_SPREAD).map(ModParticles::glyph)
    // Полотно — теж знак, не іскра: власна фізика мотика домішує ±0,4 бл./тік
    // випадкового розкиду до будь-якої швидкості, яку йому дати (це і читалось як
    // «частинки вилітають» — CLAUDE.md, пастка 6), тож падіння малюється самим
    // розкладом точок у [pane], а не фізикою частинки.
    val veil = ModParticles.glyph(color.shade(VEIL_SHADE))

    val frames = max(1, durationTicks / PERIOD_TICKS)
    var step = 0

    lateinit var handle: TaskHandle
    handle = scheduling.every(PERIOD_TICKS, delayTicks = 0) {
        if (step >= frames) return@every handle.cancel()

        val drawn = progress(step, frames)
        outline(level, palette, footing, across, drawn, step)
        // М'який поріг замість різкого «є/нема»: полотно розжарюється й гасне разом
        // із контуром, а не клацає в мить, коли drawn перетнув VEIL_FROM.
        val veilStrength = ((drawn - VEIL_FROM) / (1.0 - VEIL_FROM)).coerceIn(0.0, 1.0)
        if (veilStrength > 0.0) pane(level, veil, footing, across, step, veilStrength)

        // Три ноти на все відчинення: дзвін позначає, що лінія пішла, а не супроводжує
        // кожен її крок.
        if (step < OPEN_FRAMES && step % CHIME_EVERY == 0) {
            level.playSound(
                null, footing.x, footing.y + 1.0, footing.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.45f, OPEN_PITCH + step * OPEN_PITCH_STEP,
            )
        }

        step++
    }
}

/** Горизонтальна складова напрямку; вироджений випадок дивиться на північ. */
internal fun horizontal(direction: Vec3): Vec3 = Vec3(direction.x, 0.0, direction.z).let {
    if (it.lengthSqr() < 1.0e-6) Vec3(0.0, 0.0, 1.0) else it.normalize()
}

/**
 * Скільки контуру вже накреслено: 0 — нічого, 1 — половинки зійшлись на вершині.
 *
 * `1 − (1−t)²` на відчиненні: лінія зривається з порога швидко й доводить вершину
 * повільно. На зачиненні та сама крива в зворотному напрямку, тож остання зникає
 * вершина — двері складаються туди, звідки постали.
 */
private fun progress(step: Int, frames: Int): Double {
    val left = frames - step
    val t = when {
        step < OPEN_FRAMES -> (step + 1).toDouble() / OPEN_FRAMES
        left <= CLOSE_FRAMES -> left.toDouble() / CLOSE_FRAMES
        else -> 1.0
    }.coerceIn(0.0, 1.0)
    val inverse = 1.0 - t
    return 1.0 - inverse * inverse
}

/**
 * Контур: стійки, далі дуга — обидві половинки течуть назустріч одна одній.
 *
 * Точки розкладені за **довжиною контуру**, а не за висотою: інакше дуга, де крок по
 * висоті малий, а по дузі великий, виходила б удвічі рідшою за стійки.
 *
 * Поки двері стоять відчинені, контур не застигає: повільна хвиля світлості біжить
 * уздовж лінії ([WAVE_SPEED] тримає її неспішною), зсуваючи, яку з п'яти світлостей
 * бере точка. Самі точки не рухаються — рухається лише те, наскільки вони світяться.
 */
private fun outline(
    level: ServerLevel,
    palette: List<ParticleOptions>,
    footing: Vec3,
    across: Vec3,
    drawn: Double,
    step: Int,
) {
    val jamb = SPRING
    val arc = PI * ARCH_RISE / 2.0
    val half = jamb + arc
    val reach = half * drawn

    var travelled = 0.0
    while (travelled <= reach) {
        val (side, up) = if (travelled <= jamb) {
            HALF_WIDTH to travelled
        } else {
            val angle = (travelled - jamb) / ARCH_RISE
            HALF_WIDTH * cos(angle) to SPRING + ARCH_RISE * sin(angle)
        }

        // Палітра розкладається по висоті: біля порога глибше, під вершиною світліше.
        // Хвиля домішує невеликий зсув по довжині контуру — те саме число, що й
        // висота, тільки живе.
        val wave = sin(travelled * WAVE_LENGTH - step * WAVE_SPEED) * WAVE_AMPLITUDE
        val shade = ((up / HEIGHT) * (palette.size - 1) + wave).toInt().coerceIn(palette.indices)
        val glyph = palette[shade]
        put(level, glyph, footing.add(across.scale(side)).add(0.0, up, 0.0))
        put(level, glyph, footing.add(across.scale(-side)).add(0.0, up, 0.0))
        travelled += SPACING
    }
}

/**
 * Полотно: кілька порошинок, що повільно осідають усередині рами.
 *
 * Не вир і не заповнення — саме **натяк**: рівно стільки, щоб отвір не читався порожнім.
 *
 * Малюється [ModParticles.SPIRIT_GLYPH], не [ModParticles.SPIRIT_MOTE]: ванільний
 * конструктор частинки з ненульовою швидкістю домішує ±0,4 бл./тік випадкового
 * розкиду до будь-якого вектора, який йому дати (CLAUDE.md, пастка 6) — тому мотик
 * тут не годиться, розкид на його тлі топив би будь-яке кероване падіння. Знак же
 * ставиться там, де сказано, без фізики, тож «падіння» — це сам розклад точок:
 * кожна віртуальна порошинка (індекс [i]) має власну фазу в [FALL_CYCLE] кадрів і
 * рівномірно опускається від вершини рами до порога, після чого починає знову
 * зверху — це й читається як повільне осідання, кероване рівно двома константами.
 */
private fun pane(
    level: ServerLevel,
    veil: ParticleOptions,
    footing: Vec3,
    across: Vec3,
    step: Int,
    strength: Double,
) {
    val radiusX = HALF_WIDTH * PANE_WIDTH * strength
    // Вертикальний розмах теж стягується до центру разом зі strength, а не лишається
    // на всю висоту: інакше при strength → 0 полотно ставало б тонкою вертикальною
    // рискою замість того, щоб зникнути в точку.
    val centre = HEIGHT * (PANE_TOP + PANE_BOTTOM) / 2.0
    val top = centre + (HEIGHT * PANE_TOP - centre) * strength
    val bottom = centre - (centre - HEIGHT * PANE_BOTTOM) * strength

    repeat(PANE_MOTES) { i ->
        val phase = (step + i * FALL_CYCLE / PANE_MOTES) % FALL_CYCLE
        val fall = phase.toDouble() / FALL_CYCLE
        val angle = TAU * (i + PANE_PHASE * step) / PANE_MOTES
        val radius = PANE_INNER + (1.0 - PANE_INNER) * level.random.nextDouble()
        put(
            level, veil,
            footing
                .add(across.scale(radiusX * radius * cos(angle)))
                .add(0.0, top - (top - bottom) * fall, 0.0),
        )
    }
}

private fun put(level: ServerLevel, particle: ParticleOptions, point: Vec3) {
    level.sendParticles(particle, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
}

private val UP = Vec3(0.0, 1.0, 0.0)
private const val TAU = 2.0 * PI

/** Знак живе чотири тіки; кожні два — два покоління, тож лінія не блимає на стику. */
private const val PERIOD_TICKS = 2

// ── Арка ────────────────────────────────────────────────────────────────────────
private const val HALF_WIDTH = 0.58
/** Висота, з якої починається дуга: нижче — прямі стійки. */
private const val SPRING = 1.25
private const val ARCH_RISE = 0.72
private const val HEIGHT = SPRING + ARCH_RISE
/** Крок між точками контуру: менший — рама щільніша, знаки лягають один на одного. */
private const val SPACING = 0.09
private const val SHADES = 5
private const val SHADE_SPREAD = 0.42

/** Довжина хвилі світлості вздовж контуру: скільки блоків контуру на один її період. */
private const val WAVE_LENGTH = 1.6
/** Швидкість хвилі: мала — щоб контур відчувався живим, а не миготів. */
private const val WAVE_SPEED = 0.22
/** Розмах хвилі в кроках палітри: біля одного відтінку туди-сюди. */
private const val WAVE_AMPLITUDE = 1.1

// ── Полотно ─────────────────────────────────────────────────────────────────────
private const val PANE_WIDTH = 0.72
private const val PANE_MOTES = 3
private const val PANE_INNER = 0.35
private const val PANE_PHASE = 0.37
/** Верх і низ полотна: порошинки осідають між ними, а не спалахують і гаснуть. */
private const val PANE_TOP = 0.92
private const val PANE_BOTTOM = 0.08
/** Скільки кадрів на повне осідання зверху вниз: більше — повільніше й спокійніше. */
private const val FALL_CYCLE = 40
/** Поки контур не пройшов дві третини, полотна немає: воно висіло б у порожнечі. */
private const val VEIL_FROM = 0.66
private const val VEIL_SHADE = -0.25

// ── Відчинення ──────────────────────────────────────────────────────────────────
private const val OPEN_FRAMES = 9
/** Зачинення повільніше за відчинення: різке зникнення читалось як клацання, не згасання. */
private const val CLOSE_FRAMES = 12
private const val CHIME_EVERY = 3
private const val OPEN_PITCH = 1.15f
private const val OPEN_PITCH_STEP = 0.06f
