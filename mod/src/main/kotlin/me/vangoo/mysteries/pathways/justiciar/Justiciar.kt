package me.vangoo.mysteries.pathways.justiciar

import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.entities.PathwayGroup
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.pathways.common.CombatProficiency
import me.vangoo.mysteries.pathways.common.PhysicalEnhancement
import me.vangoo.mysteries.pathways.door.abilities.AntiDivination
import me.vangoo.mysteries.pathways.justiciar.abilities.AreaOfJurisdiction
import me.vangoo.mysteries.pathways.justiciar.abilities.ArbitersGaze
import me.vangoo.mysteries.pathways.justiciar.abilities.Authority
import me.vangoo.mysteries.pathways.justiciar.abilities.BrandOfRestraint
import me.vangoo.mysteries.pathways.justiciar.abilities.Intuition
import me.vangoo.mysteries.pathways.justiciar.abilities.PowerProhibition
import me.vangoo.mysteries.pathways.justiciar.abilities.Punishment
import me.vangoo.mysteries.pathways.justiciar.abilities.PsychicLashing
import me.vangoo.mysteries.pathways.justiciar.abilities.PsychicPiercing
import me.vangoo.mysteries.pathways.justiciar.abilities.Recognition
import me.vangoo.mysteries.pathways.justiciar.abilities.SpawnProhibition
import me.vangoo.mysteries.pathways.justiciar.abilities.Verdict
import me.vangoo.mysteries.pathways.justiciar.abilities.WhipOfPain
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffects

/**
 * The pathway of the Justiciar — law given a body: authority, judgement and punishment.
 *
 * **Phase 08, Seq. 9-5.** The plugin never went above Seq. 5 for any pathway, so this roster
 * stops there too — writing Seq. 4-0 would be new game design, not a port.
 *
 * `AntiDivination` (Seq. 5) is not a new implementation: the plugin's own roster reuses Door's
 * class outright, and so does this one — a Beyonder follows exactly one pathway, so the shared
 * identity never collides with itself.
 */
object Justiciar {

    const val NAME: String = "Justiciar"

    /** The novel's canon; the list index **equals** the Sequence: 0 — Justiciar, 9 — Arbiter. */
    private val SEQUENCE_NAMES = listOf(
        "Justiciar", "Hand of Order", "Balancer", "Chaos Hunter", "Imperative Mage",
        "Paladin", "Judge", "Interrogator", "Sheriff", "Arbiter",
    )

    private val PHYSIQUE_IDENTITY = AbilityIdentity.of("justiciar_physique")

    fun create(): Pathway = Pathway(
        name = NAME,
        group = PathwayGroup.TheAnarchy,
        sequenceNames = SEQUENCE_NAMES,
        roster = mapOf(
            9 to listOf(
                PhysicalEnhancement(
                    PHYSIQUE_IDENTITY,
                    Component.literal("Фізичні посилення"),
                    "Могутнє тіло Арбітра: сила та надзвичайна швидкість.",
                    4,
                    MobEffects.SPEED,
                ),
                CombatProficiency(),
                Authority(),
                ArbitersGaze(),
            ),
            8 to listOf(
                AreaOfJurisdiction(),
                Recognition(),
                Intuition(),
            ),
            7 to listOf(
                WhipOfPain(),
                PsychicPiercing(),
                BrandOfRestraint(),
                PsychicLashing(),
            ),
            6 to listOf(
                Verdict(),
                PowerProhibition(),
                SpawnProhibition(),
            ),
            5 to listOf(
                Punishment(),
                AntiDivination(),
            ),
        ),
    )
}
