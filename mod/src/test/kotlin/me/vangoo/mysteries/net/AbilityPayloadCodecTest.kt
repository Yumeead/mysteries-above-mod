package me.vangoo.mysteries.net

import io.netty.buffer.Unpooled
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.GameRegistries
import me.vangoo.mysteries.net.payload.AbilityActionPayload
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import me.vangoo.domain.valueobjects.SanityLoss
import me.vangoo.mysteries.net.payload.AbilityStatePayload
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

    private fun <T : Any> roundTrip(payload: T, codec: StreamCodec<RegistryFriendlyByteBuf, T>): T {
        val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), registries)
        codec.encode(buf, payload)
        val decoded = codec.decode(buf)
        assertEquals(0, buf.readableBytes(), "пакет прочитав не все, що записав")
        return decoded
    }
}
