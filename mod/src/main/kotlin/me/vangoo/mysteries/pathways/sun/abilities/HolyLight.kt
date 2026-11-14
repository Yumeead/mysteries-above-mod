package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.valueobjects.HolyAffinity
import me.vangoo.mysteries.ability.context.Beyonders
import me.vangoo.mysteries.effect.douse
import me.vangoo.mysteries.effect.kindle
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.EntityTypeTags
import net.minecraft.world.attribute.EnvironmentAttributes
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Sun pathway's shared layer: whom the light counts as **unclean** and what it does about it.
 *
 * The descendant of the plugin's `HolyTargetClassifier`, but with zero lines left of the type
 * list. There were ten `EntityType`s by hand — `ZOMBIE`, `HUSK`, `DROWNED`, … — i.e. a copy of a
 * vanilla tag that would have to be extended with every game update and that knew nothing about
 * undead from other mods.
 *
 * `SENSITIVE_TO_SMITE` is that same list, but as **data**: vanilla keeps it for the Smite
 * enchantment, other mods add their own to it, and a datapack can rewrite it entirely. The game
 * already has an answer to "what counts as undead", and it is better than ours.
 *
 * Players are classified not by a tag but by pathway ([HolyAffinity] in `core`) — that is a rule
 * of the project, not of the game, and so it lives with the rules.
 */

/**
 * Whom the light **helps**.
 *
 * Not "everyone who is not unclean": between the unclean and one's own stands the rest of the
 * world — creepers, spiders, cows. Healing a creeper because it is not undead is exactly the
 * flaw a split in two produces where there are three categories. One's own means players who did
 * not take a dark pathway.
 */
internal fun LivingEntity.isFaithful(beyonders: Beyonders): Boolean =
    this is ServerPlayer && !isProfane(beyonders)

/** A dark pathway or undead: on such a target the light strikes in full. */
internal fun LivingEntity.isProfane(beyonders: Beyonders): Boolean = when (this) {
    is ServerPlayer -> HolyAffinity.isDark(beyonders.of(uuid)?.pathway?.name)
    else -> type.builtInRegistryHolder().`is`(EntityTypeTags.SENSITIVE_TO_SMITE)
}

/**
 * A strike of holy light. Returns the damage dealt — zero means the target did not take it.
 *
 * The multiplier comes from [HolyAffinity]: full damage against the unclean, [HolyAffinity]`.NEUTRAL`
 * against the rest. The light deliberately does not tell friend from foe: it judges by the
 * target's nature, not by who is friends with whom. The only exception is the caster.
 */
internal fun smite(
    level: ServerLevel,
    caster: ServerPlayer,
    beyonders: Beyonders,
    target: LivingEntity,
    baseDamage: Int,
): Float {
    if (target === caster) return 0f
    val amount = (baseDamage * HolyAffinity.damageMultiplier(target.isProfane(beyonders))).toFloat()
    if (amount <= 0f) return 0f
    target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), amount)
    return amount
}

/**
 * Removes everything harmful from the target, leaving what is useful.
 *
 * An effect's category is vanilla data (`MobEffectCategory`), so the mod keeps no "bad effects"
 * list of its own: another mod that honestly marked its debuff `HARMFUL` gets cleansed too. A
 * copy of the list here would be the same mistake as the undead roster.
 */
internal fun cleanse(target: LivingEntity): Int {
    val harmful = target.activeEffects
        .filter { it.effect.value().category == MobEffectCategory.HARMFUL }
        .map { it.effect }
    harmful.forEach { target.removeEffect(it) }
    return harmful.size
}

/**
 * Real light that walks with someone: moves the light block from [lit] to [here] once they have
 * left it, and returns where it now burns (`null` — [here] was taken, the next step tries again).
 * Only air is lit, so the light never eats a block.
 */
internal fun carryLight(level: ServerLevel, lit: BlockPos?, here: BlockPos, brightness: Int): BlockPos? {
    if (here == lit) return lit
    lit?.let { douse(level, it) }
    return kindle(level, here, brightness)
}

/**
 * Light that rides [body] at [offset] from its crown — the way the halo rides the head: a latched
 * glyph whose position is the crown (so the client finds its owner) and whose deltas are the offset
 * (so network delay does not shift it). It lives four ticks — redraw it every two.
 */
internal fun rideLight(body: ServerPlayer, offset: Vec3, light: ParticleOptions) {
    body.level().sendParticles(light, body.x, body.y + body.bbHeight, body.z, 0, offset.x, offset.y, offset.z, 1.0)
}

/** A point on [body] as an offset from its crown: [right] and [forward] by where the body faces, [down] below the crown. */
internal fun fromCrown(body: ServerPlayer, right: Double, down: Double, forward: Double): Vec3 {
    val yaw = Math.toRadians(body.yBodyRot.toDouble())
    // Facing +z, the right hand is towards −x.
    return Vec3(-cos(yaw) * right - sin(yaw) * forward, -down, -sin(yaw) * right + cos(yaw) * forward)
}

/**
 * Where holy light comes from: the way to the sun in this world's sky, placed the way the sky places
 * its disc — so a shaft called in the morning slants in from the east, at noon it falls straight
 * down, and from the ground it runs up into the sun itself. A sun lower than [MIN_SUN_HEIGHT] would
 * send its light along the ground, and a set one has none to send: then the light falls from
 * straight above, as it always did at night.
 */
internal fun sunward(level: ServerLevel): Vec3 {
    val angle = Math.toRadians(level.environmentAttributes().getDimensionValue(EnvironmentAttributes.SUN_ANGLE).toDouble())
    // The sky turns its sun about the x axis and then a quarter turn about y: it rises in the east, +x.
    val toSun = Vec3(-sin(angle), cos(angle), 0.0)
    return if (toSun.y >= MIN_SUN_HEIGHT) toSun else UP
}

/**
 * Whether light coming along [toward] reaches [at]: nothing solid in the way for [distance] blocks.
 * What lies in chunks the server has not loaded counts as open — the check must not load the world.
 */
internal fun lightReaches(level: ServerLevel, at: Vec3, toward: Vec3, distance: Double): Boolean {
    var along = SKY_STEP
    while (along <= distance) {
        val pos = BlockPos.containing(at.add(toward.scale(along)))
        if (pos.y > level.maxY || !level.isLoaded(pos)) return true
        if (level.getBlockState(pos).blocksMotion()) return false
        along += SKY_STEP
    }
    return true
}

/**
 * How far a shaft from the sun is drawn. Far enough that, from the ground, it runs into the sun's
 * disc rather than ending in the air; short of what one packet carries.
 */
internal const val SUN_REACH = 240.0

/**
 * A shaft's width up at the sun: there it is as broad as the sun's disc looks, so from the ground it
 * is a band of the sun's own breadth running down out of it, not a straw. It narrows evenly to where it lands.
 */
internal val SUN_WIDTH = (SUN_REACH * SUN_DISC).toFloat()

/** How broad the sun's disc looks: blocks across per block of distance. */
private const val SUN_DISC = 0.055

private val UP = Vec3(0.0, 1.0, 0.0)
/** The sine of the lowest the sun may stand and still send its light: about 15° over the horizon. */
private const val MIN_SUN_HEIGHT = 0.26
private const val SKY_STEP = 0.5

/** Where the hands hang, as [fromCrown] reads them: a shoulder's width aside, an arm's length down. */
internal const val HAND_ASIDE = 0.36
internal const val HAND_DOWN = 1.05
