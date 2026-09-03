package me.vangoo.mysteries.pathways.error.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.pathways.error.ErrorTags
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

/**
 * Error, Seq. 9 (Marauder): **Shadow Theft**.
 *
 * The pathway's first power and its whole essence in miniature: step in behind someone, take
 * what is badly kept, and vanish. It breaks nothing and hits nobody — and that is exactly why it
 * works.
 *
 * ## The shadow became a condition rather than a name
 *
 * The plugin called the power "shadow theft" while there was no shadow in it: the theft always
 * went the same way, and the victim always got "someone rummaged through your things". Here the
 * light level in the victim's cell **decides** whether the theft went unnoticed: in the dark
 * (level ≤ [SHADOW_LIGHT]) the victim learns nothing, in the light they hear their own pocket.
 * The number needed for that is one the game already keeps
 * (`getMaxLocalRawBrightness`); there is no light bookkeeping of our own here and there will not be.
 *
 * ## What is taken is what is on the outside, not something random
 *
 * In the plugin the item was pulled by `nextInt(lootPool.size())` from the whole inventory — a
 * number instead of a form. A pickpocket takes what they can reach: first the off hand, then the
 * active slot, then the hotbar and only after that the backpack. A player carrying valuables in
 * the hotbar really does take more risk — and that is a decision they make themselves.
 *
 * What is untouchable lies in the `#mysteries_above:unstealable` tag rather than in an `if` full
 * of material names: artefacts, when they arrive, will be added there with a file.
 *
 * ## A miss costs nothing
 *
 * A wall behind the target's back is a `Failed`, i.e. no spirituality and no cooldown. In that
 * case the plugin teleported the player **into themselves** (the search for a spot hit a block
 * and returned the target's position), charged the price and reported success.
 */
class ShadowTheft : ActiveAbility(AbilityIdentity.of("error_shadow_theft")) {

    override val displayName: Component = Component.literal("Крадіжка тіні")

    override val spiritualityCost: Int = 55

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви зникаєте й з'являєтесь за спиною того, на кого дивитесь, забравши одну річ.\n" +
            "▪ Дальність ${range(sequence).toInt()} бл.; невидимість " +
            "${hiddenTicks(sequence) / TICKS_PER_SECOND} с\n" +
            "▪ У темряві (світло ≤ $SHADOW_LIGHT) жертва не помічає нічого\n" +
            "▪ Береться те, що ближче до руки: друга рука → активний слот → пояс → рюкзак\n" +
            "▪ Відкат $COOLDOWN_SECONDS с",
    )

    /** Stealing from a stronger Beyonder must not go unpunished. */
    override fun contestedTarget(context: CastContext): LivingEntity? =
        context.targeting.lookingAt(range(context.sequence))

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val level = context.level

        val target = context.targeting.lookingAt(range(context.sequence))
            ?: return AbilityOutcome.failed("Наведіть погляд на ціль")
        val landing = behind(level, target)
            ?: return AbilityOutcome.failed("За спиною ${target.name.string} немає куди стати")

        vanish(context, context.origin)
        caster.teleportTo(landing.x, landing.y, landing.z)
        caster.lookAt(EntityAnchorArgument.Anchor.EYES, target.eyePosition)
        appear(level, landing)

        val shadowed = level.getMaxLocalRawBrightness(target.blockPosition()) <= SHADOW_LIGHT
        caster.addEffect(
            MobEffectInstance(MobEffects.INVISIBILITY, hiddenTicks(context.sequence), 0, true, false, true),
        )

        val stolen = steal(target)
        notice(context, target, shadowed)

        if (stolen == null) return AbilityOutcome.done("Ви зайшли зі спини, але брати нема чого")
        if (!caster.addItem(stolen)) caster.drop(stolen, false)

        val what = stolen.hoverName.string
        return AbilityOutcome.done(
            if (shadowed) "Ви взяли $what — і тінь вас не видала" else "Ви взяли $what, але вас почули",
        )
    }

    /**
     * A free spot behind the target's back: exactly [STEP] back from their line of sight.
     *
     * No footing underneath is looked for separately — two empty cells are enough: the thief
     * appears where the back was just standing, and the back was standing on something.
     */
    private fun behind(level: ServerLevel, target: LivingEntity): Vec3? {
        val back = target.lookAngle.multiply(1.0, 0.0, 1.0).normalize().scale(STEP)
        val pos = BlockPos.containing(target.position().subtract(back))
        if (!level.getBlockState(pos).isAir || !level.getBlockState(pos.above()).isAir) return null
        return Vec3(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
    }

    /**
     * One thing out of whatever the thief could reach.
     *
     * The order of slots **is** the whole mechanic. On a mob there is nothing to take beyond
     * what is in its hand.
     */
    private fun steal(target: LivingEntity): ItemStack? {
        if (target !is ServerPlayer) {
            val held = target.getItemBySlot(EquipmentSlot.MAINHAND)
            if (held.isEmpty || untouchable(held)) return null
            val taken = held.copyWithCount(1)
            held.shrink(1)
            target.setItemSlot(EquipmentSlot.MAINHAND, held)
            return taken
        }

        val inventory = target.inventory
        val slot = reachOrder(inventory).firstOrNull { index ->
            val stack = inventory.getItem(index)
            !stack.isEmpty && !untouchable(stack)
        } ?: return null
        return inventory.removeItem(slot, 1)
    }

    private fun untouchable(stack: ItemStack): Boolean = stack.typeHolder().`is`(ErrorTags.UNSTEALABLE)

    /** The off hand → the active slot → the rest of the hotbar → the backpack. */
    private fun reachOrder(inventory: Inventory): List<Int> {
        val hotbar = (0 until Inventory.getSelectionSize()).toList()
        val backpack = (Inventory.getSelectionSize() until inventory.containerSize)
            .filter { it != Inventory.SLOT_OFFHAND }
        return (listOf(Inventory.SLOT_OFFHAND, inventory.selectedSlot) + hotbar + backpack).distinct()
    }

    /** In the light the victim feels the theft; in the shadow they do not. */
    private fun notice(context: CastContext, target: LivingEntity, shadowed: Boolean) {
        if (shadowed || target !is ServerPlayer) return
        context.level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 0.5f,
        )
        context.messaging.actionBar(target, Component.literal("Хтось порпався у ваших речах…"))
    }

    private fun vanish(context: CastContext, from: Vec3) {
        context.level.sendParticles(
            ModParticles.mote(context.effects.pathwayColor),
            from.x, from.y + 1.0, from.z, 40, 0.35, 0.8, 0.35, 0.0,
        )
        context.level.playSound(
            null, from.x, from.y, from.z,
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f,
        )
    }

    private fun appear(level: ServerLevel, at: Vec3) {
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.5, at.z, 30, 0.3, 0.5, 0.3, 0.01)
        level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 0.8f,
        )
    }

    private fun range(sequence: Sequence): Double =
        scale(BASE_RANGE, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private fun hiddenTicks(sequence: Sequence): Int =
        scale(BASE_HIDDEN_TICKS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val BASE_RANGE = 8
        const val BASE_HIDDEN_TICKS = 3 * TICKS_PER_SECOND
        const val COOLDOWN_SECONDS = 30

        /** The vanilla hostile-mob spawn threshold — the very number the game already calls darkness. */
        const val SHADOW_LIGHT = 7

        /** A step behind the back: closer and the thief is inside the target, further and it is no longer "from behind". */
        const val STEP = 1.2
    }
}
