package me.vangoo.mysteries.client.render

import com.mojang.authlib.GameProfile
import me.vangoo.mysteries.client.beyonder.ClientMarionettes
import me.vangoo.mysteries.entity.MarionetteEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.entity.player.PlayerSkin
import java.util.UUID
import java.util.function.Supplier

/**
 * Skins by face: for the marionettes themselves, and for the local player while inside one.
 *
 * A possessing Marionettist walks the world as the puppet (see `Marionettes.possess`): others see
 * the puppet, and the driver sees themselves — in F5 and in their first-person arm — wearing its
 * skin. The swap is one question asked by `AbstractClientPlayerSkinMixin`, answered here.
 */
object PuppetSkins {

    /**
     * Kept for the client session. Bounded by how many distinct faces have ever been made into
     * puppets in one sitting — a handful, and vanilla's own player list is no smaller.
     */
    private val lookups = HashMap<UUID, Supplier<PlayerSkin>>()

    fun of(profile: GameProfile): PlayerSkin =
        lookups.getOrPut(profile.id) { Minecraft.getInstance().skinManager.createLookup(profile, false) }.get()

    /** The skin [player] should wear right now, or `null` for their own. */
    @JvmStatic
    fun worn(player: AbstractClientPlayer): PlayerSkin? {
        val client = Minecraft.getInstance()
        if (player !== client.player) return null
        val id = ClientMarionettes.possessing ?: return null
        val puppet = client.level?.getEntity(id) as? MarionetteEntity ?: return null
        return of(puppet.profile.partialProfile())
    }
}
