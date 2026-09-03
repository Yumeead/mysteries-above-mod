package me.vangoo.mysteries.pathways.door

import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.entities.PathwayGroup
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.pathways.common.PhysicalEnhancement
import me.vangoo.mysteries.pathways.door.abilities.AntiDivination
import me.vangoo.mysteries.pathways.door.abilities.Blink
import me.vangoo.mysteries.pathways.door.abilities.Burning
import me.vangoo.mysteries.pathways.door.abilities.DecryptPatterns
import me.vangoo.mysteries.pathways.door.abilities.DivinationArts
import me.vangoo.mysteries.pathways.door.abilities.DoorOpening
import me.vangoo.mysteries.pathways.door.abilities.ElectricShock
import me.vangoo.mysteries.pathways.door.abilities.EscapeTrick
import me.vangoo.mysteries.pathways.door.abilities.Flash
import me.vangoo.mysteries.pathways.door.abilities.Record
import me.vangoo.mysteries.pathways.door.abilities.SpiritualIntuition
import me.vangoo.mysteries.pathways.door.abilities.SpiritualVision
import me.vangoo.mysteries.pathways.door.abilities.TravellersDoor
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffects

/**
 * The pathway of the Door — space, distance and other people's knowledge.
 *
 * The third pathway of phase 08. The Sun solved the problem of "many powers, all of them
 * striking with light"; the Door has the opposite one: here almost no power strikes at all, and
 * they have to be told apart not by the force of a blow but by **what exactly each of them
 * shortens**.
 *
 * | Seq. | what is shortened |
 * |---|---|
 * | 9 | the thickness of a wall — `Відкриття дверей` |
 * | 8 | the distance to an escape — `Спалах`, `Трюк зі зникненням` |
 * | 7 | the distance to someone's knowledge — `Мистецтво ворожіння`, `Духовне бачення` |
 * | 6 | the distance to someone's power — `Запис здібності`, `Розшифрування патернів` |
 * | 5 | distance itself — `Двері Мандрівника`, `Миттєвий крок` |
 *
 * **Filled for Seq. 9–5** — as much as the plugin filled.
 *
 * ## What is deliberately absent
 *
 * The plugin solved **every** question of choice with a chest menu: `Мистецтво ворожіння`
 * opened five submenus, `Запис` a list of nearby players' heads, `Двері Мандрівника` asked for
 * coordinates in chat. There is not a single menu here — and not because there are no screens
 * yet (phase 11), but because each of the three cases has an answer **made of items**: the tool
 * in hand chooses the way of divining, the gaze chooses the target to record, and a lodestone
 * compass carries a coordinate the player has already earned. The same move `RitualMagic` made
 * with a real altar instead of `openChoiceMenu`.
 */
object Door {

    const val NAME: String = "Door"

    /** The novel's canon; the list index **equals** the Sequence: 0 — Door, 9 — Apprentice. */
    private val SEQUENCE_NAMES = listOf(
        "Door", "Key of Stars", "Planeswalker", "Wanderer", "Secrets Sorcerer",
        "Traveler", "Scribe", "Astrologer", "Trickmaster", "Apprentice",
    )

    fun create(): Pathway = Pathway(
        name = NAME,
        group = PathwayGroup.LordOfMysteries,
        sequenceNames = SEQUENCE_NAMES,
        roster = mapOf(
            9 to listOf(DoorOpening()),
            8 to listOf(Flash(), EscapeTrick(), Burning(), ElectricShock()),
            7 to listOf(DivinationArts(), SpiritualVision(), SpiritualIntuition(), AntiDivination()),
            6 to listOf(
                Record(),
                DecryptPatterns(),
                PhysicalEnhancement(
                    identity = AbilityIdentity.of("door_physical_enhancement"),
                    displayName = Component.literal("Фізичні посилення"),
                    flavour = "Простір слухається вас, і тіло звикає рухатись швидше за нього.",
                    hearts = 3,
                    MobEffects.SPEED,
                ),
            ),
            5 to listOf(TravellersDoor(), Blink()),
        ),
    )
}
