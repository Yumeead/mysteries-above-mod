package me.vangoo.mysteries.effect

import me.vangoo.mysteries.modId
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.sounds.SoundEvent

/**
 * Звуки мода. Оголошення тут — і є реєстрацією, як у `ModParticles` та `ModChannels`.
 *
 * ## Чому власна подія, а не `SoundEvents.AMBIENT_CAVE`
 *
 * Ванільна подія — це не лише файл, а й гучність, висота, набір варіантів і субтитр.
 * Шепіт — це печерна атмосфера, стишена вчетверо й опущена на терцію; поклич ванільну
 * подію напряму, і гравець почує звичайну печеру, а субтитр скаже «Моторошний шум».
 * Власна подія дає моду і мікс, і власний субтитр, а `sounds.json` лишає його
 * налаштовуваним даними.
 *
 * ## Чому `sounds.json` показує на ванільні файли
 *
 * Своїх `.ogg` мод поки не несе, і це навмисно: композиція вже своя (набір варіантів,
 * гучності, висоти), а замінити джерело на власний файл — це один рядок у
 * `sounds.json` і нуль рядків коду. Та сама лінія, що вже проведена зіллям, де мод
 * бере ванільну модель і фарбує її своїм кольором.
 */
object ModSounds {

    /** Порядок ініціалізації: список мусить існувати раніше за першу подію. */
    private val declared = mutableListOf<SoundEvent>()

    /** Шепіт на низькому глузді: голоси, яких немає. */
    val WHISPER: SoundEvent = event("whisper")

    /** Серцебиття на межі: чутно власне тіло, бо більше не чутно нічого. */
    val HEARTBEAT: SoundEvent = event("heartbeat")

    /** Змушує клас ініціалізуватись до того, як реєстри закриються. */
    fun register() {
        declared.forEach { Registry.register(BuiltInRegistries.SOUND_EVENT, it.location(), it) }
    }

    private fun event(path: String): SoundEvent =
        SoundEvent.createVariableRangeEvent(modId(path)).also { declared += it }
}
