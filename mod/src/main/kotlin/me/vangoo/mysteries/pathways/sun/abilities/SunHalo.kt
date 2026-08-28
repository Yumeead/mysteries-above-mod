package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.SessionEnd
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sun, Посл. 7 (Сонячний Первосвященник): **Німб Сонця**.
 *
 * Німб не світить рівно — він **б'є пульсом**. Кожні [PULSE_PERIOD_TICKS] тіків від
 * Первосвященника розходиться кільце, і скверна, яку воно наздогнало, дістає удар.
 *
 * ## Чим відрізняється від Благословення
 *
 * Обидві сили утримувані й обидві світять навколо, тож різницю довелось зробити
 * механічною, а не описовою:
 *
 * | | Благословення (Посл. 8) | Німб (Посл. 7) |
 * |---|---|---|
 * | кому | союзникам — зцілення | скверні — шкода |
 * | як | рівно, щосекунди потроху | пульсом раз на дві секунди, сильно |
 * | активація | нічого | миттєве очищення від дебафів |
 *
 * Рівний потік проти пульсу — це різне відчуття в бою: під Благословенням стоять, під
 * Німбом наступають між ударами кільця.
 */
class SunHalo : ActiveAbility(AbilityIdentity.of("sun_halo")) {

    override val displayName: Component = Component.literal("Німб Сонця")

    /** Каст безкоштовний: ціна — утримання, і гасити німб має бути дарма. */
    override val spiritualityCost: Int = 0

    /**
     * Ціна утримання росте з Послідовністю — і мусить, бо разом із нею росте
     * **регенерація**: 3 очки/с на Посл. 7 проти 6 на Посл. 5 (і 9 при повному
     * засвоєнні). Пласка ціна робила б німб безкоштовним нагорі.
     */
    override fun periodicCost(sequence: Sequence): Int =
        scale(BASE_PERIODIC_COST, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Німб святого світла: при активації миттєво знімає з вас усе шкідливе, далі " +
            "б'є пульсом.\n" +
            "▪ Радіус: ${range(sequence).toInt()} бл.\n" +
            "▪ Пульс раз на ${PULSE_PERIOD_TICKS / TICKS_PER_SECOND} с: ${pulseDamage(sequence)} по скверні\n" +
            "▪ Утримання: ${periodicCost(sequence)} духовності/с; повторний каст гасить",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.done("Німб згас")
        }

        // Активація очищує — це те, за що німб беруть у мить, коли вже погано.
        val cleaned = cleanse(context.caster)

        val radius = range(context.sequence)
        val hit = pulseDamage(context.sequence)
        val mote = ModParticles.mote(context.effects.pathwayColor)
        // Німб — стала фігура, тож і порошинка стала: іскра мерехтить і спливає, і кільце
        // з неї читалось хмарою. Див. `SpiritGlyphParticle`.
        val glyph = ModParticles.glyph(context.effects.pathwayColor)
        val messaging = context.messaging
        val beyonders = context.beyonders

        // Такт частий — ним живе сам німб; пульс відлічується в тактах, а утримання
        // лишається посекундним (`sustain` бере його з `upkeepPeriodTicks`).
        var beat = 0

        context.session {
            try {
                val end = sustain(periodTicks = HALO_PERIOD_TICKS, upkeep = periodicCost) { priest ->
                    halo(priest, glyph, beat)
                    beat++
                    if (beat % BEATS_PER_PULSE != 0) return@sustain

                    pulse(priest)
                    val level = priest.level()
                    level.getEntitiesOfClass(
                        LivingEntity::class.java,
                        AABB.ofSize(priest.position(), radius * 2, radius * 2, radius * 2),
                    )
                        .filter { it !== priest && it.position().distanceToSqr(priest.position()) <= radius * radius }
                        .filter { it.isProfane(beyonders) }
                        .forEach { smite(level, priest, beyonders, it, hit) }
                }
                if (end == SessionEnd.EXHAUSTED) {
                    messaging.actionBar(ownerId, Component.literal("Німб згас: духовність скінчилась"))
                }
            } finally {
                owner?.let { priest ->
                    priest.level().playSound(
                        null, priest.x, priest.y, priest.z,
                        SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7f, 1.4f,
                    )
                }
            }
        }

        return AbilityOutcome.done(
            if (cleaned > 0) "Німб спалахнув і зняв $cleaned дебаф(и)" else "Німб спалахнув",
        )
    }

    /**
     * **Німб** — кільце над головою, яке тримає форму.
     *
     * ## Чому воно розліталось угору
     *
     * Порошинка духу має від'ємну гравітацію (`SpiritMoteParticle.GRAVITY` — речовина
     * духу спливає) і живе 16–28 тіків. Кільце ж перемальовувалось щочотири тіки з
     * новим кутом і новою висотою коливання — тобто в кожну мить у повітрі висіло
     * п'ять-сім кілець, кожне під своїм кутом, на своїй висоті й уже піднявшись. Разом
     * це читалось як хмара, що зринає, а не як німб.
     *
     * Виправлено трьома речами, і всі три про **сталість**:
     *
     * 1. Порошинка народжується з невеликою швидкістю **вниз** ([HALO_SINK]), яка гасить
     *    її власне спливання. Форма `sendParticles` із `count = 0` саме для цього й
     *    існує: тоді трійка дельт — це не розкид, а **швидкість**.
     * 2. Коливання по висоті прибрано зовсім: німб висить, а не гойдається.
     * 3. Обертання вповільнено втричі, щоб за час життя порошинки кільце не встигало
     *    змазатись у кільцевий сектор.
     *
     * Точки при цьому щоразу ті самі, тож перемальовування **підсилює** те саме коло,
     * замість малювати поруч нове.
     */
    private fun halo(priest: ServerPlayer, glyph: ParticleOptions, beat: Int) {
        val level = priest.level()
        val spin = beat * HALO_SPIN
        // Позиція в пакеті — сама маківка: по ній знак знаходить власника. Де саме стати,
        // каже **зсув**, і він їде окремо, тож затримка мережі кільця не зміщує.
        val head = priest.y + priest.bbHeight

        repeat(HALO_POINTS) { i ->
            val angle = TAU * i / HALO_POINTS + spin
            level.sendParticles(
                glyph,
                priest.x, head, priest.z,
                // count = 0 — точка без розкиду; трійка дельт доїжджає незміненою і є
                // зсувом від маківки, а не швидкістю (див. `SpiritGlyphParticle`).
                0, HALO_RADIUS * cos(angle), HALO_LIFT, HALO_RADIUS * sin(angle), 1.0,
            )
        }
    }

    /**
     * Удар пульсу — **тільки дзвін**.
     *
     * Кола по землі звідси прибрано першими: той самий почерк уже має `Благословення`, і
     * дві утримувані сили Сонця виглядали однаково. Друге, ширше кільце над головою,
     * яким їх замінили, виявилось не кращим: на дзвін довкола німба спалахував другий
     * обідок, і замість удару це читалось як збій.
     *
     * Німб лишається одним кільцем **завжди**. Про удар каже звук, а про його наслідки —
     * те, що ціль втрачає здоров'я; малювати це вдруге не треба.
     */
    private fun pulse(priest: ServerPlayer) {
        priest.level().playSound(
            null, priest.x, priest.y, priest.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 0.9f,
        )
    }

    private fun range(sequence: Sequence): Double =
        scale(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private fun pulseDamage(sequence: Sequence): Int =
        scale(BASE_PULSE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private companion object {
        const val BASE_RANGE = 8
        const val BASE_PULSE_DAMAGE = 5
        const val BASE_PERIODIC_COST = 20
        const val COOLDOWN_SECONDS = 4
        const val PULSE_PERIOD_TICKS = 2 * TICKS_PER_SECOND

        /**
         * Раз на два тіки — і цього досить, відколи знак **їде на власнику**.
         *
         * Поки фігура була статичною в світі, такт доводилось тримати щотіковим, аби
         * кільце не стрибало, і це коштувало десяти частинок на тік. Тепер плавність дає
         * не частота, а те, що знак чіпляється до голови: такт лишився тільки для того,
         * щоб покоління змінювались раніше, ніж закінчиться їхнє життя.
         */
        const val HALO_PERIOD_TICKS = 2
        const val BEATS_PER_PULSE = PULSE_PERIOD_TICKS / HALO_PERIOD_TICKS

        /** Над маківкою, а не на ній: німб має висіти. */
        const val HALO_LIFT = 0.30
        /** Трохи ширший за голову, і не більше: німб, а не обруч навколо плечей. */
        const val HALO_RADIUS = 0.28
        const val HALO_POINTS = 10
        /** Повільно: за два тіки життя знака кільце не має змазатись у сектор. */
        const val HALO_SPIN = 0.03
        const val TAU = 2.0 * Math.PI
    }
}
