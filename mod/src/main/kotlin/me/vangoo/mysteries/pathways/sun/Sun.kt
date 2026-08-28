package me.vangoo.mysteries.pathways.sun

import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.entities.PathwayGroup
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.pathways.common.PhysicalEnhancement
import me.vangoo.mysteries.pathways.common.RitualMagic
import me.vangoo.mysteries.pathways.sun.abilities.Blessing
import me.vangoo.mysteries.pathways.sun.abilities.CleaveOfPurification
import me.vangoo.mysteries.pathways.sun.abilities.Contract
import me.vangoo.mysteries.pathways.sun.abilities.Daytime
import me.vangoo.mysteries.pathways.sun.abilities.EvilDetection
import me.vangoo.mysteries.pathways.sun.abilities.FireOfLight
import me.vangoo.mysteries.pathways.sun.abilities.HolyLightSummoning
import me.vangoo.mysteries.pathways.sun.abilities.HolyOath
import me.vangoo.mysteries.pathways.sun.abilities.HolyWave
import me.vangoo.mysteries.pathways.sun.abilities.NightVision
import me.vangoo.mysteries.pathways.sun.abilities.Notarization
import me.vangoo.mysteries.pathways.sun.abilities.ResistanceToHorror
import me.vangoo.mysteries.pathways.sun.abilities.Singing
import me.vangoo.mysteries.pathways.sun.abilities.SunHalo
import me.vangoo.mysteries.pathways.sun.abilities.SunHolyWater
import me.vangoo.mysteries.pathways.sun.abilities.Sunshine
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffects

/**
 * Шлях Сонця — святе світло, клятви й присуди.
 *
 * Другий шлях фази 08 (після Білої Вежі) і перший, у якому здібностей багато: шістнадцять
 * на Посл. 9–5 проти семи. Через це шлях мусив стати **несхожим усередині себе**: чотири
 * сили, які «б'ють святим світлом», розрізнено дієсловом, а не числом —
 * `Сонячне сяйво` влучає, `Святе світло` падає з відкритого неба, `Вогонь Світла` займає
 * місце, `Хвиля святості` рятує загін.
 *
 * **Заповнено Посл. 9–5** — стільки ж, скільки заповнював плагін.
 */
object Sun {

    const val NAME: String = "Sun"

    /** Канон новели; індекс списку **дорівнює** Послідовності: 0 — Сонце, 9 — Бард. */
    private val SEQUENCE_NAMES = listOf(
        "Sun", "White Angel", "Light Seeker", "Justice Mentor", "Shadowless",
        "Priest of Light", "Notary", "Solar High Priest", "Light Petitioner", "Bard",
    )

    fun create(): Pathway = Pathway(
        name = NAME,
        group = PathwayGroup.GodAlmighty,
        sequenceNames = SEQUENCE_NAMES,
        roster = mapOf(
            9 to listOf(
                Singing(),
                PhysicalEnhancement(
                    identity = AbilityIdentity.of("sun_physical_enhancement"),
                    displayName = Component.literal("Фізичні посилення"),
                    flavour = "Світло наповнює ваше тіло, зміцнюючи його та прискорюючи рухи.",
                    hearts = 3,
                    MobEffects.SPEED,
                ),
            ),
            8 to listOf(
                RitualMagic(),
                Sunshine(),
                Blessing(),
                EvilDetection(),
                Daytime(),
                NightVision(),
            ),
            7 to listOf(
                HolyLightSummoning(),
                SunHalo(),
                CleaveOfPurification(),
                ResistanceToHorror(),
                SunHolyWater(),
                HolyOath(),
                FireOfLight(),
            ),
            6 to listOf(Notarization(), Contract()),
            5 to listOf(HolyWave()),
        ),
    )
}
