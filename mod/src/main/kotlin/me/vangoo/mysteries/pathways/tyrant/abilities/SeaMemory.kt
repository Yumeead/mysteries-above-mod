package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import net.minecraft.core.GlobalPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.LodestoneTracker
import java.util.Optional

/**
 * Tyrant, Seq. 7 (Seafarer): **Sea Memory**.
 *
 * The Seafarer's sharpened mind remembers a place hard enough that an ordinary compass keeps
 * pointing at it forever.
 *
 * ## Ten named slots became "carry more compasses"
 *
 * The plugin ran a whole waypoint subsystem for this: a chest menu to create, rename and delete
 * up to ten marks, a store that survives a restart, and a repeating action-bar arrow with a
 * relative-direction glyph, because Bukkit had nothing that would just *point*. Vanilla already
 * has that thing — [LodestoneTracker], the same component a lodestone-crafted compass carries —
 * and it needs no physical lodestone at all: [tracked] only governs whether the game keeps
 * re-checking that a block is still there, so `false` leaves the pointer standing on its own.
 *
 * A compass only remembers one place, which is exactly the point: memorising a *second* place is
 * "hold a second compass," and an anvil already renames one better than any chat prompt did.
 * The menu, the store and the hand-computed arrow all go away with nothing rebuilt in their place.
 */
class SeaMemory : ActiveAbility(AbilityIdentity.of("tyrant_sea_memory")) {

    override val displayName: Component = Component.literal("Морська Пам'ять")

    override val spiritualityCost: Int = 15

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Заряджає компас у вашій руці: він назавжди вказує на це місце, без потреби в лодестоні.\n" +
            "▪ Носіть кілька компасів — кожен пам'ятає своє місце\n" +
            "▪ Перейменуйте компас у кузні, щоб не плутати позначки",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val stack = context.caster.mainHandItem
        if (!stack.`is`(Items.COMPASS)) {
            return AbilityOutcome.failed("Потрібен звичайний компас у головній руці")
        }

        val here = GlobalPos.of(context.level.dimension(), context.caster.blockPosition())
        stack.set(DataComponents.LODESTONE_TRACKER, LodestoneTracker(Optional.of(here), false))

        val eye = context.caster.eyePosition
        context.level.playSound(
            null, eye.x, eye.y, eye.z,
            SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.PLAYERS, 0.8f, 0.8f,
        )
        return AbilityOutcome.done("Компас запам'ятав це місце")
    }

    private companion object {
        const val COOLDOWN_SECONDS = 8
    }
}
