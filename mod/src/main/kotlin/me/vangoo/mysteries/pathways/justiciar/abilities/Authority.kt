package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Containers
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.item.ItemStack

/**
 * Justiciar, Seq. 9 (Arbiter): **Authority**.
 *
 * An aura of undeniable authority. Everyone nearby feels it build for a few seconds; when it
 * lands, ordinary people drop what they are holding and stumble, while hostile creatures simply
 * take a blow.
 *
 * The plugin told friend from foe with a six-branch heuristic (nametag, villager, golem, horse,
 * tameable, animal). Vanilla already drew that line and called it [Enemy] — the marker interface
 * every hostile mob implements — so the whole heuristic collapses into one `is` check.
 */
class Authority : ActiveAbility(AbilityIdentity.of("justiciar_authority")) {

    override val displayName: Component = Component.literal("Авторитет")

    override val spiritualityCost: Int = 50

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Випромінює ауру неоспорюваного авторитету.\n" +
            "▪ Радіус ${RADIUS.toInt()} бл., ефект настає через ${BUILDUP_TICKS / 20} с\n" +
            "▪ Гравці впускають зброю та броню, дістають Повільність I на 10 с\n" +
            "▪ Вороже до нечисті: $MOB_DAMAGE шкоди\n" +
            "▪ Не чіпає мирних істот",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val box = caster.boundingBox.inflate(RADIUS)
        val nearby = context.level.getEntitiesOfClass(LivingEntity::class.java, box) { it !== caster }
        val players = nearby.filterIsInstance<net.minecraft.server.level.ServerPlayer>()
        val hostiles = nearby.filter { it is Enemy }

        if (players.isEmpty() && hostiles.isEmpty()) {
            context.messaging.actionBar(caster, Component.literal("Аура Авторитету активована. Поруч немає цілей..."))
            return AbilityOutcome.done("Аура Авторитету розсіялась у порожнечі")
        }

        buildup(context)
        context.messaging.actionBar(caster, Component.literal("Аура Авторитету активована! Ефект через ${BUILDUP_TICKS / 20} с..."))

        val casterSequence = context.beyonder.sequenceLevel
        context.scheduling.after(BUILDUP_TICKS) {
            var subdued = 0
            var resisted = 0
            for (target in players) {
                if (!target.isAlive) continue
                val defender = context.beyonders.of(target.uuid)
                if (defender != null && !SequenceBasedSuccessChance(casterSequence, defender.sequenceLevel).rollSuccess()) {
                    resisted++
                    continue
                }
                subdue(context.level, target)
                subdued++
            }
            for (mob in hostiles) {
                if (!mob.isAlive) continue
                mob.hurtServer(context.level, context.level.damageSources().indirectMagic(caster, caster), MOB_DAMAGE)
            }
            context.messaging.actionBar(
                caster,
                Component.literal("Авторитет: підкорено $subdued, опиралось $resisted, вражено нечисті ${hostiles.size}"),
            )
        }

        return AbilityOutcome.done("Аура Авторитету наростає")
    }

    private fun subdue(level: net.minecraft.server.level.ServerLevel, target: net.minecraft.server.level.ServerPlayer) {
        val drop = target.position()
        for (slot in EquipmentSlot.entries) {
            val stack = target.getItemBySlot(slot)
            if (!stack.isEmpty) {
                target.setItemSlot(slot, ItemStack.EMPTY)
                Containers.dropItemStack(level, drop.x, drop.y + 1.0, drop.z, stack)
            }
        }
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_DURATION_TICKS, 0))
        level.sendParticles(ParticleTypes.CRIT, drop.x, drop.y + 1.0, drop.z, 25, 0.5, 1.0, 0.5, 0.1)
        level.playSound(
            null, drop.x, drop.y, drop.z,
            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.2f,
        )
    }

    private fun buildup(context: CastContext) {
        val center = context.origin
        context.level.sendParticles(ParticleTypes.ENCHANT, center.x, center.y + 1.0, center.z, 60, RADIUS * 0.3, 1.0, RADIUS * 0.3, 0.3)
        context.level.playSound(
            null, center.x, center.y, center.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.2f,
        )
    }

    private companion object {
        const val RADIUS = 15.0
        const val BUILDUP_TICKS = 120
        const val SLOWNESS_DURATION_TICKS = 200
        const val MOB_DAMAGE = 8.0f
        const val COOLDOWN_SECONDS = 30
    }
}
