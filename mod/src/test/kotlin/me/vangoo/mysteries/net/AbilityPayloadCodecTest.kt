package me.vangoo.mysteries.net

import io.netty.buffer.Unpooled
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.GameRegistries
import me.vangoo.mysteries.net.payload.AbilityActionPayload
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import me.vangoo.domain.valueobjects.Mastery
import me.vangoo.domain.valueobjects.SanityLoss
import me.vangoo.mysteries.net.payload.AbilityStatePayload
import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.net.payload.HintPayload
import me.vangoo.mysteries.net.payload.DoorKind
import me.vangoo.mysteries.net.payload.EdictKind
import me.vangoo.mysteries.net.payload.ProhibitionSign
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.minecraft.world.phys.Vec3
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * A round trip of three packets: the registries come up, the world does not.
 *
 * A write/read desync is the quietest class of bug in mods: it does not crash, it shifts the
 * rest of the buffer and hands back rubbish a field lower. Here every packet travels there and
 * back, including the two forms easiest to get wrong: an **empty roster** (the player is not a
 * Beyonder) and `identity == null` ("whatever is already selected").
 *
 * The buffer being empty at the end is as much a part of the check as the fields matching: a
 * packet that read less than it wrote would eat the next one in the game.
 */
class AbilityPayloadCodecTest {

    companion object {
        /** `ItemStack.STREAM_CODEC` writes an item by registry — without it the roster will not travel. */
        @JvmStatic
        @BeforeAll
        fun bootstrapRegistries() = GameRegistries.bootstrap()
    }

    private val registries: RegistryAccess = GameRegistries.access()

    @Test
    @DisplayName("Порожній ростер проїжджає порожнім")
    fun emptyRosterRoundTrips() {
        val decoded = roundTrip(AbilityRosterPayload.NONE, AbilityRosterPayload.CODEC)

        assertNull(decoded.roster)
    }

    @Test
    @DisplayName("Ростер без здібностей — це не ростер без шляху")
    fun rosterWithoutAbilitiesKeepsItsHeader() {
        val payload = AbilityRosterPayload(
            AbilityRosterPayload.Roster("Fool", Sequence(9), Component.literal("Клоун"), emptyList()),
        )

        val roster = requireNotNull(roundTrip(payload, AbilityRosterPayload.CODEC).roster)

        assertEquals("Fool", roster.pathwayId)
        assertEquals(9, roster.sequence.level)
        assertEquals("Клоун", roster.sequenceName.string)
        assertTrue(roster.entries.isEmpty())
        assertNull(roster.worn)
    }

    @Test
    @DisplayName("Ростер у ляльці везе шлях тіла, яке носить Маріонетист")
    fun wornBodyRoundTrips() {
        val worn = AbilityRosterPayload.Worn("Door", Sequence(7))
        val payload = AbilityRosterPayload(
            AbilityRosterPayload.Roster("Fool", Sequence(5), Component.literal("Маріонетист"), emptyList(), worn),
        )

        assertEquals(worn, requireNotNull(roundTrip(payload, AbilityRosterPayload.CODEC).roster).worn)
    }

    @Test
    @DisplayName("Ростер зі здібностями доїжджає полем у поле, разом з іконками")
    fun fullRosterRoundTrips() {
        val payload = AbilityRosterPayload(
            AbilityRosterPayload.Roster(
                pathwayId = "Tyrant",
                sequence = Sequence(7),
                sequenceName = Component.literal("Seafarer"),
                entries = listOf(
                    entry("tyrant_illusory_scales", AbilityType.PERMANENT_PASSIVE, 0, 0, 0, Items.PRISMARINE_SHARD),
                    entry("tyrant_restorative_water", AbilityType.ACTIVE, 35, 0, 20, Items.AMETHYST_SHARD),
                    entry("tyrant_storm_eye", AbilityType.TOGGLEABLE_PASSIVE, 40, 5, 12, Items.ECHO_SHARD),
                ),
            ),
        )

        val roster = requireNotNull(roundTrip(payload, AbilityRosterPayload.CODEC).roster)
        val expected = requireNotNull(payload.roster).entries

        assertEquals(expected.size, roster.entries.size)
        expected.zip(roster.entries).forEach { (before, after) ->
            assertEquals(before.identity, after.identity)
            assertEquals(before.name.string, after.name.string)
            assertEquals(before.description.string, after.description.string)
            assertEquals(before.type, after.type)
            assertEquals(before.cost, after.cost)
            assertEquals(before.upkeep, after.upkeep)
            assertEquals(before.cooldownSeconds, after.cooldownSeconds)
            // `ItemStack` has no structural `equals` — it is compared by contents.
            assertTrue(ItemStack.matches(before.icon, after.icon), "іконка ${before.identity}")
        }
    }

    @Test
    @DisplayName("Стан без обраної здібності й без живих записів проїжджає порожнім")
    fun bareStateRoundTrips() {
        val payload = AbilityStatePayload(12_345, Spirituality(0, 300), SanityLoss.none(), null, emptyList())

        val decoded = roundTrip(payload, AbilityStatePayload.CODEC)

        assertEquals(payload, decoded)
        assertNull(decoded.selected)
    }

    @Test
    @DisplayName("Стан із перепочинками, тоглями й сесіями доїжджає як є")
    fun fullStateRoundTrips() {
        val payload = AbilityStatePayload(
            serverTick = 987_654,
            spirituality = Spirituality(124, 300),
            sanityLoss = SanityLoss.of(73),
            selected = "tyrant_restorative_water",
            live = listOf(
                AbilityStatePayload.Live("tyrant_restorative_water", 988_054, false, false),
                AbilityStatePayload.Live("tyrant_storm_eye", 0, true, false),
                AbilityStatePayload.Live("tyrant_illusory_scales", 0, false, true),
            ),
            mastery = Mastery.of(42.37),
        )

        // The state is flat numbers and strings, so plain equality works here.
        assertEquals(payload, roundTrip(payload, AbilityStatePayload.CODEC))
    }

    @Test
    @DisplayName("Розхитаність розуму доїжджає на обох межах шкали")
    fun sanityBoundsRoundTrip() {
        for (scale in intArrayOf(0, 1, 99, 100)) {
            val payload = AbilityStatePayload(
                serverTick = 1,
                spirituality = Spirituality(10, 300),
                sanityLoss = SanityLoss.of(scale),
                selected = null,
                live = emptyList(),
            )

            assertEquals(payload, roundTrip(payload, AbilityStatePayload.CODEC), "глузд $scale")
        }
    }

    @Test
    @DisplayName("Дія без ідентичності означає «те, що вже обрано»")
    fun actionWithoutIdentityRoundTrips() {
        val decoded = roundTrip(AbilityActionPayload.castSelected(), AbilityActionPayload.CODEC)

        assertEquals(AbilityActionPayload.Action.CAST, decoded.action)
        assertNull(decoded.identity)
    }

    @Test
    @DisplayName("Кожна дія проїжджає зі своєю ідентичністю")
    fun everyActionRoundTrips() {
        AbilityActionPayload.Action.entries.forEach { action ->
            val payload = AbilityActionPayload(action, "tyrant_restorative_water")
            assertEquals(payload, roundTrip(payload, AbilityActionPayload.CODEC))
        }
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────

    private fun entry(
        identity: String,
        type: AbilityType,
        cost: Int,
        upkeep: Int,
        cooldownSeconds: Int,
        item: net.minecraft.world.item.Item,
    ) = AbilityRosterPayload.Entry(
        identity = identity,
        name = Component.literal("Назва $identity"),
        description = Component.literal("Опис $identity з числами: 12 с, 34 очка"),
        type = type,
        cost = cost,
        upkeep = upkeep,
        cooldownSeconds = cooldownSeconds,
        icon = ItemStack(item),
    )

    @Test
    @DisplayName("Підказка везе і зрив, і текст")
    fun hintRoundTrips() {
        val payload = HintPayload(Fizzle.EMPTY, Component.literal("Недостатньо духовності: потрібно 30, є 12"))

        assertEquals(payload, roundTrip(payload, HintPayload.CODEC))
    }

    @Test
    @DisplayName("Обидві форми світла проїжджають кожна своєю")
    fun worldEffectsRoundTrip() {
        val beam = WorldEffectPayload.Beam(Vec3(1.5, 96.0, -3.25), Vec3(1.5, 64.0, -3.25), 0xFFD27A, 8, 1f, 12f)
        val flash = WorldEffectPayload.Flash(Vec3(0.0, 70.0, 0.0), 0xFFFFFF, 24f, 0.8f)

        assertEquals(beam, roundTrip(beam, WorldEffectPayload.CODEC))
        assertEquals(flash, roundTrip(flash, WorldEffectPayload.CODEC))
    }

    @Test
    @DisplayName("Двері й прохід проїжджають, а глибина й строк — у межах")
    fun doorsRoundTripAndAreClamped() {
        val door = WorldEffectPayload.Door(Vec3(3.5, 64.0, -2.0), 1.5f, DoorKind.WALL, 100, 3.75f, 0x6AE6F7, 0xD8F7FC)
        val passage = WorldEffectPayload.Passage(Vec3(0.0, 65.0, 0.0), Vec3(0.0, 65.0, 15.0), 0xB0F0FA, 3, 4)

        assertEquals(door, roundTrip(door, WorldEffectPayload.CODEC))
        assertEquals(passage, roundTrip(passage, WorldEffectPayload.CODEC))

        val wild = roundTrip(door.copy(holdTicks = 10_000, depth = 1e9f), WorldEffectPayload.CODEC) as WorldEffectPayload.Door
        assertEquals(WorldEffectPayload.MAX_TICKS, wild.holdTicks)
        assertEquals(WorldEffectPayload.MAX_DEPTH, wild.depth)
        val long = roundTrip(passage.copy(to = Vec3(0.0, 65.0, 1e6)), WorldEffectPayload.CODEC) as WorldEffectPayload.Passage
        assertEquals(WorldEffectPayload.MAX_PASSAGE, long.from.distanceTo(long.to), 1e-6)
    }

    @Test
    @DisplayName("Папір, нитки, клубок, обличчя, слова й обриси проїжджають; слова без прив'язки лишаються без неї")
    fun paperAndPrivateSightRoundTrip() {
        val sheet = WorldEffectPayload.Sheet(Vec3(0.0, 65.6, 0.0), Vec3(4.0, 65.0, 9.0), 4, true)
        val body = WorldEffectPayload.Effigy(42, Vec3(1.5, 64.0, -7.5), 135f)
        val loose = WorldEffectPayload.Words(Vec3(0.5, 66.0, 0.5), WorldEffectPayload.NO_ANCHOR, Component.literal("Так"), 0xECECF4, 60)
        val riding = loose.copy(anchor = 0)
        val glow = WorldEffectPayload.Glow(listOf(3, 17, 256), 40)
        val thread = WorldEffectPayload.Thread(3, 17, 0xA0A0FF, 12, true)
        val spool = WorldEffectPayload.Spool(Vec3(1.5, 64.6, -7.5), 0x8040C0, 600)
        val faces = WorldEffectPayload.Face.Kind.entries.map { WorldEffectPayload.Face(it, 3, 17) }

        (listOf(sheet, body, loose, riding, glow, thread, spool) + faces).forEach { assertEquals(it, roundTrip(it, WorldEffectPayload.CODEC)) }
        assertEquals(4, WorldEffectPayload.Sheet.flight(9.6))
        assertEquals(1, WorldEffectPayload.Sheet.flight(0.0))
    }

    @Test
    @DisplayName("Едикти й лінії Justiciar проїжджають кожною формою")
    fun justiciarShapesRoundTrip() {
        val edicts = EdictKind.entries.map { WorldEffectPayload.Edict(it, 7, 0xEBC38E, 120) }
        val laws = ProhibitionSign.entries.map { WorldEffectPayload.Prohibition(Vec3(1.0, 64.0, -3.5), 20f, it, 900, 0xEBC38E) } +
            WorldEffectPayload.Prohibition(Vec3(1.0, 64.0, -3.5), 20f, ProhibitionSign.FLIGHT, 24, 0xEBC38E, breach = 42)
        val lashes = WorldEffectPayload.Lash.Shape.entries.map { WorldEffectPayload.Lash(3, 17, it, 0xEBC38E) }

        (edicts + laws + lashes).forEach { assertEquals(it, roundTrip(it, WorldEffectPayload.CODEC)) }
        // A law may stand for a minute (the Prohibition, 45 s): longer than a light, and no longer than that.
        val long = roundTrip(edicts.first().copy(ticks = 10_000), WorldEffectPayload.CODEC) as WorldEffectPayload.Edict
        assertEquals(WorldEffectPayload.MAX_LAW_TICKS, long.ticks)
    }

    @Test
    @DisplayName("Промінь з краю світу клієнт обрізає від джерела — місце, куди він падає, лишається")
    fun anOverlongBeamIsClamped() {
        val beam = WorldEffectPayload.Beam(Vec3(0.0, 1e6, 0.0), Vec3.ZERO, 0xFFFFFF, 10_000, 99f)

        val decoded = roundTrip(beam, WorldEffectPayload.CODEC) as WorldEffectPayload.Beam

        assertEquals(Vec3.ZERO, decoded.to)
        assertEquals(WorldEffectPayload.MAX_LENGTH, decoded.from.y, 1e-6)
        assertEquals(WorldEffectPayload.MAX_TICKS, decoded.ticks)
        assertEquals(WorldEffectPayload.MAX_WIDTH, decoded.width)
    }

    private fun <T : Any> roundTrip(payload: T, codec: StreamCodec<RegistryFriendlyByteBuf, T>): T {
        val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), registries)
        codec.encode(buf, payload)
        val decoded = codec.decode(buf)
        assertEquals(0, buf.readableBytes(), "пакет прочитав не все, що записав")
        return decoded
    }
}
