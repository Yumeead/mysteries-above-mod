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
 * Шлях Дверей — простір, відстань і чуже знання.
 *
 * Третій шлях фази 08. Сонце вирішувало задачу «багато сил, усі б'ють світлом», Двері —
 * протилежну: тут майже жодна сила не б'є, і розрізняти їх доводиться не силою удару, а
 * тим, **що саме вони скорочують**.
 *
 * | Посл. | що скорочується |
 * |---|---|
 * | 9 | товщина стіни — `Відкриття дверей` |
 * | 8 | дистанція до втечі — `Флешка`, `Трюк зі зникненням` |
 * | 7 | відстань до чужого знання — `Мистецтво ворожіння`, `Духовне бачення` |
 * | 6 | відстань до чужої сили — `Запис здібності`, `Розшифровка патернів` |
 * | 5 | сама відстань — `Двері Мандрівника`, `Блимання` |
 *
 * **Заповнено Посл. 9–5** — стільки ж, скільки заповнював плагін.
 *
 * ## Чого тут немає навмисно
 *
 * Плагін вирішував **усі** питання вибору меню в скрині: `Мистецтво ворожіння`
 * відкривало п'ять підменю, `Запис` — список голів гравців поблизу, `Двері Мандрівника`
 * просили координати в чат. Меню тут немає жодного — і не тому, що екранів іще нема
 * (фаза 11), а тому, що кожен із трьох випадків має **предметну** відповідь: інструмент
 * у руці обирає спосіб ворожіння, погляд обирає ціль запису, а компас лодестона несе
 * координату, яку гравець уже заслужив. Той самий хід, що зробив `RitualMagic` зі
 * справжнім вівтарем замість `openChoiceMenu`.
 */
object Door {

    const val NAME: String = "Door"

    /** Канон новели; індекс списку **дорівнює** Послідовності: 0 — Двері, 9 — Учень. */
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
