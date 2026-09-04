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
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3

/**
 * Fool, Seq. 8 (Clown): **Clown Agility**.
 *
 * A tumbler's body: quick on their feet, and a fall never hurts them. Falls immune the same way
 * Tyrant's `Balance` already zeroes fall damage — `FALL_DAMAGE_MULTIPLIER` at its source, not a
 * caught-and-cancelled `EntityDamageEvent`.
 *
 * ## Wall climbing has no attribute to lean on
 *
 * There is no vanilla hook for "sneak-jumped into a wall, keep it pressed" — this is genuinely
 * `PassiveTicker`'s tier of the search order, the same as the plugin: a solid block ahead plus
 * sneaking plus airborne is checked every tick, and the vertical speed comes straight from
 * [WallClimbRules].
 *
 * [identity] is shared with [GracefulDescent] (Seq. 6): advancing swaps this instance for that
 * one, the same evolution pattern `PaperCutter`/`PaperWeaponry` use.
 */
class ClownAgility : PermanentPassiveAbility(IDENTITY), Ticking {

    override val displayName: Component = Component.literal("Спритність клоуна")

    private val speedModifier = modifierId(identity.id, "movement_speed")
    private val fallModifier = modifierId(identity.id, "fall_damage_multiplier")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Акробатична спритність: швидкість +1 і повний імунітет до шкоди від падіння.\n" +
            "▪ Стрибок у стіну в присіді (Shift+Пробіл) — дертеся вгору",
    )

    override fun onActivate(context: AbilityContext) {
        context.caster.grantAttribute(
            Attributes.MOVEMENT_SPEED, speedModifier,
            SPEED_BONUS_PERCENT, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
        )
        // 1.0 (base) + (-1.0) == 0: the fall's own damage is zeroed out at its source,
        // the same move Tyrant's `Balance` already made.
        context.caster.grantAttribute(Attributes.FALL_DAMAGE_MULTIPLIER, fallModifier, -1.0)
    }

    override fun onDeactivate(context: AbilityContext) {
        context.caster.revokeAttribute(Attributes.MOVEMENT_SPEED, speedModifier)
        context.caster.revokeAttribute(Attributes.FALL_DAMAGE_MULTIPLIER, fallModifier)
    }

    override fun tick(context: AbilityContext) {
        val player = context.caster
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

    companion object {
        val IDENTITY: AbilityIdentity = AbilityIdentity.of("fool_agility")
        /** +20% — the same as vanilla `SPEED I` gave, only without the step (WhiteTower's `Agility` precedent). */
        private const val SPEED_BONUS_PERCENT = 0.2
    }
}
