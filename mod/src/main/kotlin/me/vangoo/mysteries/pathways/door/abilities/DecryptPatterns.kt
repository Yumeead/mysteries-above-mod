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
 * Door, Seq. 6 (Scribe): **Decrypt Patterns**.
 *
 * The Scribe reads someone else's power the way one reads handwriting: they do not see the act
 * itself, but they do see **what exactly** was used, and by whom.
 *
 * ## The mod's first ability that listens to the mod's own event
 *
 * Before it the bus carried only damage, i.e. what comes from the game through a mixin.
 * [AbilityCast] comes from the mod itself — from `AbilityExecutor` — and that is exactly why the
 * event is worth its existence: without it "someone nearby cast something" would have to be
 * polled, and polling a moment that has already passed is impossible.
 *
 * ## Why the owner is looked up in the **caster's world**
 *
 * The handler lives longer than a tick, so a `ServerPlayer` cannot be held in it — after a
 * respawn it is no longer that player. A `uuid` is taken and the player is asked for anew each
 * time — and specifically through `event.caster.level()`, because "the same world" is the first
 * condition: a pattern used in another dimension is not felt at all, and measuring a distance to
 * it would be meaningless.
 */
class DecryptPatterns : PermanentPassiveAbility(AbilityIdentity.of("door_decrypt_patterns")) {

    override val displayName: Component = Component.literal("Розшифрування патернів")

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
