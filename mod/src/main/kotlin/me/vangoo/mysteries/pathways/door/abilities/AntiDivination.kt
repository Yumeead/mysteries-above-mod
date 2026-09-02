package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.effect.ModParticles
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * Door, Посл. 7 (Астролог): **Протидія ворожінню**.
 *
 * Єдина сила шляху, яка сама не робить нічого. Її ефект живе в чужому касті: поки щит
 * увімкнено, [DivinationArts] проти цього гравця кидає кубик замість того, щоб просто
 * спрацювати.
 *
 * ## Чому щит — це тогл, а не постійна пасивка
 *
 * Ворожіння відчувається. Астролог, який тримає щит увімкненим цілодобово, помітний
 * рівно тим, що об нього спотикаються, — тож вибір «зараз ховаюсь» проти «зараз мене
 * можна знайти» і є грою. Постійний щит зняв би цей вибір і не дав би натомість нічого.
 *
 * ## Чому тут немає такту
 *
 * Плагінна версія цокала й сіяла по дві частинки раз на дві секунди — тобто платила
 * тіком за те, щоб гравець пам'ятав, що щит увімкнено. Про це вже каже HUD: тогл видно
 * у списку здібностей. Іскри лишились там, де вони щось означають, — у мить вмикання й
 * вимикання.
 *
 * ## Чому ідентичність у `companion`
 *
 * `DivinationArts` мусить спитати про цей щит **чужого** гравця, тобто назвати його на
 * ім'я. У плагіні цієї константи не було, і роль виконували чотири рядки-здогадки поспіль
 * (`"Anti Divination"`, `"Anti-Divination"`, `"anti_divination"`, `"AntiDivination"`),
 * з яких три завжди були хибними.
 */
class AntiDivination : ToggleablePassiveAbility(IDENTITY) {

    override val displayName: Component = Component.literal("Протидія ворожінню")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Чуже ворожіння вас не бачить.\n" +
            "▪ Поки увімкнено, спроба поворожити на вас має шанс провалитись\n" +
            "▪ Шанс тим більший, чим вища ваша Послідовність проти ворожбита\n" +
            "▪ Працює з Посл. $UNLOCK_SEQUENCE і вище",
    )

    override fun onEnable(context: AbilityContext) {
        context.messaging.actionBar(context.caster, Component.literal("Ви захищені від чужого ворожіння"))
        veil(context, SoundEvents.BEACON_ACTIVATE, 1.8f)
    }

    override fun onDisable(context: AbilityContext) {
        context.messaging.actionBar(context.caster, Component.literal("Ви знову вразливі до ворожіння"))
        veil(context, SoundEvents.BEACON_DEACTIVATE, 0.8f)
    }

    private fun veil(context: AbilityContext, sound: net.minecraft.sounds.SoundEvent, pitch: Float) {
        val at = context.origin
        context.level.sendParticles(
            ModParticles.mote(context.effects.pathwayColor),
            at.x, at.y + 1.2, at.z,
            30, 0.4, 0.6, 0.4, 0.0,
        )
        context.level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 1.0, at.z, 20, 0.3, 0.5, 0.3, 0.1)
        context.level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.0f, pitch)
    }

    companion object {
        val IDENTITY: AbilityIdentity = AbilityIdentity.of("door_anti_divination")

        /**
         * Нижче цієї Послідовності щит не тримає. Межа плагінна й не косметична: інакше
         * Учень Посл. 9 із чужим щитом був би незнаходжуваним для Астролога Посл. 7.
         */
        const val UNLOCK_SEQUENCE = 7
    }
}
