package me.vangoo.mysteries.pathways.error

import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.entities.PathwayGroup
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.pathways.common.CombatProficiency
import me.vangoo.mysteries.pathways.common.PhysicalEnhancement
import me.vangoo.mysteries.pathways.error.abilities.AbilityTheft
import me.vangoo.mysteries.pathways.error.abilities.AttackTheft
import me.vangoo.mysteries.pathways.error.abilities.DreamTheft
import me.vangoo.mysteries.pathways.error.abilities.HeartTheft
import me.vangoo.mysteries.pathways.error.abilities.IdealTheft
import me.vangoo.mysteries.pathways.error.abilities.TheftOfHeavenlySecrets
import me.vangoo.mysteries.pathways.error.abilities.ThoughtUsurpation
import me.vangoo.mysteries.pathways.error.abilities.Decryption
import me.vangoo.mysteries.pathways.error.abilities.Eloquence
import me.vangoo.mysteries.pathways.error.abilities.MaterialTheft
import me.vangoo.mysteries.pathways.error.abilities.MentalDisruption
import me.vangoo.mysteries.pathways.error.abilities.MentalFortitude
import me.vangoo.mysteries.pathways.error.abilities.PowerTheft
import me.vangoo.mysteries.pathways.error.abilities.ShadowTheft
import me.vangoo.mysteries.pathways.error.abilities.SuperiorObservation
import me.vangoo.mysteries.pathways.error.abilities.SwindlerCharm
import me.vangoo.mysteries.pathways.error.abilities.ThoughtMisdirection
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffects

/**
 * The pathway of Error — what was someone else's and became yours.
 *
 * The fourth pathway of phase 08. The Sun told sixteen powers apart by the verb, the Door by
 * what exactly each of them shortens; Error tells its own apart by **what it takes**, and the
 * level of abstraction of what is taken is its Sequence:
 *
 * | Seq. | what can be taken |
 * |---|---|
 * | 9 Marauder | a thing out of a pocket |
 * | 8 Swindler | someone's attention, someone's intent, material through a wall |
 * | 7 Cryptologist | a truth someone tried to hide |
 * | 6 Prometheus | a Beyonder's ability itself |
 * | 5 Dream Stealer | a thought, an attack, a dream, a heart, an ideal — what cannot be held in the hands |
 *
 * ## The pathway's shared body
 *
 * `PhysicalEnhancement` travels under the single identity `error_physique` at every Sequence the
 * wiki gives the trait to: a stronger version **replaces** the weaker one rather than piling up
 * beside it. The identity is its own, not shared with other pathways — it is a global key, and a
 * shared one would mean the Marauder's body and the Justiciar's body substituting for each other.
 */
object Error {

    const val NAME: String = "Error"

    /** The novel's canon; the list index **equals** the Sequence: 0 — Error, 9 — Marauder. */
    private val SEQUENCE_NAMES = listOf(
        "Error", "Worm of Time", "Fate Stealer", "Mentor of Deceit", "Parasite",
        "Dream Stealer", "Prometheus", "Cryptologist", "Swindler", "Marauder",
    )

    /** The pathway's shared physique key: a stronger tier replaces a weaker one. */
    private val PHYSIQUE = AbilityIdentity.of("error_physique")

    fun create(): Pathway = Pathway(
        name = NAME,
        group = PathwayGroup.LordOfMysteries,
        sequenceNames = SEQUENCE_NAMES,
        roster = mapOf(
            9 to listOf(
                ShadowTheft(),
                SuperiorObservation(),
                CombatProficiency(),
                PhysicalEnhancement(
                    identity = PHYSIQUE,
                    displayName = Component.literal("Фізичні посилення"),
                    flavour = "Крадене чуже потроху стає вашим — починаючи з витривалості.",
                    hearts = 3,
                ),
            ),
            8 to listOf(
                SwindlerCharm(),
                Eloquence(),
                ThoughtMisdirection(),
                MentalDisruption(),
                MaterialTheft(),
                PhysicalEnhancement(
                    identity = PHYSIQUE,
                    displayName = Component.literal("Тіло афериста"),
                    flavour = "Тіло стає значно спритнішим і швидшим — від чужого життя, що вже у ваших жилах.",
                    hearts = 4,
                    MobEffects.SPEED,
                ),
            ),
            7 to listOf(Decryption()),
            6 to listOf(
                PowerTheft(),
                MentalFortitude(),
                PhysicalEnhancement(
                    identity = PHYSIQUE,
                    displayName = Component.literal("Тіло Прометея"),
                    flavour = "Крадене чуже вже частина вашого тіла — і воно про це знає.",
                    hearts = 5,
                    MobEffects.SPEED,
                ),
            ),
            5 to listOf(
                ThoughtUsurpation(),
                AttackTheft(),
                IdealTheft(),
                DreamTheft(),
                AbilityTheft(),
                HeartTheft(),
                TheftOfHeavenlySecrets(),
                PhysicalEnhancement(
                    identity = PHYSIQUE,
                    displayName = Component.literal("Тіло Крадія снів"),
                    flavour = "Ви крадете саму суть — і тіло вже не зовсім ваше.",
                    hearts = 6,
                    MobEffects.SPEED,
                    MobEffects.NIGHT_VISION,
                ),
            ),
        ),
    )
}
