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
import net.minecraft.core.SectionPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.Container
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import kotlin.math.roundToInt

/**
 * Error, Seq. 8 (Swindler): **Material Theft**, and from Seq. 6 (Prometheus) rather more than that.
 *
 * A thing comes to the hand by itself — out of a pocket beside you, out of a chest behind a wall,
 * off the ground. This is the theft that needs no line of sight, and that is its whole identity:
 * `ShadowTheft` has to see its mark and step in behind their back, this one has to see nothing.
 *
 * ## Two tiers in one class
 *
 * The Prometheus tier switches on the holder's Sequence rather than on a second registration —
 * the same shape as `SuperiorObservation`. The player's ability simply becomes stronger.
 *
 * | | Seq. 8–7 | Seq. 6 |
 * |---|---|---|
 * | reach | 5 бл. | **50 бл.** |
 * | what can be taken | spiritual materials | **anything not nailed down** |
 * | modes | one | **take / plant** |
 * | fire | — | **yes** |
 *
 * ## The filter is a tag, and at the lower tier it is the balance
 *
 * The plugin narrowed the theft to its own ingredients, and that narrowing was the balance: a
 * power that pulls an item through a wall must not be able to pull a totem. That role belongs to
 * `#mysteries_above:spiritual_material` — filled today with vanilla brewing matter and by the
 * ingredient economy later, with no line of Kotlin changing. At the Prometheus tier the filter
 * widens to everything except `#mysteries_above:unstealable`, which is where a totem still lives.
 *
 * ## Containers are found by chunk, never by walking a cube
 *
 * At five blocks a cube is 1 300 cells; at fifty it is a million, once per cast, on the server
 * thread. The game already keeps the answer — every chunk holds its block entities in a map — so
 * both tiers go through that map and keep an honest distance check. One path, not two.
 */
class MaterialTheft : ActiveAbility(AbilityIdentity.of("error_material_theft")) {

    override val displayName: Component = Component.literal("Крадіжка матеріалів")

    override val spiritualityCost: Int = 50

    /** The cooldown falls as the Sequence strengthens — the multiplier divides here, not multiplies. */
    override fun cooldownSeconds(sequence: Sequence): Int =
        (BASE_COOLDOWN_SECONDS / SequenceScaler.calculateMultiplier(sequence.level, SequenceScaler.ScalingStrategy.MODERATE))
            .roundToInt()
            .coerceAtLeast(MIN_COOLDOWN_SECONDS)

    override fun description(sequence: Sequence): Component = Component.literal(
        buildString {
            append("Річ сама йде до вашої руки — навіть крізь стіну.\n")
            append("▪ Дальність ${reach(sequence).toInt()} бл., дивитись не треба\n")
            if (prometheus(sequence)) {
                append("▪ Береться будь-що: з кишень, зі скринь, із землі\n")
                append("▪ Присівши, ви навпаки **підкидаєте** те, що в руці, тому, на кого дивитесь\n")
                append("▪ Вогонь поблизу гасне й лягає вам у руку вогняною кулею\n")
            } else {
                append("▪ Бере одну річ: із кишень поруч або зі скрині за стіною\n")
                append("▪ Тільки духовні матеріали — золото й зброя не піддаються\n")
            }
            append("▪ Відкат ${cooldownSeconds(sequence)} с")
        },
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val prometheus = prometheus(context.sequence)
        val reach = reach(context.sequence)

        if (prometheus && context.caster.isShiftKeyDown) return plant(context, reach)
        if (prometheus) stealFire(context, reach)?.let { return it }

        val loot = fromPockets(context, reach, prometheus)
            ?: fromGround(context, reach, prometheus)
            ?: fromContainers(context, reach, prometheus)
            ?: return AbilityOutcome.failed(
                if (prometheus) "Поряд немає нічого, що піддається" else "Поряд немає жодного духовного матеріалу",
            )

        val hand = context.origin.add(0.0, HAND_HEIGHT, 0.0)
        streak(context, loot.source, hand)

        if (!context.caster.addItem(loot.stack)) context.caster.drop(loot.stack, false)
        context.level.playSound(
            null, hand.x, hand.y, hand.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.4f,
        )
        return AbilityOutcome.done("${loot.stack.hoverName.string} — до вашої руки")
    }

    // ── Where a thing can come from ──────────────────────────────────────────────

    /** One thing out of someone else's pocket. The victim hears their own pocket, nothing more. */
    private fun fromPockets(context: CastContext, reach: Double, anything: Boolean): Loot? {
        for (neighbour in context.targeting.playersNear(reach)) {
            val inventory = neighbour.inventory
            val slot = (0 until inventory.containerSize)
                .firstOrNull { takeable(inventory.getItem(it), anything) } ?: continue
            notice(context, neighbour)
            return Loot(inventory.removeItem(slot, 1), neighbour.position().add(0.0, HAND_HEIGHT, 0.0))
        }
        return null
    }

    /**
     * A thing lying on the ground. Nobody owns it, so nothing is taken from anyone — the wiki
     * gives this one for free, and only from the Prometheus tier.
     */
    private fun fromGround(context: CastContext, reach: Double, anything: Boolean): Loot? {
        if (!anything) return null
        val box = context.caster.boundingBox.inflate(reach)
        val drop = context.level.getEntitiesOfClass(ItemEntity::class.java, box) { takeable(it.item, true) }
            .minByOrNull { it.distanceToSqr(context.caster) } ?: return null
        val taken = drop.item.copyWithCount(1)
        drop.item.shrink(1)
        if (drop.item.isEmpty) drop.discard()
        return Loot(taken, drop.position())
    }

    /**
     * One thing out of a container within reach — through walls and closed lids alike.
     *
     * The chunks the radius touches hand over their block entities directly; the honest distance
     * check stays, because a chunk is square and reach is round.
     */
    private fun fromContainers(context: CastContext, reach: Double, anything: Boolean): Loot? {
        val level = context.level
        val origin = context.caster.blockPosition()
        val chunkReach = SectionPos.blockToSectionCoord(reach.toInt()) + 1
        val centerX = SectionPos.blockToSectionCoord(origin.x)
        val centerZ = SectionPos.blockToSectionCoord(origin.z)
        val squared = reach * reach

        for (dx in -chunkReach..chunkReach) {
            for (dz in -chunkReach..chunkReach) {
                val chunk = level.chunkSource.getChunkNow(centerX + dx, centerZ + dz) ?: continue
                for ((pos, blockEntity) in chunk.blockEntities) {
                    if (blockEntity !is Container || pos.distSqr(origin) > squared) continue
                    val slot = (0 until blockEntity.containerSize)
                        .firstOrNull { takeable(blockEntity.getItem(it), anything) } ?: continue
                    level.playSound(
                        null, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.8f, 0.6f,
                    )
                    return Loot(blockEntity.removeItem(slot, 1), Vec3.atCenterOf(pos))
                }
            }
        }
        return null
    }

    // ── What the Prometheus tier adds ────────────────────────────────────────────

    /**
     * Fire itself is a thing that can be taken.
     *
     * Only from what is burning — searching a fifty-block cube for a flame block would cost a
     * million lookups for a side effect. Whoever burns nearby stops burning, and the fire comes
     * to the thief's hand as a charge.
     */
    private fun stealFire(context: CastContext, reach: Double): AbilityOutcome? {
        val burning = context.targeting.livingNear(reach).filter { it.isOnFire }
        if (burning.isEmpty() && !context.caster.isOnFire) return null

        burning.forEach { it.clearFire() }
        context.caster.clearFire()
        context.caster.addEffect(MobEffectInstance(MobEffects.FIRE_RESISTANCE, FIRE_RESISTANCE_TICKS, 0, false, true, true))
        val charge = ItemStack(Items.FIRE_CHARGE)
        if (!context.caster.addItem(charge)) context.caster.drop(charge, false)

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.8f,
        )
        return AbilityOutcome.done("Ви забрали вогонь собі")
    }

    /** The reverse theft: what is in the hand goes quietly into someone else's pocket. */
    private fun plant(context: CastContext, reach: Double): AbilityOutcome {
        val held = context.caster.mainHandItem
        if (held.isEmpty) return AbilityOutcome.failed("У руці порожньо — підкидати нема чого")

        val mark = context.targeting.lookingAt(reach) as? ServerPlayer
            ?: return AbilityOutcome.failed("Наведіть погляд на того, кому підкидаєте")

        val planted = held.copyWithCount(1)
        if (!mark.addItem(planted)) return AbilityOutcome.failed("У ${mark.name.string} немає місця")
        held.shrink(1)

        streak(context, context.origin.add(0.0, HAND_HEIGHT, 0.0), mark.position().add(0.0, HAND_HEIGHT, 0.0))
        return AbilityOutcome.done("${planted.hoverName.string} тепер у ${mark.name.string}, і він про це не знає")
    }

    // ── Odds and ends ────────────────────────────────────────────────────────────

    private fun takeable(stack: ItemStack, anything: Boolean): Boolean = when {
        stack.isEmpty -> false
        anything -> !stack.typeHolder().`is`(ErrorTags.UNSTEALABLE)
        else -> stack.typeHolder().`is`(ErrorTags.SPIRITUAL_MATERIAL)
    }

    private fun notice(context: CastContext, victim: ServerPlayer) {
        context.level.playSound(
            null, victim.x, victim.y, victim.z,
            SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 0.5f,
        )
        context.messaging.actionBar(victim, Component.literal("Щось вислизнуло з ваших речей…"))
    }

    /** The thing's path: a line of motes from where it lay to the thief's hand. */
    private fun streak(context: CastContext, from: Vec3, to: Vec3) {
        val mote: ParticleOptions = ModParticles.mote(context.effects.pathwayColor)
        val level: ServerLevel = context.level
        val steps = (from.distanceTo(to) / STREAK_STEP).toInt().coerceIn(1, MAX_STREAK_STEPS)
        for (i in 0..steps) {
            val point = from.lerp(to, i.toDouble() / steps)
            level.sendParticles(mote, point.x, point.y, point.z, 1, 0.04, 0.04, 0.04, 0.0)
        }
    }

    private fun prometheus(sequence: Sequence): Boolean = sequence.level <= PROMETHEUS_TIER

    private fun reach(sequence: Sequence): Double = if (prometheus(sequence)) PROMETHEUS_RANGE else RANGE

    private class Loot(val stack: ItemStack, val source: Vec3)

    private companion object {
        const val RANGE = 5.0
        const val PROMETHEUS_TIER = 6
        const val PROMETHEUS_RANGE = 50.0
        const val BASE_COOLDOWN_SECONDS = 40
        const val MIN_COOLDOWN_SECONDS = 10
        const val FIRE_RESISTANCE_TICKS = 10 * TICKS_PER_SECOND
        const val HAND_HEIGHT = 1.2
        const val STREAK_STEP = 0.35
        const val MAX_STREAK_STEPS = 120
    }
}
