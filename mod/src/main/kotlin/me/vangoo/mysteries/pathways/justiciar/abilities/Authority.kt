package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.EyeGlow
import me.vangoo.mysteries.effect.Eyes
import me.vangoo.mysteries.effect.ward
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Edict
import me.vangoo.mysteries.pathways.justiciar.gavel
import me.vangoo.mysteries.pathways.justiciar.seal
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.item.ItemStack

/**
 * Justiciar, Seq. 9 (Arbiter): **Authority**.
 *
 * Pressure that builds for six seconds in three knocks: an aura kindles round the Arbiter, as everyone near sees
 * it, and rises a step at each knock; on the fourth it flares and strikes. Ordinary people bow their heads and drop
 * what they hold, hostile creatures take a blow. Nothing is written: the knocks, the aura and the bowed heads are
 * all there is (the user's call, 16.11.2026: no item over the hand, an aura in the manner of Nen, but laconic).
 *
 * Who is struck is whoever stands in range **when the frame closes**, not when it was begun: three knocks
 * are time to step away. A cast with no one around at all is a refusal and costs nothing.
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
        "Довкола вас розгоряється аура влади і за ${Edict.SEAL_TICKS / 20} с вибухає тиском.\n" +
            "▪ Радіус ${RADIUS.toInt()} бл., цілі визначаються в мить удару\n" +
            "▪ Гравці впускають зброю та броню, схиляють голову, дістають Повільність I на 10 с\n" +
            "▪ Вороже до нечисті: $MOB_DAMAGE шкоди\n" +
            "▪ Не чіпає мирних істот",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        if (struck(context).isEmpty()) return AbilityOutcome.failed("Поруч нікого, на кого можна тиснути")

        seal(context.level, caster, context.effects.pathwayColor)
        Eyes.light(caster, EyeGlow.GOLD)
        // Three knocks, each a little higher than the last; the blow is the fourth, and the loudest.
        for (knock in 0 until KNOCKS) {
            context.scheduling.after(knock * Edict.SEAL_KNOCK_TICKS) { gavel(context.level, caster.position(), 0.6f + 0.15f * knock, 0.8f + 0.15f * knock) }
        }

        val casterSequence = context.beyonder.sequenceLevel
        context.scheduling.after(Edict.SEAL_TICKS) {
            gavel(context.level, caster.position(), 1.4f, 0.7f)
            for (target in struck(context)) {
                if (!target.isAlive) continue
                if (target is ServerPlayer) {
                    val defender = context.beyonders.of(target.uuid)
                    if (defender != null && !SequenceBasedSuccessChance(casterSequence, defender.sequenceLevel).rollSuccess()) {
                        ward(target, PathwayBranding.liquidOf(defender.pathway.name))
                        continue
                    }
                    subdue(context.level, target)
                } else {
                    target.hurtServer(context.level, context.level.damageSources().indirectMagic(caster, caster), MOB_DAMAGE)
                }
            }
        }
        context.scheduling.after(Edict.SEAL_TICKS + Edict.STRIKE_TICKS) { Eyes.dim(caster) }

        return AbilityOutcome.Done()
    }

    /** Who stands where the blow would land now: other players, and hostile creatures. */
    private fun struck(context: CastContext): List<LivingEntity> {
        val caster = context.caster
        val box = caster.boundingBox.inflate(RADIUS)
        return context.level.getEntitiesOfClass(LivingEntity::class.java, box) { it !== caster && (it is ServerPlayer || it is Enemy) }
    }

    private fun subdue(level: ServerLevel, target: ServerPlayer) {
        val drop = target.position()
        for (slot in EquipmentSlot.entries) {
            val stack = target.getItemBySlot(slot)
            if (!stack.isEmpty) {
                target.setItemSlot(slot, ItemStack.EMPTY)
                // Dropped right under the victim's own feet: the vanilla default pickup delay
                // (10 ticks) elapses before the Slowness even registers, so they scoop it straight
                // back up. A longer delay is what actually makes the stumble visible.
                val item = ItemEntity(level, drop.x, drop.y + 1.0, drop.z, stack)
                item.setPickUpDelay(DROP_PICKUP_DELAY_TICKS)
                level.addFreshEntity(item)
            }
        }
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, SLOWNESS_DURATION_TICKS, 0))
        // The head bows: the pitch is set, not turned, so it does not matter where it was looking.
        target.connection.send(ClientboundPlayerRotationPacket(target.yRot, false, (target.xRot + BOW_PITCH).coerceAtMost(MAX_PITCH), false))
    }

    private companion object {
        const val RADIUS = 15.0
        const val KNOCKS = 3
        const val SLOWNESS_DURATION_TICKS = 200
        const val DROP_PICKUP_DELAY_TICKS = 60
        const val MOB_DAMAGE = 8.0f
        const val COOLDOWN_SECONDS = 30
        const val BOW_PITCH = 40f
        const val MAX_PITCH = 85f
    }
}
