package me.vangoo.mysteries.pathways.death

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.world.SpiritWorld
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.Level

/**
 * The **Underworld** — the mod's second world, and the place a Gatekeeper's door actually leads.
 *
 * The plugin's Door to the Underworld "swallowed" a target by setting its health to zero: the
 * underworld was a word in the description and a number in the code. Here it is a dimension —
 * soul soil under a sky that never lightens, ash in the air — and whoever the gate swallows
 * **goes there**: a player spends the swallow on the far side under the Underworld's own
 * withering, and is spat back out where the gate stood when it lets go.
 *
 * Like the spirit world it is data (`data/mysteries_above/dimension*`, `worldgen/.../underworld.json`);
 * there is exactly as much code here as it takes to take someone in and bring them back.
 *
 * ## Coming back survives anything
 *
 * Where to return is written onto the player as a persistent attachment the moment they are
 * swallowed — not held in the mod's memory, as the spirit world does with its game modes. A
 * player who logs out in the dark, or a server that crashes with them there, finds them back
 * where they were taken from on the next join. Dying there needs nothing: death takes the player
 * out of the Underworld on its own, and the attachment is not copied onto the respawned body.
 */
object Underworld {

    val LEVEL: ResourceKey<Level> = ResourceKey.create(Registries.DIMENSION, modId("underworld"))

    private data class Home(val dimension: Identifier, val x: Double, val y: Double, val z: Double)

    private val HOME_CODEC: Codec<Home> = RecordCodecBuilder.create { row ->
        row.group(
            Identifier.CODEC.fieldOf("dimension").forGetter(Home::dimension),
            Codec.DOUBLE.fieldOf("x").forGetter(Home::x),
            Codec.DOUBLE.fieldOf("y").forGetter(Home::y),
            Codec.DOUBLE.fieldOf("z").forGetter(Home::z),
        ).apply(row, ::Home)
    }

    private val HOME: AttachmentType<Home> by lazy {
        AttachmentRegistry.create(modId("underworld_home")) { it.persistent(HOME_CODEC) }
    }

    fun holds(player: ServerPlayer): Boolean = player.level().dimension() == LEVEL

    fun of(level: ServerLevel): ServerLevel? = level.server.getLevel(LEVEL)

    /**
     * Takes [player] through the gate for [ticks], then brings them back to where they stood.
     * `false` — the Underworld is missing (a datapack removed it), or they are already there.
     */
    fun swallow(player: ServerPlayer, ticks: Int): Boolean {
        if (holds(player) || player.hasAttached(HOME)) return false
        val underworld = of(player.level()) ?: return false

        player.setAttached(HOME, Home(player.level().dimension().identifier(), player.x, player.y, player.z))
        val landing = SpiritWorld.surface(underworld, player.x, player.z)
        player.teleportTo(underworld, player.x, landing, player.z, emptySet(), player.yRot, player.xRot, true)
        player.resetFallDistance()
        player.addEffect(MobEffectInstance(MobEffects.WITHER, ticks, 1))
        player.addEffect(MobEffectInstance(MobEffects.DARKNESS, ticks + 40, 0))
        underworld.playSound(null, player.x, player.y, player.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.0f, 0.4f)
        AbilityServices.messaging.tell(player, Component.literal("☠ Брама Загробного Світу проковтнула вас"))

        val id = player.uuid
        AbilityServices.scheduling.after(ticks) { AbilityServices.playerOf(id)?.let(::release) }
        return true
    }

    /** Back out — to where the gate took them, or to the world spawn if that world is gone. */
    fun release(player: ServerPlayer) {
        val home = player.removeAttached(HOME) ?: return
        if (!holds(player)) return
        val server = player.level().server
        val level = server.getLevel(ResourceKey.create(Registries.DIMENSION, home.dimension)) ?: server.overworld()
        player.teleportTo(level, home.x, home.y, home.z, emptySet(), player.yRot, player.xRot, true)
        player.resetFallDistance()
        player.removeEffect(MobEffects.WITHER)
        level.playSound(null, home.x, home.y, home.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.8f, 0.6f)
        AbilityServices.messaging.tell(player, Component.literal("☠ Загробний Світ відпустив вас"))
    }

    fun install() {
        // Attachment types register lazily — at mod init, here, never in a unit test that has no Fabric mixins.
        HOME
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> release(handler.player) }
    }
}
