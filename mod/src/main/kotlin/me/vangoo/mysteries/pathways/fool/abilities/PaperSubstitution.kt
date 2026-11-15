package me.vangoo.mysteries.pathways.fool.abilities

import com.mojang.serialization.Codec
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.DollBatch
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.EVENT_FOREVER
import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.ability.context.Subscription
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.effect.privateText
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.pathways.fool.gather
import me.vangoo.mysteries.pathways.fool.paperBody
import me.vangoo.mysteries.pathways.fool.scraps
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Relative
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
 *
 * ## The blow lands on paper
 *
 * The plugin spent a doll in a puff of smoke and teleported the Magician off with an Enderman's
 * sound. Here the body itself becomes the doll: where the blow lands there stands a paper figure in
 * the Magician's very pose, the blow cuts straight through it, and it falls apart into scraps and is
 * gone (`paperBody`) — while the Magician is already a few blocks away, stepping out of nothing with
 * a little paper still falling off them. The yank keeps no momentum: whatever they were doing, they
 * stand still where they land.
 */
class PaperSubstitution : ActiveAbility(AbilityIdentity.of("fool_paper_substitution")) {

    override val displayName: Component = Component.literal("Заміна паперовою лялькою")

    override val spiritualityCost: Int = BASE_COST

    override fun cooldownSeconds(sequence: Sequence): Int = BASE_COOLDOWN_SECONDS

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
        // Switching the guard is free either way: the ticket stays unpaid and no cooldown starts.
        if (protecting.remove(casterId)) {
            absorbSubscriptions.remove(casterId)?.cancel()
            chime(context, SoundEvents.IRON_DOOR_CLOSE, 1.4f)
            return AbilityOutcome.Awaiting()
        }
        if (dollsOf(context) <= 0) {
            return AbilityOutcome.failed("Немає ляльок — спершу складіть партію")
        }

        protecting.add(casterId)
        absorbSubscriptions[casterId]?.cancel()
        absorbSubscriptions[casterId] = context.events.on<IncomingDamage>(EVENT_FOREVER) { damage ->
            if (damage.victim.uuid != casterId || damage.amount < DollBatch.DAMAGE_THRESHOLD) return@on
            if (absorb(context)) damage.cancel()
        }
        // A doll is standing ready behind the Magician: a scrap or two of it shows for a moment.
        scraps(context.level, context.caster.position().add(0.0, 1.2, 0.0), READY_SCRAPS, 0.3, 0.02)
        chime(context, SoundEvents.BEACON_ACTIVATE, 1.5f)
        return AbilityOutcome.Awaiting()
    }

    private fun craftBatch(context: CastContext): AbilityOutcome {
        val casterId = context.caster.uuid
        val max = DollBatch.maxStored(context.sequence)
        val current = dollsOf(context)
        if (current >= max) return AbilityOutcome.failed("Ви вже накопичили максимум ляльок ($max)")

        val added = minOf(DollBatch.dollsPerCast(context.sequence), max - current)
        val paperNeeded = added * DollBatch.PAPER_PER_DOLL
        if (PaperInventory.count(context.caster) < paperNeeded) {
            return AbilityOutcome.failed("Потрібно $paperNeeded паперу в інвентарі")
        }

        PaperInventory.consume(context.caster, paperNeeded)
        context.caster.setAttached(PaperDolls.TYPE, current + added)

        craftAnimation(context, current + added)
        return AbilityOutcome.Done()
    }

    /** @return true if a doll took the hit. */
    private fun absorb(context: CastContext): Boolean {
        val casterId = context.caster.uuid
        var dolls = dollsOf(context)
        if (casterId !in protecting || dolls <= 0) return false

        dolls--
        val caster = context.caster
        caster.setAttached(PaperDolls.TYPE, dolls)
        val origin = caster.position()
        // The body where the blow lands is paper now; the figure is drawn from the packet, not from them.
        paperBody(context.level, caster)

        val escape = randomSafeSpotNear(context, origin, DollBatch.TELEPORT_RADIUS)
        if (escape != null) {
            // No momentum carried over: `teleportTo(x, y, z)` keeps the client's velocity (see Blink).
            caster.connection.teleport(PositionMoveRotation(escape, Vec3.ZERO, 0f, 0f), Relative.ROTATION)
            caster.resetFallDistance()
            scraps(context.level, escape.add(0.0, 1.3, 0.0), ARRIVAL_SCRAPS, 0.25, 0.015)
            context.level.playSound(null, escape.x, escape.y, escape.z, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.3f)
        }

        if (dolls <= 0) {
            protecting.remove(casterId)
            absorbSubscriptions.remove(casterId)?.cancel()
            context.messaging.hint(caster, Component.literal("Ляльки скінчились — захист знято"), Fizzle.NONE)
        }
        return true
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

    /** The paper folds in the hands; how many figurines are ready, the Magician reads off them. */
    private fun craftAnimation(context: CastContext, ready: Int) {
        chime(context, SoundEvents.BOOK_PAGE_TURN, 1.2f)
        val hands = context.eye.add(context.look.scale(0.6)).add(0.0, -0.35, 0.0)
        gather(context.level, hands, FOLD_SCRAPS)
        privateText(context.caster, hands, Component.literal("Паперових ляльок: $ready"), COUNT_TICKS)
    }

    private fun chime(context: CastContext, sound: SoundEvent, pitch: Float) {
        val at = context.origin
        context.level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.0f, pitch)
    }

    private fun dollsOf(context: CastContext): Int = context.caster.getAttachedOrElse(PaperDolls.TYPE, 0)

    private companion object {

        const val BASE_COST = 60
        const val BASE_COOLDOWN_SECONDS = 8
        const val READY_SCRAPS = 2
        const val ARRIVAL_SCRAPS = 4
        const val FOLD_SCRAPS = 10
        val COUNT_TICKS = 2 * TICKS_PER_SECOND
    }
}

/** How many dolls a Magician has folded — paper already spent, so it is saved with the player. */
object PaperDolls {
    // Lazy: Fabric registers attachments through a mixin, and unit tests have none.
    val TYPE: AttachmentType<Int> by lazy {
        AttachmentRegistry.create(modId("paper_dolls")) { it.persistent(Codec.INT).copyOnDeath() }
    }

    /** At mod init: an attachment unknown when a player loads is dropped from their save. */
    fun register() {
        TYPE
    }
}
