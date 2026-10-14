package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.pathways.fool.Marionettes
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Fool, Seq. 5 (Marionettist): **Possession**.
 *
 * The Marionettist steps into the puppet they are holding and walks in it. Their own body stays
 * exactly where it stood — slack, breathing, and completely undefended.
 *
 * ## The plugin's swap, kept — and why
 *
 * The plugin moved the caster into the puppet and swapped inventories and looks. The port first
 * tried the opposite (the camera into the puppet, the player left home), and the vanilla client
 * refused it piece by piece: the hand, the run, the hotbar and the hearts all assume the camera
 * is a player. So the driver goes into the puppet again (`Marionettes.possess`), but without the
 * plugin's NBT exit item and appearance swap: the stash of their own things is persistent, the way
 * back is this same ability, and the skin is swapped on the client alone.
 *
 * ## The body left behind is the price
 *
 * Inside, the driver's hit points are the puppet's: running out breaks the puppet and throws them
 * home. The **body** stays behind as a vessel with their own health — struck, pushed, and if it
 * falls while they are away, they die. A heartbeat plays when it is struck, because the driver
 * cannot see it happen.
 *
 * ## Free to enter, free to leave
 *
 * No spirituality: the threads are already paid for, and charging to step out of a puppet would
 * mean charging to stop. The four-second cooldown is against flicker, not against use.
 */
class Possession : ActiveAbility(AbilityIdentity.of("fool_marionette_possession")) {

    override val displayName: Component = Component.literal("Вселення")

    override val icon: ItemStack get() = ItemStack(Items.ECHO_SHARD)

    override val spiritualityCost: Int = 0

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви входите в обрану маріонетку й ходите в ній. Ваше тіло лишається стояти там, де стояло.\n" +
            "▪ Ви ходите як маріонетка: її вигляд, її речі, її здоров'я; ваші речі чекають у тілі\n" +
            "▪ Поки ви всередині, вам доступні й сили того, з кого зроблено маріонетку\n" +
            "▪ Якщо маріонетку розбито — вас викидає в тіло; якщо вбито тіло — ви гинете\n" +
            "▪ Повторний каст повертає вас у тіло; маріонетка лишається там, де ви її покинули",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val casterId = context.caster.uuid

        if (Marionettes.isPossessing(casterId)) {
            Marionettes.eject(context.caster)
            return AbilityOutcome.done("Ви повернулись у власне тіло")
        }

        val puppet = Marionettes.selected(casterId)
            ?: return AbilityOutcome.failed("У вас немає маріонеток")
        if (!puppet.isAlive) {
            return AbilityOutcome.failed("Ця маріонетка вже розбита")
        }

        val range = Marionettes.controlRange(context.beyonder)
        if (context.caster.level() !== puppet.level() || context.caster.distanceToSqr(puppet) > range * range) {
            return AbilityOutcome.failed("${Marionettes.nameOf(puppet)} поза зоною контролю")
        }

        Marionettes.possess(context.caster, puppet)
        Marionettes.pushState(context.caster)
        return AbilityOutcome.done("Ви в тілі ${Marionettes.nameOf(puppet)}")
    }

    private companion object {
        /** Against flicker only: entering and leaving are both free. */
        const val COOLDOWN_SECONDS = 4
    }
}
