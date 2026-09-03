package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Justiciar, Seq. 8 (Sheriff): **Danger Intuition**.
 *
 * A sixth sense for who means harm: a hostile mob that has locked onto the Sheriff, or an armed
 * player sighting them down, both surface as an action-bar warning.
 */
class Intuition : ToggleablePassiveAbility(AbilityIdentity.of("justiciar_intuition")), Ticking {

    override val tickPeriodTicks: Int = 10

    override val displayName: Component = Component.literal("Інтуїція Небезпеки")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Дозволяє передчувати небезпеку від озброєних гравців і агресивних мобів у радіусі $RANGE бл.",
    )

    override fun onEnable(context: AbilityContext) {
        warnedAt[context.caster.uuid] = ConcurrentHashMap()
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 2.0f,
        )
        context.messaging.actionBar(context.caster, Component.literal("Інтуїцію загострено"))
    }

    override fun onDisable(context: AbilityContext) {
        warnedAt.remove(context.caster.uuid)
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 2.0f,
        )
        context.messaging.actionBar(context.caster, Component.literal("Інтуїцію послаблено"))
    }

    override fun tick(context: AbilityContext) {
        val caster = context.caster
        val warned = warnedAt.getOrPut(caster.uuid) { ConcurrentHashMap() }
        val now = context.scheduling.tick

        for (entity in context.targeting.livingNear(RANGE)) {
            val threat: Boolean = when {
                entity is Mob -> entity.target === caster
                entity is net.minecraft.server.level.ServerPlayer ->
                    holdsWeapon(entity) && isLookingAt(entity, caster)
                else -> false
            }
            if (!threat) continue

            val last = warned[entity.uuid]
            if (last != null && now - last < WARNING_COOLDOWN_TICKS) continue
            warned[entity.uuid] = now

            context.messaging.actionBar(
                caster,
                Component.literal("⚠ ЗАГРОЗА: ${entity.name.string} [${"%.1f".format(caster.position().distanceTo(entity.position()))} бл.]"),
            )
            context.level.playSound(
                null, caster.x, caster.y, caster.z,
                SoundEvents.NOTE_BLOCK_BASS, SoundSource.PLAYERS, 1.0f, 0.5f,
            )
        }
    }

    private fun holdsWeapon(player: net.minecraft.server.level.ServerPlayer): Boolean =
        player.mainHandItem.typeHolder().`is`(ItemTags.WEAPON_ENCHANTABLE)

    private fun isLookingAt(observer: LivingEntity, target: LivingEntity): Boolean {
        val toTarget = target.eyePosition.subtract(observer.eyePosition).normalize()
        return observer.getViewVector(1.0f).normalize().dot(toTarget) > 0.5
    }

    private companion object {
        const val RANGE = 20.0
        const val WARNING_COOLDOWN_TICKS = 100L // 5 seconds

        val warnedAt = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Long>>()
    }
}
