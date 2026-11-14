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

    /**
     * A door whose threshold's middle stands at [footing], opening **into** [facing] — an angle about
     * Y in radians, the direction being `(cos, 0, sin)`: into the wall, or the way one steps through.
     * [kind] says how it comes and goes; it stands open for [holdTicks]. Its rim is [color]; behind it
     * a corridor runs [depth] blocks in and glows [far] at its end — the colour of where it leads.
     * Depth 0 is no corridor at all.
     */
    data class Door(
        val footing: Vec3,
        val facing: Float,
        val kind: DoorKind,
        val holdTicks: Int,
        val depth: Float,
        val color: Int,
        val far: Int,
    ) : WorldEffectPayload

    /**
     * A passage folded through space from [from] to [to] — arch after arch of the door's own outline. It
     * builds itself out over [delay] ticks while the doors at its ends open, then for [flight] ticks the
     * Traveller rushes along it ([travelled] says how far), and then it goes out behind them.
     */
    data class Passage(val from: Vec3, val to: Vec3, val color: Int, val delay: Int, val flight: Int) : WorldEffectPayload {
        companion object {
            /**
             * How much of the way is behind the Traveller at [t] (0 → 1) of the flight: a sharp start that
             * eases into the arrival. Shared, so the arches flare exactly where the server has them.
             */
            fun travelled(t: Float): Float = t.coerceIn(0f, 1f).let { 1 - (1 - it) * (1 - it) }
        }
    }

    companion object {

        const val MAX_TICKS = 200
        const val MAX_WIDTH = 4f
        const val MAX_SOURCE_WIDTH = 32f
        const val MAX_LENGTH = 256.0
        const val MAX_DEPTH = 8f
        const val MAX_PASSAGE = 64.0

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
                        is Door -> {
                            buf.writeByte(DOOR)
                            Vec3.STREAM_CODEC.encode(buf, payload.footing)
                            buf.writeFloat(payload.facing)
                            buf.writeByte(payload.kind.ordinal)
                            buf.writeVarInt(payload.holdTicks)
                            buf.writeFloat(payload.depth)
                            buf.writeInt(payload.color)
                            buf.writeInt(payload.far)
                        }
                        is Passage -> {
                            buf.writeByte(PASSAGE)
                            Vec3.STREAM_CODEC.encode(buf, payload.from)
                            Vec3.STREAM_CODEC.encode(buf, payload.to)
                            buf.writeInt(payload.color)
                            buf.writeVarInt(payload.delay)
                            buf.writeVarInt(payload.flight)
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
                        DOOR -> {
                            val footing = Vec3.STREAM_CODEC.decode(buf)
                            val facing = buf.readFloat().takeIf { it.isFinite() } ?: 0f
                            val kind = DoorKind.entries.getOrNull(buf.readByte().toInt()) ?: throw DecoderException("Невідомі двері")
                            Door(footing, facing, kind, buf.readVarInt().coerceIn(0, MAX_TICKS), buf.readFloat().coerceIn(0f, MAX_DEPTH),
                                buf.readInt(), buf.readInt())
                        }
                        PASSAGE -> {
                            val from = Vec3.STREAM_CODEC.decode(buf)
                            var to = Vec3.STREAM_CODEC.decode(buf)
                            if (from.distanceTo(to) > MAX_PASSAGE) to = from.add(to.subtract(from).normalize().scale(MAX_PASSAGE))
                            Passage(from, to, buf.readInt(), buf.readVarInt().coerceIn(0, MAX_TICKS), buf.readVarInt().coerceIn(1, MAX_TICKS))
                        }
                        else -> throw DecoderException("Невідома форма світла: $tag")
                    }
                },
            )

        private const val BEAM = 0
        private const val FLASH = 1
        private const val DOOR = 2
        private const val PASSAGE = 3
    }
}

/**
 * How a door comes and goes, in ticks of each phase — in this order, any of them possibly none:
 * a line widens into a door ([unfold]), its outline is cut round the arch ([cut]), its leaves swing
 * open ([open]), it stands open (the packet's hold), they swing shut ([close]), it narrows back into
 * a line ([fold]), and what is left of its outline goes out pixel by pixel ([scar]).
 *
 * Shared by both sides so the server's passage waits exactly as long as the leaves take to open.
 */
enum class DoorKind(
    val unfold: Int,
    val cut: Int,
    val open: Int,
    val close: Int,
    val fold: Int,
    val scar: Int,
    val leaves: Leaves,
) {
    /** A door in a wall: a crack runs round the arch, and the wall's own face swings open on its jambs. */
    WALL(unfold = 0, cut = 5, open = 3, close = 5, fold = 0, scar = 40, leaves = Leaves.WALL),

    /** A door in the air: its outline is drawn, then the corridor widens out of a line between the jambs. */
    FREE(unfold = 0, cut = 8, open = 6, close = 8, fold = 0, scar = 30, leaves = Leaves.NONE),

    /**
     * Either end of a blink: a line widens into a pale door that swings open at once, stands while the
     * Traveller rushes through, shuts behind them and folds away.
     */
    BLINK(unfold = 1, cut = 0, open = 2, close = 3, fold = 2, scar = 0, leaves = Leaves.PLAIN);

    /** Ticks from the packet until the door stands fully open. */
    val opened: Int get() = unfold + cut + open

    /** Ticks from the packet until the door is shut, held for [hold]. */
    fun shut(hold: Int): Int = opened + hold + close

    /** Ticks from the packet until nothing of the door is left. */
    fun life(hold: Int): Int = shut(hold) + fold + scar

    /** What swings open: the wall's own surface, a pale pane of the pathway's light, or nothing at all. */
    enum class Leaves { NONE, WALL, PLAIN }
}
