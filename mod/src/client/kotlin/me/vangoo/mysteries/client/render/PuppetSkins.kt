package me.vangoo.mysteries.client.render

import com.mojang.authlib.GameProfile
import me.vangoo.mysteries.client.beyonder.ClientMarionettes
import me.vangoo.mysteries.entity.MarionetteEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.item.component.ResolvableProfile
import java.util.UUID
import java.util.function.Supplier

/**
 * Skins by face: for the marionettes themselves, and for the local player while inside one.
 *
 * A possessing Marionettist walks the world as the puppet (see `Marionettes.possess`): others see
 * the puppet, and the driver sees themselves — in F5 and in their first-person arm — wearing its
 * skin. The swap is one question asked by `AbstractClientPlayerSkinMixin`, answered here. A player
 * in a borrowed face (Shapeshifting) is drawn in it the same way — by everyone, themselves included
 * ([masks]): the player list cannot carry a face's id, see `WornFacePayload`.
 */
object PuppetSkins {

    /**
     * Kept for the client session. Bounded by how many distinct faces have ever been made into
     * puppets in one sitting — a handful, and vanilla's own player list is no smaller.
     */
    private val lookups = HashMap<UUID, Supplier<PlayerSkin>>()

    fun of(profile: GameProfile): PlayerSkin =
        lookups.getOrPut(profile.id) { Minecraft.getInstance().skinManager.createLookup(profile, false) }.get()

    /** Who wears whose face under Shapeshifting, by wearer — the local player among them. */
    val masks = HashMap<UUID, ResolvableProfile>()

    fun mask(wearer: UUID, face: ResolvableProfile?) {
        if (face == null) masks.remove(wearer) else masks[wearer] = face
    }

    /** The skin [player] should wear right now, or `null` for their own. */
    @JvmStatic
    fun worn(player: AbstractClientPlayer): PlayerSkin? {
        val client = Minecraft.getInstance()
        val possessing = if (player === client.player) ClientMarionettes.possessing else null
        if (possessing == null) return masks[player.uuid]?.let { of(it.partialProfile()) }
        val puppet = client.level?.getEntity(possessing) as? MarionetteEntity ?: return null
        return of(puppet.profile.partialProfile())
    }
}
