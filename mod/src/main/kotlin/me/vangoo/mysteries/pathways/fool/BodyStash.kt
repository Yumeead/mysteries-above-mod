package me.vangoo.mysteries.pathways.fool

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

/**
 * What a possessing Marionettist left at home: their own things, health and place.
 *
 * While inside a puppet the player carries the puppet's inventory and hit points (see
 * `Marionettes.possess`), so their own wait here — on the player, and **persistent**. A server
 * that goes down mid-possession saves the player with the puppet's things on; on the next join
 * this is still attached and puts everything back. Anything shorter-lived than the player's own
 * save file could lose their inventory, and that is the one thing this must never do.
 *
 * @property items every inventory slot, empties included, in container order
 */
data class BodyStash(
    val items: List<ItemStack>,
    val health: Float,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val dimension: Identifier,
) {
    companion object {

        private val CODEC: Codec<BodyStash> = RecordCodecBuilder.create { row ->
            row.group(
                ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(BodyStash::items),
                Codec.FLOAT.fieldOf("health").forGetter(BodyStash::health),
                Codec.DOUBLE.fieldOf("x").forGetter(BodyStash::x),
                Codec.DOUBLE.fieldOf("y").forGetter(BodyStash::y),
                Codec.DOUBLE.fieldOf("z").forGetter(BodyStash::z),
                Codec.FLOAT.fieldOf("yaw").forGetter(BodyStash::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(BodyStash::pitch),
                Identifier.CODEC.fieldOf("dimension").forGetter(BodyStash::dimension),
            ).apply(row, ::BodyStash)
        }

        val TYPE: AttachmentType<BodyStash> =
            AttachmentRegistry.create(modId("body_stash")) { it.persistent(CODEC) }

        /** Touches [TYPE] so the attachment is registered at start-up, before any player loads. */
        fun register() = Unit
    }
}
