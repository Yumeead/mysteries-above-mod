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
 * The pathway of the Sun — holy light, oaths and verdicts.
 *
 * The second pathway of phase 08 (after the White Tower) and the first with many abilities:
 * sixteen across Seq. 9–5 against seven. Because of that the pathway had to become **unlike
 * itself inside**: four powers that "strike with holy light" are told apart by the verb rather
 * than by a number — `Сонячне сяйво` hits, `Святе світло` falls from open sky, `Вогонь Світла`
 * takes ground, `Хвиля святості` saves the party.
 *
 * **Filled for Seq. 9–5** — as much as the plugin filled.
 */
object Sun {

    const val NAME: String = "Sun"

    /** The novel's canon; the list index **equals** the Sequence: 0 — Sun, 9 — Bard. */
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
