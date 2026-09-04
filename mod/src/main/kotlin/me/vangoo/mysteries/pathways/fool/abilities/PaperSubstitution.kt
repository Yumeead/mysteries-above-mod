package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.DollBatch
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.EVENT_FOREVER
import me.vangoo.mysteries.ability.context.Subscription
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Fool, Seq. 7 (Magician): **Paper Figurine Substitution**.
 *
 * A plain cast folds a batch of paper dolls, which sit accumulated until spent. Sneak + cast
 * toggles a free "protection" switch: while it's on and dolls remain, the next heavy blow is
 * eaten by one and the Magician is yanked to safety nearby instead.
 */
class PaperSubstitution : ActiveAbility(AbilityIdentity.of("fool_paper_substitution")) {

    override val displayName: Component = Component.literal("Заміна паперовою лялькою")

    override val spiritualityCost: Int = BASE_COST

    override fun cooldownSeconds(sequence: Sequence): Int = BASE_COOLDOWN_SECONDS

    private val dollCount = ConcurrentHashMap<UUID, Int>()
    private val protecting = ConcurrentHashMap.newKeySet<UUID>()
    private val absorbSubscriptions = ConcurrentHashMap<UUID, Subscription>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "ПКМ — складає партію з ${DollBatch.dollsPerCast(sequence)} ляльок " +
            "(${DollBatch.paperCost(sequence)} паперу, накопичуються до ${DollBatch.maxStored(sequence)}).\n" +
            "▪ Shift+ПКМ — вмикає/вимикає захист: важкий удар поглинає лялька, ви телепортуєтесь поруч",
    )

    override fun cast(context: CastContext): AbilityOutcome =
        if (context.caster.isShiftKeyDown) toggleProtection(context) else craftBatch(context)

    private fun toggleProtection(context: CastContext): AbilityOutcome {
        val casterId = context.caster.uuid
        if (protecting.remove(casterId)) {
            absorbSubscriptions.remove(casterId)?.cancel()
            context.messaging.actionBar(context.caster, Component.literal("📜 Захист вимкнено"))
            chime(context, SoundEvents.IRON_DOOR_CLOSE, 1.4f)
            return AbilityOutcome.failed("Захист вимкнено")
        }
        if (dollCount.getOrDefault(casterId, 0) <= 0) {
            context.messaging.actionBar(context.caster, Component.literal("📜 Немає ляльок — спершу складіть партію"))
            return AbilityOutcome.failed("Немає ляльок")
        }

        protecting.add(casterId)
        absorbSubscriptions[casterId]?.cancel()
        absorbSubscriptions[casterId] = context.events.on<IncomingDamage>(EVENT_FOREVER) { damage ->
            if (damage.victim.uuid != casterId || damage.amount < DollBatch.DAMAGE_THRESHOLD) return@on
            if (absorb(context)) damage.cancel()
        }
        context.messaging.actionBar(context.caster, Component.literal("📜 Захист увімкнено (${dollCount[casterId]} ляльок)"))
        chime(context, SoundEvents.BEACON_ACTIVATE, 1.5f)
        return AbilityOutcome.failed("Захист увімкнено")
    }

    private fun craftBatch(context: CastContext): AbilityOutcome {
        val casterId = context.caster.uuid
        val max = DollBatch.maxStored(context.sequence)
        val current = dollCount.getOrDefault(casterId, 0)
        if (current >= max) return AbilityOutcome.failed("Ви вже накопичили максимум ляльок ($max)")

        val added = minOf(DollBatch.dollsPerCast(context.sequence), max - current)
        val paperNeeded = added * DollBatch.PAPER_PER_DOLL
        if (PaperInventory.count(context.caster) < paperNeeded) {
            return AbilityOutcome.failed("Потрібно $paperNeeded паперу в інвентарі")
        }

        PaperInventory.consume(context.caster, paperNeeded)
        dollCount[casterId] = current + added

        craftAnimation(context)
        context.messaging.actionBar(context.caster, Component.literal("📜 Складено $added ляльок (усього ${current + added})"))
        return AbilityOutcome.done("Складено $added ляльок")
    }

    /** @return true if a doll took the hit. */
    private fun absorb(context: CastContext): Boolean {
        val casterId = context.caster.uuid
        var dolls = dollCount.getOrDefault(casterId, 0)
        if (casterId !in protecting || dolls <= 0) return false

        dolls--
        dollCount[casterId] = dolls
        val origin = context.caster.position()
        scatter(context, origin)

        val escape = randomSafeSpotNear(context, origin, DollBatch.TELEPORT_RADIUS)
        if (escape != null) {
            context.caster.teleportTo(escape.x, escape.y, escape.z)
            context.level.sendParticles(ParticleTypes.END_ROD, escape.x, escape.y + 1.0, escape.z, 12, 0.3, 0.4, 0.3, 0.0)
            context.level.playSound(null, escape.x, escape.y, escape.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.4f)
        }

        context.messaging.actionBar(context.caster, Component.literal("📜 Лялька прийняла удар! (лишилось $dolls)"))
        if (dolls <= 0) {
            protecting.remove(casterId)
            absorbSubscriptions.remove(casterId)?.cancel()
            context.messaging.actionBar(context.caster, Component.literal("📜 Ляльки скінчились — захист знято"))
        }
        return true
    }

    private fun scatter(context: CastContext, origin: Vec3) {
        context.level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.1f, 0.8f)
        val at = origin.add(0.0, 1.4, 0.0)
        context.level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 18, 0.35, 0.15, 0.35, 0.0)
        context.level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 6, 0.3, 0.1, 0.3, 0.01)
    }

    private fun randomSafeSpotNear(context: CastContext, origin: Vec3, radius: Int): Vec3? {
        val level = context.level
        repeat(12) {
            val dx = Random.nextDouble(-radius.toDouble(), radius.toDouble())
            val dz = Random.nextDouble(-radius.toDouble(), radius.toDouble())
            val at = origin.add(dx, 0.0, dz)
            if (isSafe(level, at)) return at
            val up = at.add(0.0, 1.0, 0.0)
            if (isSafe(level, up)) return up
        }
        return null
    }

    private fun isSafe(level: ServerLevel, at: Vec3): Boolean {
        val pos = BlockPos.containing(at.x, at.y, at.z)
        val feet = level.getBlockState(pos)
        val head = level.getBlockState(pos.above())
        val ground = level.getBlockState(pos.below())
        return !feet.blocksMotion() && !head.blocksMotion() && ground.blocksMotion() &&
            feet.block != Blocks.LAVA
    }

    private fun craftAnimation(context: CastContext) {
        chime(context, SoundEvents.BOOK_PAGE_TURN, 1.2f)
        val at = context.caster.position()
        context.level.sendParticles(ParticleTypes.POOF, at.x, at.y + 1.2, at.z, 8, 0.4, 0.3, 0.4, 0.02)
    }

    private fun chime(context: CastContext, sound: SoundEvent, pitch: Float) {
        val at = context.origin
        context.level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.0f, pitch)
    }

    private companion object {
        const val BASE_COST = 60
        const val BASE_COOLDOWN_SECONDS = 8
    }
}
