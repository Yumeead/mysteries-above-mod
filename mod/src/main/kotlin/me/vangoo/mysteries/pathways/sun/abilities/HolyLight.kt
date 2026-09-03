package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.valueobjects.HolyAffinity
import me.vangoo.mysteries.ability.context.Beyonders
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.EntityTypeTags
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity

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
