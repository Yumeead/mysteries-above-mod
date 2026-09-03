package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.pathways.justiciar.Zone
import me.vangoo.mysteries.pathways.justiciar.raiseZone
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.skeleton.Skeleton
import net.minecraft.world.entity.monster.spider.Spider
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.Items

/**
 * Justiciar, Seq. 6 (Judge): **Spawn Prohibition**.
 *
 * Bans one kind of hostile mob from a territory: existing ones are removed on the spot, and the
 * zone keeps sweeping for new arrivals for as long as it stands. Mode is which spawn egg the
 * Judge holds — a small irony that also happens to be the clearest possible mapping from item to
 * effect, better than the arbitrary picks a mode-cycling ability usually needs.
 *
 * No `CreatureSpawnEvent` exists on Fabric to cancel outright, so the ban stays a sweep rather
 * than a true cancellation — the same honest trade-off Tyrant's `LightningBoltArrow` made when
 * its own event had no Fabric hook.
 */
class SpawnProhibition : ActiveAbility(AbilityIdentity.of("justiciar_spawn_prohibition")) {

    override val displayName: Component = Component.literal("Заборона Спавну")

    override val spiritualityCost: Int = 200

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Виганяє і не пускає назад один вид нечисті в радіусі $RADIUS бл. на ${DURATION_TICKS / 20} с.\n" +
            "▪ Яйце-спавнер у руці обирає вид: зомбі, скелет, павук, кріпер\n" +
            "▪ Без яйця — забороняє всю ворожу нечисть",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val matches = matcherFor(context.caster.mainHandItem.item)

        val zone = context.raiseZone(
            radius = RADIUS,
            durationTicks = DURATION_TICKS,
            boundaryParticle = ParticleTypes.SMOKE,
            boundaryPeriodTicks = SWEEP_PERIOD_TICKS,
            onTick = { purge(context, it, matches) },
            onExpire = { context.messaging.actionBar(context.caster, Component.literal("⚖ Заборона спавну завершилась")) },
        )
        val removed = purge(context, zone, matches)

        showActivation(context, removed)
        return AbilityOutcome.done("Заборону спавну накладено")
    }

    private fun purge(context: CastContext, zone: Zone, matches: (LivingEntity) -> Boolean): Int {
        val box = net.minecraft.world.phys.AABB.ofSize(zone.center, RADIUS * 2, RADIUS * 2, RADIUS * 2)
        val victims = context.level.getEntitiesOfClass(LivingEntity::class.java, box) { it !is net.minecraft.server.level.ServerPlayer && matches(it) && zone.contains(it.position()) }
        for (victim in victims) {
            context.level.sendParticles(ParticleTypes.POOF, victim.x, victim.y + 0.5, victim.z, 20, 0.3, 0.5, 0.3, 0.05)
            context.level.playSound(null, victim.x, victim.y, victim.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.5f, 1.5f)
            victim.discard()
        }
        return victims.size
    }

    private fun matcherFor(item: net.minecraft.world.item.Item): (LivingEntity) -> Boolean = when (item) {
        Items.ZOMBIE_SPAWN_EGG -> { entity -> entity is Zombie }
        Items.SKELETON_SPAWN_EGG -> { entity -> entity is Skeleton }
        Items.SPIDER_SPAWN_EGG -> { entity -> entity is Spider }
        Items.CREEPER_SPAWN_EGG -> { entity -> entity is Creeper }
        else -> { entity -> entity is Monster }
    }

    private fun showActivation(context: CastContext, removed: Int) {
        val center = context.origin
        context.level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y + 1.0, center.z, 1, 0.0, 0.0, 0.0, 0.0)
        context.level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.5f)
        context.messaging.actionBar(
            context.caster,
            Component.literal(if (removed > 0) "⚖ Активовано (видалено: $removed)" else "⚖ Активовано"),
        )
    }

    private companion object {
        const val RADIUS = 30.0
        const val DURATION_TICKS = 60 * 20
        const val SWEEP_PERIOD_TICKS = 40
        const val COOLDOWN_SECONDS = 60
    }
}
