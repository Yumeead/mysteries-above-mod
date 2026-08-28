package me.vangoo.mysteries.client.beyonder

import me.vangoo.domain.valueobjects.SanityLoss
import me.vangoo.mysteries.effect.ModSounds
import me.vangoo.mysteries.mixin.client.GameRendererInvoker
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.resources.Identifier
import net.minecraft.util.RandomSource

/**
 * Що гравець **чує й бачить**, коли розум хитається.
 *
 * ## Чому це живе на клієнті
 *
 * Шепіт і екранний ланцюг — не механіка, а сприйняття: вони нікого не б'ють і ні на
 * що не впливають. Сервер уже надіслав число (`AbilityStatePayload.sanityLoss`), і
 * більше йому тут робити нема чого — інакше кожен удар серця коштував би пакета.
 * Клієнт цокає сам, з тим самим годинником, що й кулдауни: тік, а не настінний час,
 * тож одиночна гра на паузі мовчить разом зі світом.
 *
 * ## Смуги, а не плавна крива
 *
 * `PostChain` не має ручки гучності: уніформи запечені в JSON, і плавно підкрутити
 * `Amount` під час гри нічим. Тому ланцюгів два — по одному на смугу, — і це дані,
 * не код. Звук натомість крива і є: інтервал між шепотами скорочується разом із
 * глуздом.
 */
object SanityAmbience {

    /** Скільки тіків до наступного шепоту. `0` — час говорити. */
    private var untilWhisper = 0

    /** Скільки тіків до наступного удару серця. */
    private var untilBeat = 0

    /** Ланцюг, який поставили **ми**. Чужий (крипер, павук) чіпати не можна. */
    private var applied: Identifier? = null

    private val random: RandomSource = RandomSource.create()

    fun tick() {
        val minecraft = Minecraft.getInstance()
        // Немає світу — немає ні чого хитатись, ні на чому малювати.
        val alive = minecraft.level != null && minecraft.player != null
        val scale = if (alive && ClientAbilityState.isBeyonder) ClientAbilityState.sanityLoss else SanityLoss.none()

        applyPostEffect(minecraft, chainFor(scale))
        if (minecraft.isPaused) return
        speak(minecraft, scale)
    }

    /** Вихід зі світу: ланцюг мусить зникнути разом зі станом, що його викликав. */
    fun clear() {
        applyPostEffect(Minecraft.getInstance(), null)
        untilWhisper = 0
        untilBeat = 0
    }

    // ── Екран ───────────────────────────────────────────────────────────────────

    private fun chainFor(scale: SanityLoss): Identifier? = when {
        scale.isCritical() || scale.isExtreme() -> BROKEN
        scale.isSevere() -> STRAINED
        else -> null
    }

    private fun applyPostEffect(minecraft: Minecraft, wanted: Identifier?) {
        if (wanted == applied) return

        val renderer = minecraft.gameRenderer
        if (wanted == null) {
            // Гасимо лише свій ланцюг: ванілька ставить власний, коли гравець дивиться
            // очима крипера, і зняти його було б чужою поломкою без жодної помилки в лозі.
            if (renderer.currentPostEffect() == applied) renderer.clearPostEffect()
        } else {
            (renderer as GameRendererInvoker).`mysteries_above$setPostEffect`(wanted)
        }
        applied = wanted
    }

    // ── Звук ────────────────────────────────────────────────────────────────────

    private fun speak(minecraft: Minecraft, scale: SanityLoss) {
        val whisperPeriod = whisperPeriod(scale)
        if (whisperPeriod == null) {
            untilWhisper = 0
        } else if (--untilWhisper <= 0) {
            untilWhisper = whisperPeriod / 2 + random.nextInt(whisperPeriod)
            // Локальна атмосфера, а не звук із точки: голосів немає, тож і місця в них немає.
            minecraft.soundManager.play(
                SimpleSoundInstance.forLocalAmbience(
                    ModSounds.WHISPER,
                    MIN_PITCH + random.nextFloat() * PITCH_SPREAD,
                    WHISPER_VOLUME,
                ),
            )
        }

        // Серце чути лише на самій межі — і рівним ритмом, на відміну від шепоту.
        if (!scale.isCritical() && !scale.isExtreme()) {
            untilBeat = 0
            return
        }
        if (--untilBeat <= 0) {
            untilBeat = BEAT_PERIOD_TICKS
            minecraft.soundManager.play(
                SimpleSoundInstance.forLocalAmbience(ModSounds.HEARTBEAT, 1f, HEARTBEAT_VOLUME),
            )
        }
    }

    /** Типовий інтервал між шепотами; `null` — розум ще тримається, і тиша доречна. */
    private fun whisperPeriod(scale: SanityLoss): Int? = when {
        scale.isCritical() || scale.isExtreme() -> 6 * TICKS_PER_SECOND
        scale.isSevere() -> 12 * TICKS_PER_SECOND
        scale.isSerious() -> 25 * TICKS_PER_SECOND
        else -> null
    }

    private val STRAINED: Identifier = modId("sanity_strained")
    private val BROKEN: Identifier = modId("sanity_broken")

    private const val WHISPER_VOLUME = 0.6f
    private const val HEARTBEAT_VOLUME = 0.8f
    private const val MIN_PITCH = 0.85f
    private const val PITCH_SPREAD = 0.3f

    /** Ритм на межі: приблизно 50 ударів за хвилину — повільніший за живий, і тому чужий. */
    private const val BEAT_PERIOD_TICKS = 24
}
