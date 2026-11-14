package me.vangoo.mysteries.net.payload

import io.netty.handler.codec.DecoderException
import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.phys.Vec3

/**
 * Server → client: a shape of light the client builds itself.
 *
 * A beam drawn by the server would be a stream of particle packets every tick for as long as it
 * holds; here it is one packet, and the client sows the light along the line on its own clock.
 * The flash goes the same way for a different reason: only the client knows where its camera
 * looks, so only it can say how hard the flash hits.
 *
 * Every number is clamped on reading: a broken packet must not make a client sow a beam to the
 * edge of the world.
 */
sealed interface WorldEffectPayload : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<WorldEffectPayload> = TYPE

    /**
     * A line of light from [from] to [to], sown anew every tick for [ticks]; then it fades.
     * [width] 0 is a thread, 1 a pillar a block across; [sourceWidth] is its width at [from] — a shaft
     * out of the sun is as broad as the sun up there. [color] is RGB.
     */
    data class Beam(
        val from: Vec3,
        val to: Vec3,
        val color: Int,
        val ticks: Int,
        val width: Float,
        val sourceWidth: Float = width,
    ) : WorldEffectPayload

    /** A flash at [at]: those within [radius] who look at it see their screen whiten by up to [strength]. */
    data class Flash(val at: Vec3, val color: Int, val radius: Float, val strength: Float) : WorldEffectPayload

    companion object {

        const val MAX_TICKS = 200
        const val MAX_WIDTH = 4f
        const val MAX_SOURCE_WIDTH = 32f
        const val MAX_LENGTH = 256.0

        val TYPE: CustomPacketPayload.Type<WorldEffectPayload> = CustomPacketPayload.Type(modId("world_effect"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, WorldEffectPayload> =
            CustomPacketPayload.codec(
                StreamMemberEncoder { payload, buf ->
                    when (payload) {
                        is Beam -> {
                            buf.writeByte(BEAM)
                            Vec3.STREAM_CODEC.encode(buf, payload.from)
                            Vec3.STREAM_CODEC.encode(buf, payload.to)
                            buf.writeInt(payload.color)
                            buf.writeVarInt(payload.ticks)
                            buf.writeFloat(payload.width)
                            buf.writeFloat(payload.sourceWidth)
                        }
                        is Flash -> {
                            buf.writeByte(FLASH)
                            Vec3.STREAM_CODEC.encode(buf, payload.at)
                            buf.writeInt(payload.color)
                            buf.writeFloat(payload.radius)
                            buf.writeFloat(payload.strength)
                        }
                    }
                },
                StreamDecoder { buf ->
                    when (val tag = buf.readByte().toInt()) {
                        BEAM -> {
                            var from = Vec3.STREAM_CODEC.decode(buf)
                            val to = Vec3.STREAM_CODEC.decode(buf)
                            // Cut from the source: where a beam lands is what it is about.
                            if (from.distanceTo(to) > MAX_LENGTH) from = to.add(from.subtract(to).normalize().scale(MAX_LENGTH))
                            Beam(from, to, buf.readInt(), buf.readVarInt().coerceIn(1, MAX_TICKS),
                                buf.readFloat().coerceIn(0f, MAX_WIDTH), buf.readFloat().coerceIn(0f, MAX_SOURCE_WIDTH))
                        }
                        FLASH -> Flash(Vec3.STREAM_CODEC.decode(buf), buf.readInt(), buf.readFloat().coerceIn(0f, MAX_LENGTH.toFloat()),
                            buf.readFloat().coerceIn(0f, 1f))
                        else -> throw DecoderException("Невідома форма світла: $tag")
                    }
                },
            )

        private const val BEAM = 0
        private const val FLASH = 1
    }
}
