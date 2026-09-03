package me.vangoo.mysteries.pathways.tyrant

import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.entities.PathwayGroup
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.pathways.common.PhysicalEnhancement
import me.vangoo.mysteries.pathways.tyrant.abilities.AbyssalDominion
import me.vangoo.mysteries.pathways.tyrant.abilities.AzureWave
import me.vangoo.mysteries.pathways.tyrant.abilities.Balance
import me.vangoo.mysteries.pathways.tyrant.abilities.CleansingLight
import me.vangoo.mysteries.pathways.tyrant.abilities.CorrosiveDeluge
import me.vangoo.mysteries.pathways.tyrant.abilities.Eavesdrop
import me.vangoo.mysteries.pathways.tyrant.abilities.IllusoryScales
import me.vangoo.mysteries.pathways.tyrant.abilities.LightningBoltArrow
import me.vangoo.mysteries.pathways.tyrant.abilities.LightningStrike
import me.vangoo.mysteries.pathways.tyrant.abilities.PreciseThrow
import me.vangoo.mysteries.pathways.tyrant.abilities.PressureWave
import me.vangoo.mysteries.pathways.tyrant.abilities.RagingBlow
import me.vangoo.mysteries.pathways.tyrant.abilities.RestorativeWater
import me.vangoo.mysteries.pathways.tyrant.abilities.SeaLunge
import me.vangoo.mysteries.pathways.tyrant.abilities.SeaMemory
import me.vangoo.mysteries.pathways.tyrant.abilities.SeaTongue
import me.vangoo.mysteries.pathways.tyrant.abilities.Singing
import me.vangoo.mysteries.pathways.tyrant.abilities.SlickWater
import me.vangoo.mysteries.pathways.tyrant.abilities.StormHeart
import me.vangoo.mysteries.pathways.tyrant.abilities.ThunderVoice
import me.vangoo.mysteries.pathways.tyrant.abilities.WaterControl
import me.vangoo.mysteries.pathways.tyrant.abilities.WaterCurtain
import me.vangoo.mysteries.pathways.tyrant.abilities.WaterFilm
import me.vangoo.mysteries.pathways.tyrant.abilities.WindBinding
import me.vangoo.mysteries.pathways.tyrant.abilities.WindFlight
import me.vangoo.mysteries.pathways.tyrant.abilities.WindImbuedHands
import me.vangoo.mysteries.pathways.tyrant.abilities.WindPull
import me.vangoo.mysteries.pathways.tyrant.abilities.WindSprint
import me.vangoo.mysteries.pathways.tyrant.abilities.Windblades
import me.vangoo.mysteries.pathways.tyrant.abilities.Wrath
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffects

/**
 * The pathway of the Tyrant — the sea, the storm and the wind.
 *
 * Not a subclass of `Pathway` as in the plugin: there the base constructor called the overridden
 * `initializeAbilities()`, i.e. the subclass filled a field that did not exist yet. Here a
 * pathway is simply a value, and this is the factory that assembles it.
 *
 * **Phase 08, Seq. 9-5.** The plugin never went above Seq. 5 for any pathway, so this roster
 * stops there too — writing Seq. 4-0 would be new game design, not a port.
 */
object Tyrant {

    const val NAME: String = "Tyrant"

    /** The novel's canon; the list index **equals** the Sequence: 0 — Tyrant, 9 — Sailor. */
    private val SEQUENCE_NAMES = listOf(
        "Tyrant", "God of Thunder", "Calamity", "Sea King", "Calamity Patron",
        "Ocean Songster", "Wind-Blessed", "Seafarer", "Furious One", "Sailor",
    )

    /** Shared identity of the Tyrant's body: one ability that progresses between sequences. */
    private val PHYSIQUE_IDENTITY = AbilityIdentity.of("tyrant_physique")

    fun create(): Pathway = Pathway(
        name = NAME,
        group = PathwayGroup.GodAlmighty,
        sequenceNames = SEQUENCE_NAMES,
        roster = mapOf(
            9 to listOf(
                PhysicalEnhancement(
                    PHYSIQUE_IDENTITY,
                    Component.literal("Морська спорідненість"),
                    "Матрос споріднений з морем: пливе спритно, мов риба.",
                    3,
                    MobEffects.DOLPHINS_GRACE,
                ),
                IllusoryScales(),
                Balance(),
                SeaLunge(),
            ),
            8 to listOf(
                PhysicalEnhancement(
                    PHYSIQUE_IDENTITY,
                    Component.literal("Штормова Міць"),
                    "Тіло Народу Люті загартоване штормом: швидкість підсилена, а спорідненість із морем збережена.",
                    4,
                    MobEffects.DOLPHINS_GRACE,
                    MobEffects.SPEED,
                ),
                Wrath(),
                RagingBlow(),
            ),
            7 to listOf(
                PhysicalEnhancement(
                    PHYSIQUE_IDENTITY,
                    Component.literal("Морська Спорідненість"),
                    "Тіло Мореплавця повністю зрослося з морем: до штормової міці додається Сила " +
                        "Провідника — нічне бачення, швидкість у воді й дихання під водою навіть у безодні.",
                    5,
                    MobEffects.WATER_BREATHING,
                    MobEffects.DOLPHINS_GRACE,
                    MobEffects.CONDUIT_POWER,
                    MobEffects.SPEED,
                ),
                SeaMemory(),
                PreciseThrow(),
                WaterFilm(),
                AzureWave(),
                RestorativeWater(),
                CleansingLight(),
            ),
            6 to listOf(
                PhysicalEnhancement(
                    PHYSIQUE_IDENTITY,
                    Component.literal("Спорідненість зі Стихіями"),
                    "Тіло Благословенного Вітром зрослося не лише з морем, а й із небом: зберігає " +
                        "штормову міць і Силу Провідника, а до них додається нічне бачення.",
                    6,
                    MobEffects.WATER_BREATHING,
                    MobEffects.DOLPHINS_GRACE,
                    MobEffects.SPEED,
                    MobEffects.NIGHT_VISION,
                ),
                Windblades(),
                PressureWave(),
                WindFlight(),
                WindSprint(),
                WindImbuedHands(),
                WindPull(),
                Eavesdrop(),
                Balance(
                    displayName = Component.literal("Повітряна подушка"),
                    flavour = "Вітер згущується під вами при падінні, а збити вас із ніг стало ще важче.",
                    resistance = 0.5,
                    fallImmune = true,
                ),
                WaterControl(),
            ),
            5 to listOf(
                PhysicalEnhancement(
                    PHYSIQUE_IDENTITY,
                    Component.literal("Володар Безодні"),
                    "Тіло Океанського Співця більше не знає меж глибини: воно бере кисень просто з " +
                        "води, не боїться тиску безодні й тримає штормову міць, Силу Провідника та нічне бачення.",
                    7,
                    MobEffects.WATER_BREATHING,
                    MobEffects.DOLPHINS_GRACE,
                    MobEffects.SPEED,
                    MobEffects.CONDUIT_POWER,
                    MobEffects.NIGHT_VISION,
                ),
                StormHeart(),
                AbyssalDominion(),
                LightningStrike(),
                LightningBoltArrow(),
                ThunderVoice(),
                Singing(),
                SeaTongue(),
                WaterCurtain(),
                CorrosiveDeluge(),
                SlickWater(),
                WindBinding(),
            ),
        ),
    )
}
