package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.AABB
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sun, Посл. 9 (Бард): **Спів**.
 *
 * Бард співає — і поки він співає, світло збирається навколо тих, хто слухає. Пісня
 * триває [SONG_TICKS] тіків і має кінець: підсилення дістається тим, хто **достояв до
 * останньої ноти**, а не тим, хто випадково пробіг повз на початку.
 *
 * ## Чому пісня, а не миттєвий баф
 *
 * Плагінна версія була сплеском: каст — і всі в радіусі восьми блоків отримали ефект.
 * Це працює, але «Спів» там був лише назвою: нічого не співало, і слухати не було чого.
 *
 * Тут пісня — сесія на чотири секунди з висхідним рядом нот
 * (`NOTE_BLOCK_CHIME`, півтони [SCALE]), і саме тому вона має **умову**: стій поруч,
 * доки не доспівано. Бард платить не лише духовністю, а й тим, що чотири секунди
 * стоїть на місці й голосно позначає себе.
 *
 * ## Хто чує, той і отримує
 *
 * Слухачі рахуються **в мить останньої ноти**, не на початку: сесія їх не запам'ятовує
 * взагалі. Список, зібраний на старті, довелось би тримати в полі здібності — а
 * екземпляр здібності один на весь сервер, тож два барди, що заспівали одночасно,
 * писали б у той самий список.
 */
class Singing : ActiveAbility(AbilityIdentity.of("sun_singing")) {

    override val displayName: Component = Component.literal("Спів")

    override val spiritualityCost: Int = 35

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви співаєте ${SONG_TICKS / TICKS_PER_SECOND} с, і світло збирається навколо тих, хто слухає.\n" +
            "▪ Радіус: ${range(sequence).toInt()} бл.\n" +
            "▪ Підсилення на ${boonSeconds(sequence)} с: Регенерація, Опір, Сила\n" +
            "▪ Отримає лише той, хто дослухав до кінця — вас зокрема",
    )

    /** Другий спів поверх першого — це вже не спів. */
    override fun canCast(context: CastContext): Boolean = !context.sessionRunning

    override fun cast(context: CastContext): AbilityOutcome {
        val pending = context.cast
        val radius = range(context.sequence)
        val boonTicks = boonSeconds(context.sequence) * TICKS_PER_SECOND
        val mote = ModParticles.mote(context.effects.pathwayColor)
        // У сесію їде сервіс, не контекст: `Messaging` не тримає нічиєї особи.
        val messaging = context.messaging

        context.session {
            repeat(SCALE.size) { note ->
                val bard = owner ?: return@session
                sing(bard, mote, radius, note)
                waitTicks(SONG_TICKS / SCALE.size)
            }

            val bard = owner ?: return@session
            val listeners = audience(bard, radius)
            listeners.forEach { uplift(it, boonTicks) }
            pending.complete()

            chord(bard)
            messaging.actionBar(ownerId, Component.literal("Пісню дослухали: ${listeners.size}"))
        }

        return AbilityOutcome.Awaiting(Component.literal("Ви заспівали"))
    }

    /** Одна нота: звук плюс кільце порошинок, що позначає, кого пісня дістає. */
    private fun sing(bard: ServerPlayer, mote: ParticleOptions, radius: Double, note: Int) {
        val level = bard.level() as? ServerLevel ?: return
        level.playSound(
            null, bard.x, bard.y, bard.z,
            SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS,
            1.2f, pitchOf(SCALE[note]),
        )
        repeat(RING_POINTS) { i ->
            val angle = TAU * i / RING_POINTS + note * RING_TURN
            level.sendParticles(
                mote,
                bard.x + radius * cos(angle), bard.y + 0.4, bard.z + radius * sin(angle),
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }
    }

    /** Останній акорд — його чути далі за саму пісню. */
    private fun chord(bard: ServerPlayer) {
        val level = bard.level() as? ServerLevel ?: return
        level.playSound(
            null, bard.x, bard.y, bard.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.5f,
        )
    }

    /** Слухачі — гравці в радіусі, включно з самим бардом. */
    private fun audience(bard: ServerPlayer, radius: Double): List<ServerPlayer> {
        val level = bard.level() as? ServerLevel ?: return listOf(bard)
        return level.getEntitiesOfClass(
            ServerPlayer::class.java,
            AABB.ofSize(bard.position(), radius * 2, radius * 2, radius * 2),
        ).filter { it.position().distanceToSqr(bard.position()) <= radius * radius }
    }

    private fun uplift(listener: ServerPlayer, ticks: Int) {
        listener.addEffect(MobEffectInstance(MobEffects.REGENERATION, ticks, 0, false, true, true))
        listener.addEffect(MobEffectInstance(MobEffects.RESISTANCE, ticks, 0, false, true, true))
        listener.addEffect(MobEffectInstance(MobEffects.STRENGTH, ticks, 0, false, true, true))
        (listener.level() as? ServerLevel)?.sendParticles(
            net.minecraft.core.particles.ParticleTypes.NOTE,
            listener.x, listener.y + listener.bbHeight + 0.3, listener.z,
            6, 0.3, 0.1, 0.3, 1.0,
        )
    }

    private fun range(sequence: Sequence): Double =
        scale(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private fun boonSeconds(sequence: Sequence): Int =
        scale(BASE_BOON_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_RANGE = 8
        const val BASE_BOON_SECONDS = 10
        const val COOLDOWN_SECONDS = 20

        const val SONG_TICKS = 4 * TICKS_PER_SECOND
        /** Висхідний ряд у півтонах від основного тону — ванільний нотний блок бере 0..24. */
        val SCALE = intArrayOf(0, 4, 7, 11, 12, 16)
        const val RING_POINTS = 14
        const val RING_TURN = 0.11
        const val TAU = 2.0 * Math.PI
    }
}

/** Ванільна формула нотного блоку: висота = 2^((нота − 12) / 12). */
private fun pitchOf(semitone: Int): Float =
    Math.pow(2.0, (semitone - 12) / 12.0).toFloat()
