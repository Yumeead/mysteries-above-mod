package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.CorpseCollectorLore
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.SessionEnd
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.pathways.death.Spirits
import me.vangoo.mysteries.pathways.death.isUndead
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.phys.EntityHitResult

/**
 * Death, Seq. 9 (Corpse Collector): **Spirit Vision**.
 *
 * "Can use Spirit Vision to see non-physical things… deduce a person's health and emotions." Unlike
 * the always-on [DeathSight], this has to be opened, reaches further and costs spirituality every
 * second; a second cast closes it, an empty pool closes it by itself.
 *
 * ## The world turns grey
 *
 * The plugin could only outline the undead. Here the eye itself changes: while the vision is open
 * the Corpse Collector's screen loses its colour — the world seen as a graveyard — and only the
 * dead glow through it. That is a post-processing chain (`post_effect/death_sight.json`), i.e. data;
 * the client puts it up for exactly as long as this session runs, which it already learns from the
 * ability state it receives anyway. Not a single packet of its own.
 */
class SpiritVision : ActiveAbility(IDENTITY) {

    override val displayName: Component = Component.literal("Духовний зір")

    // A power that goes out by itself costs only its upkeep — otherwise the cast that closes it
    // would charge for stopping to pay.
    override val spiritualityCost: Int = 0

    override fun periodicCost(sequence: Sequence): Int = CorpseCollectorLore.SPIRIT_VISION_PERIODIC_COST

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Світ сіріє, як кладовище, а нежить світиться крізь стіни. Той, на кого дивитесь, розкриває свій стан.\n" +
            "▪ Радіус ${CorpseCollectorLore.spiritVisionRange(sequence).toInt()} бл." +
            (if (sequence.level <= SPIRIT_SIGHT_SEQUENCE) ", духів теж видно" else "") + "\n" +
            "▪ Витрата ${CorpseCollectorLore.SPIRIT_VISION_PERIODIC_COST} духовності/с, повторне застосування закриває",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.done("✦ Духовний зір закрито")
        }

        val messaging = context.messaging
        val at = context.origin
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 0.8f, 0.7f)
        context.session {
            val end = sustain(periodTicks = SCAN_TICKS, upkeep = periodicCost) { player ->
                val sequence = beyonder?.sequence ?: return@sustain
                reveal(player, sequence)
                report(player, sequence)?.let { messaging.actionBar(player, it) }
            }
            if (end == SessionEnd.EXHAUSTED) owner?.let { messaging.actionBar(it, Component.literal("✗ Духовність вичерпана — зір мутніє")) }
        }
        return AbilityOutcome.done("✦ Духовний зір розкрився")
    }

    private fun reveal(player: ServerPlayer, sequence: Sequence) {
        val range = CorpseCollectorLore.spiritVisionRange(sequence)
        val spiritsToo = sequence.level <= SPIRIT_SIGHT_SEQUENCE
        player.level().getEntitiesOfClass(LivingEntity::class.java, player.boundingBox.inflate(range)) {
            it.isUndead() || (spiritsToo && Spirits.isSpirit(it))
        }.forEach { it.addEffect(MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, true, false, false)) }
    }

    /** "Deduce a person's health and emotions": whoever is under the gaze, read in one line. */
    private fun report(player: ServerPlayer, sequence: Sequence): Component? {
        val range = CorpseCollectorLore.spiritVisionRange(sequence)
        val hit = ProjectileUtil.getHitResultOnViewVector(player, { it is LivingEntity && it !== player && it.isAlive }, range)
        val target = (hit as? EntityHitResult)?.entity as? LivingEntity ?: return null
        val percent = (100.0 * target.health / target.maxHealth.coerceAtLeast(1f)).toInt()
        val essence = when {
            Spirits.isSpirit(target) -> "дух"
            target.isUndead() -> "нежить"
            else -> "плоть"
        }
        val mood = target.activeEffects.joinToString { it.effect.value().displayName.string }.ifEmpty { "спокій" }
        return Component.literal("✦ ${target.name.string} · $percent% · $essence · $mood")
    }

    companion object {
        val IDENTITY: AbilityIdentity = AbilityIdentity.of("death_spirit_vision")

        private const val COOLDOWN_SECONDS = 3
        private const val SCAN_TICKS = 10
        private const val GLOW_TICKS = 25
        private const val SPIRIT_SIGHT_SEQUENCE = 8
    }
}
