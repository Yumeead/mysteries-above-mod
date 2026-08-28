package me.vangoo.mysteries.pathways.whitetower.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * WhiteTower, Посл. 7 (Детектив): **Реконструкція**.
 *
 * Детектив зупиняє місце події й дивиться, що тут щойно сталось. У повітрі проступає
 * схема: від кожного, хто бив, до кожного, кого били, тягнеться нитка, і по нитці
 * біжить сам удар. Знизу — протокол: хто, кого, скільки секунд тому.
 *
 * ## Чому не журнал змін блоків, як у плагіні
 *
 * Плагінна `MysticalReenactment` показувала історію поставлених і зламаних блоків за
 * останні п'ять хвилин, і платив за це весь сервер: щоб її було що показати, мод мусив
 * **постійно писати журнал** усіх змін світу, включно з чужими й тими, які ніхто ніколи
 * не запитає. Детектив там був споживачем підсистеми, яка існувала заради нього одного.
 *
 * Тут журналу немає взагалі. Гра **вже** зберігає, хто кого вдарив останнім і скільки
 * тіків тому — це ванільні `getLastHurtByMob` / `getLastHurtMob` з їхніми мітками часу,
 * потрібні мобам для агро. Детектив просто вміє це прочитати. Нуль записів, нуль
 * пам'яті, нуль впливу на тих, хто не Детектив — і сцена виходить точнішою, ніж список
 * зламаних блоків: бійка цікавіша за паркан.
 *
 * ## Позиції знімаються в мить касту
 *
 * Схема не ходить за учасниками, і це не спрощення: реконструюється **минуле**. Нитка,
 * що тягнеться за тим, хто вже втік, показувала б не те, що сталось.
 */
class CrimeReconstruction : ActiveAbility(AbilityIdentity.of("whitetower_crime_reconstruction")) {

    override val displayName: Component = Component.literal("Реконструкція")

    override val spiritualityCost: Int = 120

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Місце події проступає в повітрі: нитки показують, хто кого бив, а протокол — коли.\n" +
            "▪ Радіус: ${radius(sequence).toInt()} бл.\n" +
            "▪ Пам'ять сцени: ${WINDOW_SECONDS} с\n" +
            "▪ Схема тримається ${REPLAY_TICKS / TICKS_PER_SECOND} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val scene = context.targeting.livingNear(radius(context.sequence))
            .mapNotNull { blow(it) }
            .distinctBy { setOf(it.attacker.uuid, it.victim.uuid) }
            .sortedBy { it.ticksAgo }
            .take(MAX_LINKS)

        if (scene.isEmpty()) {
            return AbilityOutcome.failed("Сцена чиста: тут нікого не били останні $WINDOW_SECONDS с")
        }

        val mote = ModParticles.mote(context.effects.pathwayColor)
        scene.forEach { link ->
            thread(context, link, mote)
            context.messaging.tell(
                context.caster,
                Component.literal(
                    "  ${link.attacker.name.string} → ${link.victim.name.string} · " +
                        "${link.ticksAgo / TICKS_PER_SECOND} с тому",
                ),
            )
        }

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.6f,
        )
        return AbilityOutcome.done("Реконструкція: ${scene.size} слід(и) на сцені")
    }

    /**
     * Останній удар, який пам'ятає сама гра.
     *
     * Мітка часу — це `tickCount` **потерпілого** в мить удару, тож і порівнюється вона
     * з його ж лічильником, а не з годинником сервера: сутність, що прогрузилась пізніше,
     * має власний вік.
     */
    private fun blow(victim: LivingEntity): Link? {
        val attacker = victim.lastHurtByMob ?: return null
        if (attacker === victim || attacker.isRemoved) return null
        val ticksAgo = victim.tickCount - victim.lastHurtByMobTimestamp
        if (ticksAgo !in 0..(WINDOW_SECONDS * TICKS_PER_SECOND)) return null
        return Link(attacker, victim, ticksAgo)
    }

    /**
     * Нитка від нападника до потерпілого, по якій біжить сам удар.
     *
     * Голова біжить лише в один бік — інакше нитка читалась би як зв'язок, а не як
     * напрямок, а напрямок тут і є висновком.
     */
    private fun thread(context: CastContext, link: Link, mote: net.minecraft.core.particles.ParticleOptions) {
        val level = context.level
        val from = link.attacker.eyePosition
        val to = link.victim.position().add(0.0, link.victim.bbHeight * 0.5, 0.0)
        var step = 0

        lateinit var handle: TaskHandle
        handle = context.scheduling.every(PERIOD_TICKS) {
            if (step * PERIOD_TICKS >= REPLAY_TICKS) return@every handle.cancel()
            val head = (step * HEAD_SPEED) % 1.0
            repeat(DASHES) { dash ->
                val t = (head + dash.toDouble() / DASHES) % 1.0
                val point = from.lerp(to, t)
                level.sendParticles(mote, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
            }
            step++
        }

        // Кінці нитки позначені густіше: там стояли учасники, і саме туди дивиться гравець.
        mark(level, from, mote)
        mark(level, to, mote)
    }

    private fun mark(level: ServerLevel, at: Vec3, mote: net.minecraft.core.particles.ParticleOptions) {
        level.sendParticles(mote, at.x, at.y, at.z, 12, 0.25, 0.25, 0.25, 0.0)
    }

    private fun radius(sequence: Sequence): Double =
        scale(BASE_RADIUS, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private data class Link(val attacker: LivingEntity, val victim: LivingEntity, val ticksAgo: Int)

    private companion object {
        const val BASE_RADIUS = 16
        const val COOLDOWN_SECONDS = 45
        /** Скільки секунд назад ще вважається «щойно». */
        const val WINDOW_SECONDS = 60
        const val MAX_LINKS = 8

        const val REPLAY_TICKS = 6 * TICKS_PER_SECOND
        const val PERIOD_TICKS = 2
        /** Скільки нитки голова проходить за такт: повний пробіг ≈ 1,7 с. */
        const val HEAD_SPEED = 0.06
        const val DASHES = 5
    }
}
