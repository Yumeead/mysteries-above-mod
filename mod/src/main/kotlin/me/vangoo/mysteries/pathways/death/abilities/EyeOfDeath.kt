package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.GravediggerLore
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.Subscription
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.pathways.death.DEATH_COLOR
import me.vangoo.mysteries.pathways.death.Spirits
import me.vangoo.mysteries.pathways.death.isDeadSoul
import me.vangoo.mysteries.pathways.death.isUndead
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.CombatRules
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import java.util.UUID

/**
 * Death, Seq. 8 (Gravedigger): **Eye of Death**.
 *
 * "By pressing down on their eyes, it will turn gray then colorless, allowing them to quickly
 * determine the weaknesses of even unfamiliar Undead… looking down at the enemy from a higher
 * vantage point as they search for a 'node'."
 *
 * A node hangs on the target until struck — the next blow of the Gravedigger lands multiplied, and
 * on the dead it goes **through armour**. Unlike [UndeadKnowledge]'s timed mark, it reads even the
 * unfamiliar, and the living too, at half the bonus.
 *
 * ## The eyes really do turn grey
 *
 * The plugin could not touch the camera, so the "higher vantage point" was a beam drawn from above.
 * The beam stays — a column of the pathway's colour falling onto the node — and the eyes now do
 * what the line says: for two seconds the Gravedigger's world loses its colour, the same chain the
 * Spirit Vision uses, put up by the client for as long as the short gaze session runs.
 */
class EyeOfDeath : ActiveAbility(IDENTITY) {

    override val displayName: Component = Component.literal("Око Смерті")

    override val spiritualityCost: Int = GravediggerLore.EYE_COST

    override fun cooldownSeconds(sequence: Sequence): Int = GravediggerLore.eyeCooldownSeconds(sequence)

    private val nodes = HashMap<UUID, Subscription>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ваші очі сіріють — і ви бачите вузол цілі. Наступний ваш удар по ній посилений.\n" +
            "▪ Мертве (нежить, духи): ×${"%.2f".format(GravediggerLore.eyeDeadDamageMultiplier(sequence))}, крізь броню\n" +
            "▪ Живе: ×${"%.2f".format(GravediggerLore.eyeLivingDamageMultiplier(sequence))}\n" +
            "▪ Дальність погляду ${GravediggerLore.eyeRange(sequence).toInt()} бл.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val sequence = context.sequence
        val target = context.targeting.lookingAt(GravediggerLore.eyeRange(sequence))
            ?: return AbilityOutcome.failed("Погляд ні на кому не спинився")
        val dead = target.isDeadSoul()
        val multiplier = if (dead) GravediggerLore.eyeDeadDamageMultiplier(sequence) else GravediggerLore.eyeLivingDamageMultiplier(sequence)
        val casterId = context.caster.uuid
        val messaging = context.messaging

        nodes.remove(casterId)?.cancel()
        lateinit var node: Subscription
        node = context.events.on<IncomingDamage>(GravediggerLore.NODE_TTL_SECONDS * TICKS_PER_SECOND) { damage ->
            if (damage.victim !== target || damage.attacker?.uuid != casterId) return@on
            damage.amount = if (dead) throughArmour(target, damage, damage.amount * multiplier) else (damage.amount * multiplier).toFloat()
            node.cancel()
            nodes.remove(casterId)
            target.removeEffect(MobEffects.GLOWING)
            val level = target.level() as ServerLevel
            level.sendParticles(ParticleTypes.SCULK_SOUL, target.x, target.y + 1.0, target.z, 12, 0.3, 0.3, 0.3, 0.03)
            level.playSound(null, target.x, target.y, target.z, SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.9f, 0.5f)
            messaging.actionBar(casterId, Component.literal("✦ Вузол пробито (×${"%.2f".format(multiplier)})"))
        }
        nodes[casterId] = node
        target.addEffect(MobEffectInstance(MobEffects.GLOWING, NODE_GLOW_TICKS, 0, true, false, false))

        gazeFromAbove(context, target)
        // The two grey seconds: the client reads them off this session.
        context.session { waitTicks(GREY_TICKS) }

        val percent = (100.0 * target.health / target.maxHealth.coerceAtLeast(1f)).toInt()
        val essence = when {
            target.isUndead() -> "нежить"
            Spirits.isSpirit(target) -> "дух"
            else -> "живе тіло"
        }
        return AbilityOutcome.done(
            "✦ Вузол: ${target.name.string} · $essence · $percent% · ×${"%.2f".format(multiplier)}" +
                if (dead) " — навстіж" else " — схований плоттю",
        )
    }

    /**
     * The dead are not held together by armour: the blow is inflated by exactly what the armour
     * would take back, so what lands is the multiplied number.
     *
     * ponytail: armour's reduction is not linear, so this is one step of the inverse, not the exact
     * solution — it lands within a few percent. Solve it exactly if a balance pass ever asks.
     */
    private fun throughArmour(target: LivingEntity, damage: IncomingDamage, wanted: Double): Float {
        val raw = wanted.toFloat()
        val armour = target.armorValue.toFloat()
        if (armour <= 0f) return raw
        val toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS).toFloat()
        val after = CombatRules.getDamageAfterAbsorb(target, raw, damage.source, armour, toughness)
        return if (after <= 0f) raw else raw * raw / after
    }

    /** A column of the pathway's colour falling onto the node from where the eye looks down. */
    private fun gazeFromAbove(context: CastContext, target: LivingEntity) {
        val level = context.level
        var step = 0
        lateinit var handle: me.vangoo.mysteries.schedule.TaskHandle
        handle = context.scheduling.every(1, delayTicks = 0) {
            if (step > GAZE_STEPS || !target.isAlive) return@every handle.cancel()
            val y = target.eyeY + GAZE_HEIGHT * (1.0 - step.toDouble() / GAZE_STEPS)
            level.sendParticles(ModParticles.glyph(DEATH_COLOR.shade(0.2)), target.x, y, target.z, 2, 0.05, 0.1, 0.05, 0.0)
            step++
        }
        level.playSound(
            null, target.x, target.y, target.z,
            if (target.isDeadSoul()) SoundEvents.SCULK_SHRIEKER_SHRIEK else SoundEvents.SCULK_CLICKING,
            SoundSource.PLAYERS, 0.8f, 0.6f,
        )
    }

    companion object {
        val IDENTITY: AbilityIdentity = AbilityIdentity.of("death_eye_of_death")

        private const val GAZE_HEIGHT = 6.0
        private const val GAZE_STEPS = 10
        private const val GREY_TICKS = 40
        private const val NODE_GLOW_TICKS = 30 * TICKS_PER_SECOND
    }
}
