package me.vangoo.mysteries.pathways.visionary.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import java.util.UUID

/**
 * Visionary, Seq. 8 (Telepathist): **Danger Sense**.
 *
 * Stand near someone for twenty seconds and their weapons stop being a secret: everything
 * dangerous in their inventory surfaces on the action bar, and they glow for five.
 *
 * ## Two clocks, both the mod's tick
 *
 * The plugin timed both the twenty-second watch and the two-and-a-half-minute per-target
 * cooldown against `System.currentTimeMillis()`. Neither needs the wall clock — nothing here
 * has to survive the caster logging out, unlike `TheftRegistry`'s loan — so both stay on
 * `Scheduling.tick`, the same clock every session and cooldown in the mod already uses.
 */
class DangerSense :
    ToggleablePassiveAbility(AbilityIdentity.of("visionary_danger_sense")),
    Ticking {

    override val tickPeriodTicks: Int = TICKS_PER_SECOND

    override val displayName: Component = Component.literal("Психічне відлуння")

    private class Watch(var ticks: Int = 0)

    private val watches = HashMap<UUID, HashMap<UUID, Watch>>()
    private val readyAt = HashMap<UUID, HashMap<UUID, Long>>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Побудьте поруч із гравцем ($RANGE бл.) ${OBSERVE_TICKS / TICKS_PER_SECOND} с — і дізнаєтесь про зброю в його інвентарі.",
    )

    override fun onEnable(context: AbilityContext) {
        watches[context.caster.uuid] = HashMap()
        chime(context, SoundEvents.BEACON_ACTIVATE, 1.5f)
    }

    override fun onDisable(context: AbilityContext) {
        watches.remove(context.caster.uuid)
        // Cooldowns stay: relogging to reset a target's cooldown would be the exploit they exist to prevent.
        chime(context, SoundEvents.BEACON_DEACTIVATE, 0.5f)
    }

    override fun tick(context: AbilityContext) {
        val owner = context.caster.uuid
        val myWatches = watches[owner] ?: return
        val myCooldowns = readyAt.getOrPut(owner) { HashMap() }
        val now = context.scheduling.tick

        val nearby = context.targeting.playersNear(RANGE)
        myWatches.keys.retainAll(nearby.map { it.uuid }.toSet())

        for (target in nearby) {
            val targetId = target.uuid
            if ((myCooldowns[targetId] ?: 0L) > now) continue

            val watch = myWatches.getOrPut(targetId) { Watch() }
            watch.ticks += tickPeriodTicks
            if (watch.ticks < OBSERVE_TICKS) continue

            reveal(context, target)
            myCooldowns[targetId] = now + TARGET_COOLDOWN_TICKS
            myWatches.remove(targetId)
        }
    }

    private fun reveal(context: AbilityContext, target: ServerPlayer) {
        val found = dangerousItems(target)
        if (found.isEmpty()) {
            context.messaging.actionBar(
                context.caster,
                Component.literal("Ви відчули ${target.name.string}, але не знайшли нічого підозрілого."),
            )
            return
        }

        context.messaging.actionBar(
            context.caster,
            Component.literal("⚠ Передчуття! У ${target.name.string} знайдено: ${found.joinToString(", ")}"),
        )
        context.level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 0.5f,
        )
        target.addEffect(MobEffectInstance(MobEffects.GLOWING, MARK_TICKS, 0, true, false, true))
    }

    private fun dangerousItems(target: ServerPlayer): List<String> {
        val held = target.inventory.nonEquipmentItems.asSequence() + sequenceOf(target.mainHandItem)
        return held
            .filter { !it.isEmpty && it.item in DANGEROUS_ITEMS }
            .map { it.hoverName.string }
            .distinct()
            .toList()
    }

    private fun chime(context: AbilityContext, sound: net.minecraft.sounds.SoundEvent, pitch: Float) {
        val at = context.origin
        context.level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.0f, pitch)
    }

    private companion object {
        const val RANGE = 8.0
        val OBSERVE_TICKS = 20 * TICKS_PER_SECOND
        val TARGET_COOLDOWN_TICKS = 150L * TICKS_PER_SECOND
        const val MARK_TICKS = 5 * TICKS_PER_SECOND

        val DANGEROUS_ITEMS: Set<Item> = setOf(
            Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD,
            Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
            Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE,
            Items.DIAMOND_AXE, Items.NETHERITE_AXE,
            Items.BOW, Items.CROSSBOW, Items.TRIDENT,
            Items.TNT, Items.TNT_MINECART,
            Items.SPLASH_POTION, Items.LINGERING_POTION,
            Items.LAVA_BUCKET, Items.FIRE_CHARGE, Items.FLINT_AND_STEEL,
        )
    }
}
