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
 * ## Not a body swap
 *
 * The plugin teleported the caster into the puppet, swapped both inventories, swapped both
 * appearances, and handed out an NBT-tagged item as the only way back. All of that was scaffolding
 * around one thing Spigot could not do: steer somebody else's body. Here the camera goes to the
 * puppet and the keys are read off the driver (`Marionettes.possess`), so nothing is exchanged at
 * all — and what is left is the picture from the novel, where the puppeteer does not move.
 *
 * ## The body left behind is the price
 *
 * Damage to the puppet does not reach the driver — it has its own health, inherited from whoever
 * it was made of, and that is the whole point of having one. Damage to the **body** does: it is a
 * player standing still in the world, and if it dies while you are away, you die. A heartbeat is
 * played when something strikes it, because the driver cannot see it happen.
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
            "▪ Керування — звичайні клавіші руху; удар б'є рукою маріонетки\n" +
            "▪ Поки ви всередині, вам доступні й сили того, з кого зроблено маріонетку\n" +
            "▪ Шкода по маріонетці вас не зачіпає; шкода по вашому тілу — зачіпає, і смерть тіла є вашою смертю\n" +
            "▪ Повторний каст повертає вас у тіло; маріонетка лишається там, де ви її покинули",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val casterId = context.caster.uuid

        if (Marionettes.isPossessing(casterId)) {
            Marionettes.eject(casterId)
            Marionettes.pushState(context.caster)
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
