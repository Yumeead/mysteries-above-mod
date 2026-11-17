package me.vangoo.mysteries.net.payload

import io.netty.handler.codec.DecoderException
import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
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

    /**
     * A sheet of paper thrown from [from] to [to]: it spins flat through the air for [ticks] and, if it
     * [hit] something, tears into scraps where it lands; if not, it loses its speed and flutters down.
     */
    data class Sheet(val from: Vec3, val to: Vec3, val ticks: Int, val hit: Boolean) : WorldEffectPayload {
        companion object {
            /** Blocks a thrown sheet covers in a tick. Shared, so the blow lands when the sheet does. */
            const val SPEED = 2.5

            /** Ticks a sheet takes to fly [distance] blocks: never less than one. */
            fun flight(distance: Double): Int = maxOf(1, kotlin.math.ceil(distance / SPEED).toInt())
        }
    }

    /**
     * An air bullet from [from] to [to]: it flies [ticks] at [SPEED] and [end]s by bursting on a body,
     * stopping at a wall or spending itself in the air. [powered] — the heavier shot, bigger in all.
     */
    data class AirShot(val from: Vec3, val to: Vec3, val ticks: Int, val powered: Boolean, val end: End) : WorldEffectPayload {
        enum class End { AIR, WALL, BODY }

        companion object {
            /** Blocks an air bullet covers in a tick. Shared, so the blow lands when the bullet does. */
            const val SPEED = 8.0

            /** Ticks a bullet takes to fly [distance] blocks: never less than one. */
            fun flight(distance: Double): Int = maxOf(1, kotlin.math.ceil(distance / SPEED).toInt())
        }
    }

    /**
     * A spirit pendulum hanging from [holder]'s hand for [ticks]: leaning towards [yaw] (degrees, as
     * an entity faces) and swinging with [strength] (0 → 1). [swing] says how it moves.
     */
    data class Pendulum(val holder: Int, val yaw: Float, val strength: Float, val swing: Swing, val ticks: Int) : WorldEffectPayload {
        enum class Swing {
            /** Leans and swings towards the mark. */
            SEEK,

            /** Trembles: the mark is a Sequence above the Seer, and its way is only half there. */
            FOG,

            /** Hangs dead still: the mark is in no world the chain can reach. */
            STILL,

            /** Spins and snaps: the mark is far above the Seer, or shielded. */
            SNAP,
        }
    }

    /** The dreamer's sight for [ticks]: grey and blurred, by [haze] (0 → 1). For the dreamer alone. */
    data class Vision(val ticks: Int, val haze: Float) : WorldEffectPayload

    /**
     * The body of [entity] becomes paper where it stood, at [at] facing [yaw] (degrees, as the body
     * turns): a paper figure in its very pose is struck through at once, falls apart into scraps and is
     * gone. The client takes the pose from the entity if it has it, and stands the figure upright if not.
     */
    data class Effigy(val entity: Int, val at: Vec3, val yaw: Float) : WorldEffectPayload

    /**
     * Words only this client sees, at [at] — or over the head of [anchor] and travelling with it, if it
     * is an entity id rather than [NO_ANCHOR]. They hang for [ticks] in [color] and fade.
     */
    data class Words(val at: Vec3, val anchor: Int, val text: Component, val color: Int, val ticks: Int) : WorldEffectPayload

    /**
     * A thread from the hand of [from] to the body of [to] — entity ids both, so it rides them frame
     * by frame. It runs out of the hand, holds for [ticks] and fades. With [pull] it goes taut and
     * flares as it is jerked, the way a puppet's string is.
     */
    data class Thread(val from: Int, val to: Int, val color: Int, val ticks: Int, val pull: Boolean) : WorldEffectPayload {
        companion object {
            /** Ticks a thread takes to run out of the hand to its end. */
            const val REACH_TICKS = 3
        }
    }

    /** [entities] glow for this client alone for [ticks]: an outline no one else sees. */
    data class Glow(val entities: List<Int>, val ticks: Int) : WorldEffectPayload

    /**
     * A ball of threads winding itself at [at] in [color] for [ticks], loop after loop, tighter as it
     * goes; wound, it closes on its middle and is gone — what is left there is the Characteristic.
     */
    data class Spool(val at: Vec3, val color: Int, val ticks: Int) : WorldEffectPayload

    /**
     * A face coming off a head or going onto one — the Faceless's trade. [from] and [to] are entity
     * ids; only [Kind.TAKE] travels between two heads, the rest stay on [from]'s.
     */
    data class Face(val kind: Kind, val from: Int, val to: Int) : WorldEffectPayload {
        enum class Kind {
            /** [from]'s face peels off, flies to [to]'s head and sinks into it. */
            TAKE,

            /** The face [from] wears now peels off, falls and tears to scraps. Sent before the skin goes back. */
            DROP,

            /** A blank paper mask settles on [from]'s face and sinks in. */
            MASK,

            /** The paper mask peels off [from]'s face, falls and tears to scraps. */
            UNMASK,

            /**
             * A scrap of paper with [from]'s face sketched on it comes off their head, flies to [to]'s
             * hand and is gone — the Faceless remembering a face. Sent to the Faceless alone.
             */
            SKETCH,
        }
    }

    /**
     * The Justiciar's law set in iron on [entity] (or in its hand), for [ticks]: [kind] says what is drawn.
     * It is gold for an instant and cools to iron; everything in it stands along the axes of the world.
     * [color] is the gold's RGB. The phases are the client's — ticks are all the server has to agree on.
     */
    data class Edict(val kind: EdictKind, val entity: Int, val color: Int, val ticks: Int) : WorldEffectPayload {
        companion object {
            /** Ticks Authority's aura builds for: three knocks, a pause, the blow. The client flares it on this tick. */
            const val SEAL_TICKS = 120

            /** Ticks between the knocks, each of which lifts the aura a step. */
            const val SEAL_KNOCK_TICKS = 40

            /** Ticks after the blow before the eyes go out. */
            const val STRIKE_TICKS = 2

            /** Ticks the aura takes to burn out after the blow: the tail of the packet's [ticks]. */
            const val AURA_FADE_TICKS = 20

            /** Ticks a hoop takes to break once its term is over: the tail of the packet's [ticks]. */
            const val BREAK_TICKS = 10

            /** Bonds: chains that break out of the ground, one every [BOND_STAGGER_TICKS], each coiling for [BOND_REACH_TICKS]. */
            const val BOND_CHAINS = 3
            const val BOND_STAGGER_TICKS = 3
            const val BOND_REACH_TICKS = 6

            /** The tick chain [chain] snaps tight on the body: the server's ratchet lands on it. */
            fun bondSnap(chain: Int): Int = chain * BOND_STAGGER_TICKS + BOND_REACH_TICKS

            /** The tick the crown, after the last chain, closes on the head. */
            val CROWN_SNAP: Int get() = bondSnap(BOND_CHAINS - 1) + 3

            /** Verdict, Exile: the ticks its wave of force takes to run out and die. */
            const val EXILE_TICKS = 16

            /** Verdict, Imprison: the walls close round the target one layer at a time. */
            const val IMPRISON_LAYERS = 3
            const val IMPRISON_LAYER_TICKS = 3

            /** Verdict, Death: the ticks the Judge's dash takes, the blow landing on the last. */
            const val DEATH_DASH_TICKS = 4

            /** Verdict, Flog: three strokes of the unseen whip, this many ticks apart. */
            const val FLOG_STROKES = 3
            const val FLOG_STROKE_TICKS = 6
        }
    }

    /**
     * A line between two bodies, riding both frame by frame: from [from]'s hand (the eyes, for a needle) to
     * [to]'s body. [shape] says how it is drawn and how long it lives ([Lash.life]).
     */
    /**
     * The Judge's Prohibition, a law over a round of ground: [sign] is what it forbids, [at] and [radius] where. With
     * [breach] set (an entity id, not [NO_ANCHOR]) it is not the law going up but someone breaking it: the sign
     * flashes over them and the law's weight comes down on them.
     */
    data class Prohibition(
        val at: Vec3,
        val radius: Float,
        val sign: ProhibitionSign,
        val ticks: Int,
        val color: Int,
        val breach: Int = NO_ANCHOR,
    ) : WorldEffectPayload {
        companion object {
            /** Ticks the law takes to spread from the Judge's palm to its edge. */
            const val SPREAD_TICKS = 30

            /** Ticks a breach is shown for. */
            const val BREACH_TICKS = 24
        }
    }

    data class Lash(val from: Int, val to: Int, val shape: Shape, val color: Int) : WorldEffectPayload {
        enum class Shape(val life: Int) {
            /** A segmented whip swung over the shoulder: it unrolls, cracks at [WHIP_CRACK_TICKS] and is drawn back. */
            WHIP(life = 14),

            /** Two bolts of lightning out of the eyes into the head, crackling; the target's spirit is knocked out of it. */
            NEEDLE(life = 10),

            /** A short arc of lightning that jumps from the blade onto what it struck. */
            BOLT(life = 6),

            /** Lightning crawling over the weapon in the caster's hand ([to] is the caster too), for its whole charge. */
            COAT(life = 200),

            /** Verdict, Death: the Judge's dash to the target, dragging afterimages, and the blow. */
            DASH(life = Edict.DEATH_DASH_TICKS + 14),

            /** Verdict, Flog: the strokes of an unseen whip, torn into the target's body. */
            FLOG(life = Edict.FLOG_STROKES * Edict.FLOG_STROKE_TICKS + 16),

            /**
             * Punishment: [from] (the Paladin) has chosen [to]. A dawn behind the Paladin, unseen shackles on the
             * target's feet, and in the target's own eyes the Paladin grows. As long as the mark lasts.
             */
            MARK(life = 30 * 20),
        }

        companion object {
            /** Ticks a needle or a bolt takes to reach its target. */
            const val REACH_TICKS = 2

            /** Ticks from the swing to the whip's crack. Shared, so the blow lands on the crack. */
            const val WHIP_CRACK_TICKS = 7
        }
    }

    companion object {

        const val NO_ANCHOR = -1
        const val MAX_SPOOL_TICKS = 1200

        /** A law outlasts a light: an edict or a prohibition may stand this long (the Prohibition, 45 s). */
        const val MAX_LAW_TICKS = 1200
        const val MAX_GLOW = 64
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
                        is Sheet -> {
                            buf.writeByte(SHEET)
                            Vec3.STREAM_CODEC.encode(buf, payload.from)
                            Vec3.STREAM_CODEC.encode(buf, payload.to)
                            buf.writeVarInt(payload.ticks)
                            buf.writeBoolean(payload.hit)
                        }
                        is AirShot -> {
                            buf.writeByte(AIR_SHOT)
                            Vec3.STREAM_CODEC.encode(buf, payload.from)
                            Vec3.STREAM_CODEC.encode(buf, payload.to)
                            buf.writeVarInt(payload.ticks)
                            buf.writeBoolean(payload.powered)
                            buf.writeByte(payload.end.ordinal)
                        }
                        is Pendulum -> {
                            buf.writeByte(PENDULUM)
                            buf.writeVarInt(payload.holder)
                            buf.writeFloat(payload.yaw)
                            buf.writeFloat(payload.strength)
                            buf.writeByte(payload.swing.ordinal)
                            buf.writeVarInt(payload.ticks)
                        }
                        is Vision -> {
                            buf.writeByte(VISION)
                            buf.writeVarInt(payload.ticks)
                            buf.writeFloat(payload.haze)
                        }
                        is Effigy -> {
                            buf.writeByte(EFFIGY)
                            buf.writeVarInt(payload.entity)
                            Vec3.STREAM_CODEC.encode(buf, payload.at)
                            buf.writeFloat(payload.yaw)
                        }
                        is Words -> {
                            buf.writeByte(WORDS)
                            Vec3.STREAM_CODEC.encode(buf, payload.at)
                            buf.writeVarInt(payload.anchor + 1)
                            ComponentSerialization.STREAM_CODEC.encode(buf, payload.text)
                            buf.writeInt(payload.color)
                            buf.writeVarInt(payload.ticks)
                        }
                        is Thread -> {
                            buf.writeByte(THREAD)
                            buf.writeVarInt(payload.from)
                            buf.writeVarInt(payload.to)
                            buf.writeInt(payload.color)
                            buf.writeVarInt(payload.ticks)
                            buf.writeBoolean(payload.pull)
                        }
                        is Glow -> {
                            buf.writeByte(GLOW)
                            buf.writeVarInt(payload.entities.size)
                            payload.entities.forEach(buf::writeVarInt)
                            buf.writeVarInt(payload.ticks)
                        }
                        is Spool -> {
                            buf.writeByte(SPOOL)
                            Vec3.STREAM_CODEC.encode(buf, payload.at)
                            buf.writeInt(payload.color)
                            buf.writeVarInt(payload.ticks)
                        }
                        is Face -> {
                            buf.writeByte(FACE)
                            buf.writeByte(payload.kind.ordinal)
                            buf.writeVarInt(payload.from)
                            buf.writeVarInt(payload.to)
                        }
                        is Edict -> {
                            buf.writeByte(EDICT)
                            buf.writeByte(payload.kind.ordinal)
                            buf.writeVarInt(payload.entity)
                            buf.writeInt(payload.color)
                            buf.writeVarInt(payload.ticks)
                        }
                        is Prohibition -> {
                            buf.writeByte(PROHIBITION)
                            Vec3.STREAM_CODEC.encode(buf, payload.at)
                            buf.writeFloat(payload.radius)
                            buf.writeByte(payload.sign.ordinal)
                            buf.writeVarInt(payload.ticks)
                            buf.writeInt(payload.color)
                            buf.writeVarInt(payload.breach)
                        }
                        is Lash -> {
                            buf.writeByte(LASH)
                            buf.writeVarInt(payload.from)
                            buf.writeVarInt(payload.to)
                            buf.writeByte(payload.shape.ordinal)
                            buf.writeInt(payload.color)
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
                        SHEET -> {
                            val from = Vec3.STREAM_CODEC.decode(buf)
                            var to = Vec3.STREAM_CODEC.decode(buf)
                            if (from.distanceTo(to) > MAX_PASSAGE) to = from.add(to.subtract(from).normalize().scale(MAX_PASSAGE))
                            Sheet(from, to, buf.readVarInt().coerceIn(1, MAX_TICKS), buf.readBoolean())
                        }
                        AIR_SHOT -> {
                            val from = Vec3.STREAM_CODEC.decode(buf)
                            var to = Vec3.STREAM_CODEC.decode(buf)
                            if (from.distanceTo(to) > MAX_PASSAGE) to = from.add(to.subtract(from).normalize().scale(MAX_PASSAGE))
                            val ticks = buf.readVarInt().coerceIn(1, MAX_TICKS)
                            val powered = buf.readBoolean()
                            val end = AirShot.End.entries.getOrNull(buf.readByte().toInt()) ?: throw DecoderException("Невідомий кінець кулі")
                            AirShot(from, to, ticks, powered, end)
                        }
                        PENDULUM -> Pendulum(
                            buf.readVarInt(),
                            buf.readFloat().takeIf { it.isFinite() } ?: 0f,
                            buf.readFloat().coerceIn(0f, 1f),
                            Pendulum.Swing.entries.getOrNull(buf.readByte().toInt()) ?: throw DecoderException("Невідомий розмах"),
                            buf.readVarInt().coerceIn(1, MAX_TICKS),
                        )
                        VISION -> Vision(buf.readVarInt().coerceIn(1, MAX_TICKS), buf.readFloat().coerceIn(0f, 1f))
                        EFFIGY -> Effigy(buf.readVarInt(), Vec3.STREAM_CODEC.decode(buf), buf.readFloat().takeIf { it.isFinite() } ?: 0f)
                        WORDS -> Words(Vec3.STREAM_CODEC.decode(buf), buf.readVarInt() - 1, ComponentSerialization.STREAM_CODEC.decode(buf),
                            buf.readInt(), buf.readVarInt().coerceIn(1, MAX_TICKS))
                        THREAD -> Thread(buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readVarInt().coerceIn(1, MAX_TICKS), buf.readBoolean())
                        GLOW -> {
                            val count = buf.readVarInt()
                            if (count !in 0..MAX_GLOW) throw DecoderException("Забагато обрисів: $count")
                            Glow(List(count) { buf.readVarInt() }, buf.readVarInt().coerceIn(1, MAX_TICKS))
                        }
                        SPOOL -> Spool(Vec3.STREAM_CODEC.decode(buf), buf.readInt(), buf.readVarInt().coerceIn(1, MAX_SPOOL_TICKS))
                        FACE -> {
                            val kind = Face.Kind.entries.getOrNull(buf.readByte().toInt())
                                ?: throw DecoderException("Невідомий рух обличчя")
                            Face(kind, buf.readVarInt(), buf.readVarInt())
                        }
                        EDICT -> {
                            val kind = EdictKind.entries.getOrNull(buf.readByte().toInt()) ?: throw DecoderException("Невідомий едикт")
                            Edict(kind, buf.readVarInt(), buf.readInt(), buf.readVarInt().coerceIn(1, MAX_LAW_TICKS))
                        }
                        LASH -> {
                            val from = buf.readVarInt()
                            val to = buf.readVarInt()
                            val shape = Lash.Shape.entries.getOrNull(buf.readByte().toInt()) ?: throw DecoderException("Невідома лінія")
                            Lash(from, to, shape, buf.readInt())
                        }
                        PROHIBITION -> {
                            val at = Vec3.STREAM_CODEC.decode(buf)
                            val radius = buf.readFloat().takeIf { it.isFinite() }?.coerceIn(0f, MAX_LENGTH.toFloat()) ?: 0f
                            val sign = ProhibitionSign.entries.getOrNull(buf.readByte().toInt()) ?: throw DecoderException("Невідома заборона")
                            Prohibition(at, radius, sign, buf.readVarInt().coerceIn(1, MAX_LAW_TICKS), buf.readInt(), buf.readVarInt())
                        }
                        else -> throw DecoderException("Невідома форма світла: $tag")
                    }
                },
            )

        private const val BEAM = 0
        private const val FLASH = 1
        private const val DOOR = 2
        private const val PASSAGE = 3
        private const val SHEET = 4
        private const val EFFIGY = 5
        private const val WORDS = 6
        private const val GLOW = 7
        private const val THREAD = 8
        private const val SPOOL = 9
        private const val FACE = 10
        private const val AIR_SHOT = 11
        private const val PENDULUM = 12
        private const val VISION = 13
        private const val EDICT = 14
        private const val LASH = 15
        private const val PROHIBITION = 16
    }
}

/** What an [WorldEffectPayload.Edict] draws. */
/** What a Prohibition forbids: the novel's own examples. */
enum class ProhibitionSign {
    FLIGHT,
    TELEPORTATION,
    INVISIBILITY,
    UNDEAD,
}

enum class EdictKind {
    /** A square frame over the hand, built a side at a time, closed and struck down into the palm: Authority. */
    SEAL,

    /** Three square hoops dropped down the body — ankles, belt, chest — that snap shut and cool to iron. */
    BONDS,

    /** A small hoop on the head, after the others: for a Beyonder, whose powers are locked. */
    CROWN,

    /** Verdict, Exile: a wave of force out from the Judge. */
    EXILE,

    /** Verdict, Imprison: transparent walls closing round the target in layers, a viscous liquid running down them. */
    IMPRISON,

    /** Verdict, Confinement: a transparent cell round where the target stood, rippling where it is struck. */
    CONFINE,
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
