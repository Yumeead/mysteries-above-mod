package me.vangoo.mysteries.pathways.justiciar

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.event.ProhibitionViolated
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.ProhibitionSign
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * The Prohibitions standing now, wherever they stand: what a Judge has forbidden over which round of ground, and
 * until when. The Prohibition itself enforces most of its law from its own tick; what has to be stopped **before**
 * it happens asks here: a power that moves a body ([forbids], from `AbilityExecutor`) and an ender pearl or chorus
 * fruit about to be used ([install]).
 */
object Prohibitions {

    /** One law: [sign] forbidden within [radius] of [centre] in [level], until server tick [until]. */
    class Law(
        val level: ServerLevel,
        val centre: Vec3,
        val radius: Double,
        val sign: ProhibitionSign,
        val enforcer: UUID,
        val color: RgbColor,
        val until: Long,
    ) {
        fun covers(entity: Entity): Boolean =
            entity.level() === level && entity.position().distanceToSqr(centre) <= radius * radius
    }

    private val laws = mutableListOf<Law>()

    fun proclaim(law: Law) {
        laws += law
    }

    fun repeal(law: Law) {
        laws -= law
    }

    /** The law forbidding [sign] over where [entity] stands, if one does. */
    fun over(entity: Entity, sign: ProhibitionSign): Law? {
        val now = entity.level().server?.tickCount?.toLong() ?: return null
        laws.removeAll { it.until < now }
        return laws.firstOrNull { it.sign == sign && it.covers(entity) }
    }

    /**
     * Whether [caster] may not use [ability] where they stand: a power that carries the body from one place to another
     * under a Prohibition of teleportation. The cast is refused before it costs anything, and counts as a breach.
     */
    fun forbids(caster: ServerPlayer, ability: AbilityIdentity): Boolean {
        if (ability.id !in TELEPORTING) return false
        val law = over(caster, ProhibitionSign.TELEPORTATION) ?: return false
        if (law.enforcer == caster.uuid) return false
        breach(law, caster, "спробував телепортуватися")
        return true
    }

    /** Someone broke [law]: the sign flashes over them and the law's weight comes down; a Paladin may punish. */
    fun breach(law: Law, offender: Entity, deed: String) {
        val level = law.level
        ModChannels.WORLD_EFFECT.sendTo(
            PlayerLookup.around(level, offender.position(), AUDIENCE),
            WorldEffectPayload.Prohibition(law.centre, law.radius.toFloat(), law.sign, WorldEffectPayload.Prohibition.BREACH_TICKS, law.color.packed, offender.id),
        )
        level.playSound(null, offender.x, offender.y, offender.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.6f)
        if (offender is ServerPlayer && offender.uuid != law.enforcer) {
            AbilityServices.events.publish(ProhibitionViolated(law.enforcer, offender.uuid, deed))
        }
        // A Paladin's law punishes its breakers by itself.
        if (offender is LivingEntity && offender.uuid != law.enforcer) Punishments.onBreach(law, offender)
    }

    /** An ender pearl or a chorus fruit under a Prohibition of teleportation will not be used. */
    fun install() {
        UseItemCallback.EVENT.register { player, _, hand ->
            val item = player.getItemInHand(hand).item
            if (item != Items.ENDER_PEARL && item != Items.CHORUS_FRUIT) return@register InteractionResult.PASS
            val law = (player as? ServerPlayer)?.let { over(it, ProhibitionSign.TELEPORTATION) } ?: return@register InteractionResult.PASS
            if (law.enforcer == player.uuid) return@register InteractionResult.PASS
            breach(law, player, "спробував телепортуватися")
            InteractionResult.FAIL
        }
    }

    /**
     * The powers that carry a body from one place to another. Substitution is not here: the novel has the Judge
     * forbid stand-ins only from Seq. 4.
     */
    private val TELEPORTING = setOf(
        "door_blink", "door_travellers_door", "door_opening",
        "fool_flame_jump",
        "visionary_dream_traversal",
    )

    private const val AUDIENCE = 48.0
}
