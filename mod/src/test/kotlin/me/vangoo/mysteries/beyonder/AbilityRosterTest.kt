package me.vangoo.mysteries.beyonder

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.Mastery
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.GameRegistries
import me.vangoo.mysteries.ability.Ability
import me.vangoo.mysteries.pathways.tyrant.Tyrant
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.DyedItemColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Знімок ростера: світу не піднімає, реєстри — так.
 *
 * Доводиться одне: те, що поїде клієнту, — це той самий [Beyonder], а не його
 * переказ. Ціни, типи, перепочинки й **порядок** мусять збігтися поле в поле; будь-яке
 * розходження тут означало б меню, що показує не те, що спрацює.
 */
class AbilityRosterTest {

    companion object {
        /** Канали кольору без альфи: `DyedItemColor.getOrDefault` віддає ARGB. */
        const val RGB = 0x00FFFFFF

        /** Іконка — це `ItemStack`, тож потрібні ще й прив'язані компоненти предметів. */
        @JvmStatic
        @BeforeAll
        fun bootstrapRegistries() = GameRegistries.bootstrap()
    }

    @Test
    @DisplayName("Не Потойбічний — порожній ростер, а не порожній список")
    fun ordinaryPlayerGetsNoRoster() {
        assertNull(BeyonderStatus.roster(null).roster)
        assertSame(me.vangoo.mysteries.net.payload.AbilityRosterPayload.NONE, BeyonderStatus.roster(null))
    }

    @Test
    @DisplayName("Заголовок ростера — шлях і канонічна назва Послідовності")
    fun rosterCarriesPathwayAndSequenceName() {
        val roster = requireNotNull(BeyonderStatus.roster(sailor()).roster)

        assertEquals("Tyrant", roster.pathwayId)
        assertEquals(9, roster.sequence.level)
        assertEquals("Sailor", roster.sequenceName.string)
    }

    @Test
    @DisplayName("Ціни, типи й перепочинки — ті самі, що в самого Потойбічного")
    fun entriesMatchTheBeyonder() {
        val beyonder = seafarer()
        val entries = requireNotNull(BeyonderStatus.roster(beyonder).roster).entries

        assertEquals(beyonder.abilities.size, entries.size)
        // Порядок теж частина відповіді: колесо розкладає картки саме в ньому.
        assertEquals(beyonder.abilities.map { it.identity.id }, entries.map { it.identity })

        beyonder.abilities.zip(entries).forEach { (spec, entry) ->
            assertEquals(spec.type, entry.type, entry.identity)
            assertEquals(spec.spiritualityCost, entry.cost, entry.identity)
            assertEquals(spec.periodicCost(beyonder.sequence), entry.upkeep, entry.identity)
            assertEquals(spec.cooldownSeconds(beyonder.sequence), entry.cooldownSeconds, entry.identity)
            assertEquals((spec as Ability).displayName.string, entry.name.string, entry.identity)
        }
    }

    @Test
    @DisplayName("Опис порахований під Послідовність власника, а не під базову")
    fun descriptionScalesWithSequence() {
        // Цілюща Вода називає тривалість у секундах, а та йде через `SequenceScaler`.
        // Клієнт цих чисел не рахує й рахувати не має — вони приїжджають готовими.
        val seven = entry(at(7), "tyrant_restorative_water").description.string
        val five = entry(at(5), "tyrant_restorative_water").description.string

        assertTrue(seven != five, "опис не змінився з Послідовністю: $seven")
    }

    @Test
    @DisplayName("Типова іконка — за типом здібності, пофарбована в колір шляху")
    fun iconIsTypedAndTinted() {
        val scales = entry(sailor(), "tyrant_illusory_scales")

        assertEquals(AbilityType.PERMANENT_PASSIVE, scales.type)
        assertSame(Items.PRISMARINE_SHARD, scales.icon.item)
        // Порівнюються самі канали кольору: `getOrDefault` віддає ARGB, тобто той самий
        // відтінок плюс непрозора альфа.
        assertEquals(
            PathwayBranding.liquidOf("Tyrant").packed and RGB,
            DyedItemColor.getOrDefault(scales.icon, 0) and RGB,
        )
    }

    @Test
    @DisplayName("Активна здібність несе свою іконку, а не пасивчину")
    fun activeAbilityHasItsOwnIcon() {
        val water = entry(seafarer(), "tyrant_restorative_water")

        assertEquals(AbilityType.ACTIVE, water.type)
        assertSame(Items.AMETHYST_SHARD, water.icon.item)
    }

    @Test
    @DisplayName("Фарбування ростера не чіпає іконку самої здібності")
    fun tintingDoesNotLeakIntoTheAbility() {
        val ability = Tyrant.create().abilitiesFor(9).single() as Ability
        BeyonderStatus.roster(sailor())

        assertTrue(
            !ability.icon.has(DataComponents.DYED_COLOR),
            "фарба лягла на спільний екземпляр іконки",
        )
    }

    // ── Заготовки ───────────────────────────────────────────────────────────────

    private fun sailor(): Beyonder = Beyonder(UUID.randomUUID(), Sequence.starter(), Tyrant.create())

    private fun seafarer(): Beyonder = at(7)

    /** Засвоєння скидається на кожному піднесенні, тож добираємо його щоразу. */
    private fun at(level: Int): Beyonder = sailor().apply {
        repeat(Sequence.starter().level - level) {
            setMastery(Mastery.max())
            advance()
        }
    }

    private fun entry(beyonder: Beyonder, identity: String) =
        requireNotNull(BeyonderStatus.roster(beyonder).roster).entries.single { it.identity == identity }

}
