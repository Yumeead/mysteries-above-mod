package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityMode
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.worldText
import me.vangoo.mysteries.net.payload.EdictKind
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Edict
import me.vangoo.mysteries.pathways.justiciar.clang
import me.vangoo.mysteries.pathways.justiciar.edict
import me.vangoo.mysteries.pathways.justiciar.gavel
import me.vangoo.mysteries.pathways.justiciar.lash
import me.vangoo.mysteries.pathways.justiciar.rootInPlace
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Relative
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.phys.Vec3

/**
 * Justiciar, Seq. 6 (Judge): **Verdict**.
 *
 * A word of the mystical tongue, and what it names comes true. The Judge speaks it (the word stands over their head
 * for a moment, in gold: the lore's one exception to the rule that powers do not write) and [WORD_TICKS] later the
 * sentence lands. Which sentence is the ability's mode, picked on the wheel (the user's call, 17.11.2026); the
 * plugin's item-in-hand choice and its trident "Evaporation", which the novel has no trace of, are gone. The five
 * are the novel's own:
 *
 * - **Exile**: an immense invisible force throws everyone hostile round the Judge out and away.
 * - **Imprison**: layer upon layer of transparent wall and a slow, viscous liquid freeze the target where it stands.
 * - **Confinement**: a transparent cell round the target; it can move inside it and cannot leave.
 * - **Death**: the Judge becomes a blur dragging afterimages and crashes a fist into the target's head.
 * - **Flog**: a swing of the hand, and an invisible soft whip tears the target's clothes and flesh, thrice.
 */
class Verdict : ActiveAbility(AbilityIdentity.of("justiciar_verdict")) {

    private enum class Sentence(val word: String) {
        EXILE("Вигнання"),
        IMPRISON("Ув'язнення"),
        CONFINE("Ізоляція"),
        DEATH("Смерть"),
        FLOG("Бичування"),
    }

    override val displayName: Component = Component.literal("Вердикт")

    override val spiritualityCost: Int = 130

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun modes(sequence: Sequence): List<AbilityMode> =
        Sentence.entries.map { AbilityMode(it.name.lowercase(), Component.literal(it.word)) }

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви вимовляєте вирок, і він здійснюється. Вирок обирається на колесі.\n" +
            "▪ Вигнання: відкидає ворогів у радіусі ${EXILE_RADIUS.toInt()} бл.\n" +
            "▪ Ув'язнення: ціль під прицілом скута на ${IMPRISON_TICKS / 20} с\n" +
            "▪ Ізоляція: ціль замкнена в прозорій сфері радіусом ${CONFINE_RADIUS.toInt()} бл. на ${CONFINE_TICKS / 20} с\n" +
            "▪ Смерть: ривок до цілі й удар на ${DEATH_DAMAGE.toInt()} шкоди\n" +
            "▪ Бичування: три удари невидимим батогом, рве обладунок\n" +
            "▪ Дальність $RANGE бл.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val sentence = Sentence.entries.firstOrNull { it.name.lowercase() == mode(context) } ?: Sentence.EXILE
        val caster = context.caster
        val level = context.level

        val target = if (sentence == Sentence.EXILE) null else context.targeting.lookingAt(RANGE) ?: return AbilityOutcome.failed("Немає цілі під прицілом")
        if (sentence == Sentence.EXILE && exiled(context).isEmpty()) return AbilityOutcome.failed("Поруч нікого вигнати")

        // The word, then the sentence.
        val word = Component.literal("${sentence.word}!").withColor(WORD_COLOR)
        worldText(context.scheduling, level, caster.position().add(0.0, caster.bbHeight + WORD_HEIGHT, 0.0), word, WORD_SHOWN_TICKS, fadeInTicks = WORD_TICKS)
        gavel(level, caster.position(), 1.2f, 0.75f)
        caster.swing(InteractionHand.MAIN_HAND, true)

        context.scheduling.after(WORD_TICKS) {
            when (sentence) {
                Sentence.EXILE -> exile(context)
                Sentence.IMPRISON -> imprison(context, target!!)
                Sentence.CONFINE -> confine(context, target!!)
                Sentence.DEATH -> death(context, target!!)
                Sentence.FLOG -> flog(context, target!!)
            }
        }
        return AbilityOutcome.Done()
    }

    /** Everyone the Exile would throw: other players and hostile creatures round the Judge. */
    private fun exiled(context: CastContext): List<LivingEntity> {
        val caster = context.caster
        return context.level.getEntitiesOfClass(LivingEntity::class.java, caster.boundingBox.inflate(EXILE_RADIUS)) {
            it !== caster && it.isAlive && (it is ServerPlayer || it is Enemy) && it.distanceTo(caster) <= EXILE_RADIUS
        }
    }

    private fun exile(context: CastContext) {
        val caster = context.caster
        val level = context.level
        edict(level, caster, EdictKind.EXILE, context.effects.pathwayColor, Edict.EXILE_TICKS)
        level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8f, 1.3f)
        for (target in exiled(context)) {
            val away = target.position().subtract(caster.position()).multiply(1.0, 0.0, 1.0)
                .let { if (it.lengthSqr() < 1e-4) caster.lookAngle.multiply(1.0, 0.0, 1.0) else it }.normalize()
            // Harder the nearer: the force is the Judge's, it spends itself with distance.
            val push = EXILE_PUSH * (1.2 - target.distanceTo(caster) / EXILE_RADIUS).coerceIn(0.5, 1.2)
            target.deltaMovement = away.scale(push).add(0.0, EXILE_LIFT, 0.0)
            target.hurtMarked = true
        }
    }

    private fun imprison(context: CastContext, target: LivingEntity) {
        val level = context.level
        edict(level, target, EdictKind.IMPRISON, context.effects.pathwayColor, IMPRISON_TICKS)
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, IMPRISON_TICKS, 255, false, false))
        target.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, IMPRISON_TICKS, 250, false, false))
        target.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, IMPRISON_TICKS, 4, false, false))
        context.rootInPlace(target, IMPRISON_TICKS)
        for (layer in 0 until Edict.IMPRISON_LAYERS) {
            context.scheduling.after(layer * Edict.IMPRISON_LAYER_TICKS) { clang(level, target.position(), 1.4f + 0.2f * layer) }
        }
        context.scheduling.after(Edict.IMPRISON_LAYER_TICKS * Edict.IMPRISON_LAYERS) {
            level.playSound(null, target.x, target.y, target.z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 1f, 0.6f)
        }
    }

    /**
     * A dome of [CONFINE_RADIUS] round where the target stands. Inside it moves freely; at its wall it is set back
     * inside, its run stopped. A player is set back with `connection.teleport`: `teleportTo` keeps the
     * client's velocity and would carry them straight back out.
     */
    private fun confine(context: CastContext, target: LivingEntity) {
        val level = context.level
        val centre = target.position()
        edict(level, target, EdictKind.CONFINE, context.effects.pathwayColor, CONFINE_TICKS)
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.6f)
        val loop = context.scheduling.every(1, 0) {
            if (!target.isAlive) return@every
            val at = target.position()
            val out = at.subtract(centre).multiply(1.0, 0.0, 1.0)
            val reach = CONFINE_RADIUS - target.bbWidth / 2.0
            val ceiling = centre.y + CONFINE_RADIUS - target.bbHeight - 0.5
            if (out.length() <= reach && at.y <= ceiling) return@every
            val inside = if (out.length() > reach) out.normalize().scale(reach - 0.3) else out
            val back = Vec3(centre.x + inside.x, at.y.coerceAtMost(ceiling), centre.z + inside.z)
            if (target is ServerPlayer) {
                target.connection.teleport(PositionMoveRotation(back, Vec3.ZERO, 0f, 0f), Relative.ROTATION)
            } else {
                target.deltaMovement = Vec3.ZERO
                target.teleportTo(back.x, back.y, back.z)
            }
            level.playSound(null, back.x, back.y, back.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1f, 0.5f)
        }
        context.scheduling.after(CONFINE_TICKS) { loop.cancel() }
    }

    /**
     * The Judge becomes a blur: carried along the line to the target over [Edict.DEATH_DASH_TICKS], a step a tick
     * (the client draws the afterimages from where they were), and on arrival a blow to the head.
     */
    private fun death(context: CastContext, target: LivingEntity) {
        val caster = context.caster
        val level = context.level
        val start = caster.position()
        lash(level, caster, target, WorldEffectPayload.Lash.Shape.DASH, context.effects.pathwayColor)
        level.playSound(null, start.x, start.y, start.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1f, 0.7f)
        for (step in 1..Edict.DEATH_DASH_TICKS) {
            context.scheduling.after(step) {
                if (!target.isAlive) return@after
                val toward = target.position().subtract(start)
                val stop = toward.length() - DEATH_STOP_SHORT
                val at = start.add(toward.normalize().scale(stop * step / Edict.DEATH_DASH_TICKS))
                caster.connection.teleport(PositionMoveRotation(at, Vec3.ZERO, 0f, 0f), Relative.ROTATION)
                if (step == Edict.DEATH_DASH_TICKS) strike(level, caster, target)
            }
        }
    }

    private fun strike(level: ServerLevel, caster: ServerPlayer, target: LivingEntity) {
        caster.swing(InteractionHand.MAIN_HAND, true)
        target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), DEATH_DAMAGE)
        val away = target.position().subtract(caster.position()).multiply(1.0, 0.0, 1.0).normalize()
        target.deltaMovement = away.scale(DEATH_KNOCK).add(0.0, 0.35, 0.0)
        target.hurtMarked = true
        level.playSound(null, target.x, target.eyeY, target.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2f, 0.8f)
    }

    /** Three strokes of an unseen whip, each a swing of the hand: flesh torn, and the armour on the body with it. */
    private fun flog(context: CastContext, target: LivingEntity) {
        val caster = context.caster
        val level = context.level
        lash(level, caster, target, WorldEffectPayload.Lash.Shape.FLOG, context.effects.pathwayColor)
        for (stroke in 0 until Edict.FLOG_STROKES) {
            context.scheduling.after(stroke * Edict.FLOG_STROKE_TICKS) {
                if (!target.isAlive) return@after
                caster.swing(InteractionHand.MAIN_HAND, true)
                target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), FLOG_DAMAGE)
                for (slot in listOf(EquipmentSlot.CHEST, EquipmentSlot.LEGS)) {
                    val worn = target.getItemBySlot(slot)
                    if (!worn.isEmpty && worn.isDamageableItem) worn.hurtAndBreak(FLOG_TEAR, target, slot)
                }
                level.playSound(null, target.x, target.eyeY, target.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 1.6f)
            }
        }
    }

    private companion object {
        const val RANGE = 20.0
        const val COOLDOWN_SECONDS = 30

        const val WORD_TICKS = 6
        const val WORD_SHOWN_TICKS = 30
        const val WORD_HEIGHT = 0.6
        const val WORD_COLOR = 0xF2C14E

        const val EXILE_RADIUS = 8.0
        const val EXILE_PUSH = 2.2
        const val EXILE_LIFT = 0.55

        const val IMPRISON_TICKS = 8 * 20

        const val CONFINE_TICKS = 12 * 20
        const val CONFINE_RADIUS = 5.0

        const val DEATH_DAMAGE = 16f
        const val DEATH_STOP_SHORT = 1.2
        const val DEATH_KNOCK = 1.4

        const val FLOG_DAMAGE = 4f
        const val FLOG_TEAR = 12
    }
}
