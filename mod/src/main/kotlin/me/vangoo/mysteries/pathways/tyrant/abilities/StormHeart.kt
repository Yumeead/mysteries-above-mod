package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import java.util.UUID

/**
 * Tyrant, Seq. 5 (Ocean Songster): **Storm Heart**.
 *
 * Facing death, the Songster's heart clenches and swells like the source of a storm, and blood
 * surges through the veins like a tide. A reactive passive: it needs no cast at all.
 *
 * The rearm clock is the server tick, not [System.currentTimeMillis] as in the plugin — a paused
 * single-player game should not rearm while nobody is playing.
 */
class StormHeart : PermanentPassiveAbility(AbilityIdentity.of("tyrant_storm_heart")) {

    override val displayName: Component = Component.literal("Штормове Серце")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Коли удар лишає вам менше 40% здоров'я, серце б'є, мов джерело шторму.\n" +
            "▪ Сила II, Швидкість II і Регенерація на ${BURST_TICKS / 20} с\n" +
            "▪ Перезарядка ${REARM_TICKS / 20} с",
    )

    override fun onActivate(context: AbilityContext) {
        val owner = context.caster.uuid
        val scheduling = context.scheduling

        context.events.on<IncomingDamage> { damage ->
            val victim = damage.victim as? ServerPlayer ?: return@on
            if (victim.uuid != owner || damage.isCancelled) return@on

            val after = victim.health - damage.amount
            if (after <= 0f || after >= victim.maxHealth * HP_THRESHOLD) return@on

            val now = scheduling.tick
            val last = lastTrigger[owner]
            if (last != null && now - last < REARM_TICKS) return@on
            lastTrigger[owner] = now

            surge(victim)
        }
    }

    override fun onDeactivate(context: AbilityContext) {
        lastTrigger.remove(context.caster.uuid)
    }

    private fun surge(player: ServerPlayer) {
        player.addEffect(MobEffectInstance(MobEffects.STRENGTH, BURST_TICKS, 1))
        player.addEffect(MobEffectInstance(MobEffects.SPEED, BURST_TICKS, 1))
        player.addEffect(MobEffectInstance(MobEffects.REGENERATION, BURST_TICKS, 0))

        val at = player.position()
        player.level().playSound(
            null, at.x, at.y, at.z,
            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7f, 1.6f,
        )
        player.level().playSound(
            null, at.x, at.y, at.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 0.6f,
        )
        player.sendSystemMessage(Component.literal("Серце б'є штормом — кров рине припливом!"))
    }

    private companion object {
        const val HP_THRESHOLD = 0.4
        const val BURST_TICKS = 120
        const val REARM_TICKS = 60 * 20

        /** Per-player rearm clock. Not backed by any vanilla state. */
        val lastTrigger = mutableMapOf<UUID, Long>()
    }
}
