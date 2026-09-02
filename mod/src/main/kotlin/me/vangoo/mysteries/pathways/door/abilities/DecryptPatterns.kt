package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.Ability
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.AbilityCast
import net.minecraft.network.chat.Component

/**
 * Door, Посл. 6 (Писар): **Розшифровка патернів**.
 *
 * Писар читає чужу силу, як читають почерк: не бачить самої дії, зате бачить, **що саме**
 * було застосовано й ким.
 *
 * ## Перша здібність мода, що слухає власну подію мода
 *
 * До неї шина везла лише шкоду, тобто те, що приходить із гри через міксин. [AbilityCast]
 * приходить із самого мода — з `AbilityExecutor`, — і саме тому подія варта існування:
 * без неї «хтось поруч скастував» довелось би опитувати, а опитати мить, що вже минула,
 * не можна взагалі.
 *
 * ## Чому власника шукаємо у **світі кастера**
 *
 * Обробник живе довше за тік, тож `ServerPlayer` у ньому тримати не можна — після
 * респавну він уже не той гравець. Береться `uuid`, а сам гравець питається щоразу
 * наново — причому саме через `event.caster.level()`, бо «той самий світ» і є першою
 * умовою: патерн, застосований в іншому вимірі, не відчувається ніяк, і рахувати до
 * нього відстань було б безглуздо.
 */
class DecryptPatterns : PermanentPassiveAbility(AbilityIdentity.of("door_decrypt_patterns")) {

    override val displayName: Component = Component.literal("Розшифровка патернів")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви читаєте чужі сили, щойно їх застосували.\n" +
            "▪ Радіус ${RANGE.toInt()} бл., той самий вимір\n" +
            "▪ Ім'я й назва сили — над хотбаром",
    )

    override fun onActivate(context: AbilityContext) {
        val owner = context.caster.uuid
        val messaging = context.messaging

        context.events.on<AbilityCast> { event ->
            if (event.caster.uuid == owner) return@on
            val reader = event.caster.level().getPlayerByUUID(owner) ?: return@on
            if (reader.distanceToSqr(event.caster) > RANGE * RANGE) return@on

            val name = (event.spec as? Ability)?.displayName?.string ?: event.spec.identity.id
            messaging.actionBar(
                owner,
                Component.literal("${event.caster.name.string} використав $name"),
            )
        }
    }

    private companion object {
        const val RANGE = 30.0
    }
}
