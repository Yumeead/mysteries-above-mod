package me.vangoo.mysteries.net.payload

import me.vangoo.mysteries.modId
import net.minecraft.core.UUIDUtil
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.item.component.ResolvableProfile
import java.util.Optional
import java.util.UUID

/**
 * Server → client: [wearer] now wears [face] — or, `null`, their own again.
 *
 * The player list already carries the borrowed name, and the skin when the face has textures. What
 * it cannot carry is the face's **id**: on the wire a player-info entry's profile is a name and
 * properties, and the client puts it back together under the entry's own id — the wearer's. So
 * where no textures exist (an offline server) the client picked the default skin by the wearer's
 * id, and the face never changed. This packet gives the mod's client the whole face, and it draws
 * it — on others, and on the wearer themselves in F5 and on the arm.
 */
data class WornFacePayload(val wearer: UUID, val face: ResolvableProfile?) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<WornFacePayload> = TYPE

    companion object {

        val TYPE: CustomPacketPayload.Type<WornFacePayload> = CustomPacketPayload.Type(modId("worn_face"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, WornFacePayload> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, WornFacePayload::wearer,
            ByteBufCodecs.optional(ResolvableProfile.STREAM_CODEC), { Optional.ofNullable(it.face) },
        ) { wearer, face -> WornFacePayload(wearer, face.orElse(null)) }.cast()
    }
}
