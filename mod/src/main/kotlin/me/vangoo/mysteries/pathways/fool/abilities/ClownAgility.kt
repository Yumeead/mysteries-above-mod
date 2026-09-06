package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.WallClimbRules
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.grantAttribute
import me.vangoo.mysteries.ability.modifierId
import me.vangoo.mysteries.ability.revokeAttribute
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3

/**
 * Fool, Seq. 8 (Clown), sharpening into **Graceful Descent** at Seq. 6 (Faceless): **Clown
 * Agility**.
 *
 * A tumbler's body: quick on their feet, and a fall never hurts them. Fall immunity is
 * `FALL_DAMAGE_MULTIPLIER` at the damage's source, the same move Tyrant's `Balance` already made,
 * not a caught-and-cancelled event.
 *
 * ## One class, not two — the Seq. 6 upgrade is a number, not a new power
 *
 * The plugin had `GracefulDescent` replace this passive through a shared `AbilityIdentity`. The
 * mod does not allow that (a shared identity must carry a shared class — see `PaperCutter` and
 * Tyrant's `Balance`), and it costs nothing here: what the evolution actually changed was the
 * speed step and some particles, both of which read [Sequence] anyway. Above Seq. 6 the body
 * moves faster and starts leaving a trail on the way down.
 *
 * ## Two things a passive cannot get from an attribute
 *
 * Wall climbing has no vanilla hook — "sneak-jumped into a wall, keep it pressed" is genuinely
 * `PassiveTicker`'s tier of the search order, the same as in the plugin.
 *
 * The speed step has one, but granting it in [onActivate] alone is not enough: `PassiveTicker`
 * only calls that for a passive it has **not** activated yet, so a Beyonder who ascends 8 → 6
 * would keep the weaker modifier until they relogged. [tick] compares the live modifier against
 * what the current Sequence asks for once a second and re-grants only on a mismatch — stateless,
 * so nothing here goes stale across a respawn either.
 */
class ClownAgility : PermanentPassiveAbility(IDENTITY), Ticking {

    override val displayName: Component = Component.literal("Спритність клоуна")

    private val speedModifier = modifierId(identity.id, "movement_speed")
    private val fallModifier = modifierId(identity.id, "fall_damage_multiplier")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Акробатична спритність: швидкість +${(speedBonus(sequence) * 100).toInt()}% і повний імунітет до шкоди від падіння.\n" +
            "▪ Стрибок у стіну в присіді (Shift+Пробіл) — дертеся вгору\n" +
            if (sequence.level <= GRACE_UNLOCK_SEQUENCE) {
                "▪ Витончений спуск: падіння лишає слід, тіло слухається повністю"
            } else {
                "▪ На Посл. $GRACE_UNLOCK_SEQUENCE спритність переростає у витончений спуск"
            },
    )

    override fun onActivate(context: AbilityContext) {
        grantSpeed(context.caster, context.sequence)
        // 1.0 (base) + (-1.0) == 0: the fall's own damage is zeroed out at its source.
        context.caster.grantAttribute(Attributes.FALL_DAMAGE_MULTIPLIER, fallModifier, -1.0)
    }

    override fun onDeactivate(context: AbilityContext) {
        context.caster.revokeAttribute(Attributes.MOVEMENT_SPEED, speedModifier)
        context.caster.revokeAttribute(Attributes.FALL_DAMAGE_MULTIPLIER, fallModifier)
    }

    override fun tick(context: AbilityContext) {
        val player = context.caster
        if (context.scheduling.tick % TICKS_PER_SECOND == 0L) keepSpeedCurrent(player, context.sequence)
        if (context.sequence.level <= GRACE_UNLOCK_SEQUENCE) traceDescent(context, player)
        climbWall(context, player)
    }

    /** The Sequence moved and nobody re-activated the passive: put the right step back. */
    private fun keepSpeedCurrent(player: ServerPlayer, sequence: Sequence) {
        val granted = player.getAttribute(Attributes.MOVEMENT_SPEED)?.getModifier(speedModifier)
        if (granted != null && granted.amount() == speedBonus(sequence)) return
        grantSpeed(player, sequence)
    }

    private fun grantSpeed(player: ServerPlayer, sequence: Sequence) {
        player.grantAttribute(
            Attributes.MOVEMENT_SPEED, speedModifier,
            speedBonus(sequence), AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
        )
    }

    /** The name earns itself: from Seq. 6 a long drop is visibly slowed rather than merely survived. */
    private fun traceDescent(context: AbilityContext, player: ServerPlayer) {
        if (player.onGround() || player.deltaMovement.y > DESCENT_SPEED) return
        val at = player.position()
        context.level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 1, 0.2, 0.0, 0.2, 0.0)
    }

    private fun climbWall(context: AbilityContext, player: ServerPlayer) {
        if (!player.isShiftKeyDown) return
        if (player.onGround() || player.isInWater || player.isFallFlying) return

        val heading = Vec3(player.lookAngle.x, 0.0, player.lookAngle.z)
        if (heading.lengthSqr() < 0.01) return
        val toward = heading.normalize().scale(WallClimbRules.WALL_REACH)

        val level = context.level
        val ahead = player.position().add(toward)
        val aheadPos = BlockPos.containing(ahead.x, ahead.y, ahead.z)
        val aheadHead = BlockPos.containing(ahead.x, ahead.y + 1.0, ahead.z)
        val wall = level.getBlockState(aheadPos).blocksMotion() || level.getBlockState(aheadHead).blocksMotion()
        if (!wall) return

        val climb = WallClimbRules.climbSpeed(context.sequence)
        val push = heading.normalize().scale(0.05)
        player.setDeltaMovement(push.x, climb, push.z)
        player.resetFallDistance()

        if (context.scheduling.tick % 4 == 0L) {
            val at = player.position()
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 1.0, at.z, 2, 0.1, 0.2, 0.1, 0.0)
        }
    }

    private fun speedBonus(sequence: Sequence): Double =
        if (sequence.level <= GRACE_UNLOCK_SEQUENCE) GRACEFUL_SPEED_PERCENT else CLOWN_SPEED_PERCENT

    companion object {
        val IDENTITY: AbilityIdentity = AbilityIdentity.of("fool_agility")

        private const val GRACE_UNLOCK_SEQUENCE = 6

        /** +20% — the same as vanilla `SPEED I` gave, only without the step (WhiteTower's `Agility` precedent). */
        private const val CLOWN_SPEED_PERCENT = 0.2

        /** The plugin's `SPEED II` at Seq. 6, again without being nailed to the effect's 20% step. */
        private const val GRACEFUL_SPEED_PERCENT = 0.4

        /** Falling at least this fast counts as a descent worth marking. */
        private const val DESCENT_SPEED = -0.6
    }
}
