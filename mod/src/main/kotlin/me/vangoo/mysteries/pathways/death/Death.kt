package me.vangoo.mysteries.pathways.death

import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.entities.PathwayGroup
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.entity.MarionetteEntity
import me.vangoo.mysteries.pathways.common.PhysicalEnhancement
import me.vangoo.mysteries.pathways.common.RitualMagic
import me.vangoo.mysteries.pathways.death.abilities.CadavericResilience
import me.vangoo.mysteries.pathways.death.abilities.CorpseGuise
import me.vangoo.mysteries.pathways.death.abilities.DeathSight
import me.vangoo.mysteries.pathways.death.abilities.DoorToTheUnderworld
import me.vangoo.mysteries.pathways.death.abilities.EyeOfDeath
import me.vangoo.mysteries.pathways.death.abilities.GatekeeperStep
import me.vangoo.mysteries.pathways.death.abilities.GloomyPresence
import me.vangoo.mysteries.pathways.death.abilities.InternalUnderworld
import me.vangoo.mysteries.pathways.death.abilities.LanguageOfTheDead
import me.vangoo.mysteries.pathways.death.abilities.Resurrection
import me.vangoo.mysteries.pathways.death.abilities.SpiritChanneling
import me.vangoo.mysteries.pathways.death.abilities.SpiritCommunication
import me.vangoo.mysteries.pathways.death.abilities.SpiritPact
import me.vangoo.mysteries.pathways.death.abilities.SpiritPerception
import me.vangoo.mysteries.pathways.death.abilities.SpiritVision
import me.vangoo.mysteries.pathways.death.abilities.SpiritWorldKnowledge
import me.vangoo.mysteries.pathways.death.abilities.UndeadKnowledge
import me.vangoo.mysteries.pathways.death.abilities.VoiceOfTheDead
import net.minecraft.network.chat.Component
import net.minecraft.world.effect.MobEffects

/**
 * The pathway of Death — the dead, the spirits, and the gate between them: Corpse Collector,
 * Gravedigger, Spirit Medium, Spirit Usher, Gatekeeper.
 *
 * **Phase 08, Seq. 9-5 filled** — the last and largest pathway of the phase. The plugin's two
 * hand-passed registries (`LingeringSouls`, `UndeadRetinue`) became world-level records here:
 * [DeathTraces] is kept by every death rather than by a medium's passive, and [Retinue] is kept by
 * the world's own entity saving rather than by `retinue.json`.
 */
object Death {

    const val NAME: String = Retinue.DEATH

    /** The novel's canon; the list index **equals** the Sequence: 0 — Death, 9 — Corpse Collector. */
    private val SEQUENCE_NAMES = listOf(
        "Death", "Pale Emperor", "Consul of Death", "Ferryman", "Undying",
        "Gatekeeper", "Spirit Usher", "Spiritualist", "Gravedigger", "Corpse Collector",
    )

    /** The shared physique key: a stronger tier replaces a weaker one. */
    private val PHYSIQUE = AbilityIdentity.of("death_physique")

    fun create(): Pathway = Pathway(
        name = NAME,
        group = PathwayGroup.EternalDarkness,
        sequenceNames = SEQUENCE_NAMES,
        roster = mapOf(
            9 to listOf(
                UndeadKnowledge(),
                CadavericResilience(),
                GloomyPresence(),
                DeathSight(),
                SpiritVision(),
                PhysicalEnhancement(
                    identity = PHYSIQUE,
                    displayName = Component.literal("Тіло Трупозбирача"),
                    flavour = "Ваше тіло холодніє, а присутність стає похмурою.",
                    hearts = 3,
                ),
            ),
            8 to listOf(
                SpiritCommunication(),
                EyeOfDeath(),
                PhysicalEnhancement(
                    identity = PHYSIQUE,
                    displayName = Component.literal("Тіло Могильника"),
                    flavour = "Тіло могильника лишається холодним, але вже не важким: могили копаються легко, а кроки швидкі.",
                    hearts = 4,
                    MobEffects.SPEED,
                ),
            ),
            7 to listOf(
                SpiritPerception(),
                VoiceOfTheDead(),
                SpiritChanneling(),
                CorpseGuise(),
                RitualMagic(),
                PhysicalEnhancement(
                    identity = PHYSIQUE,
                    displayName = Component.literal("Тіло Медіума"),
                    flavour = "Тіло медіума майже не міцніє понад могильникове — сила тепер не в м'язах, а в духовності.",
                    hearts = 4,
                    MobEffects.SPEED,
                ),
            ),
            6 to listOf(
                SpiritWorldKnowledge(),
                LanguageOfTheDead(),
                Resurrection(),
                SpiritPact(),
                PhysicalEnhancement(
                    identity = PHYSIQUE,
                    displayName = Component.literal("Тіло Провідника"),
                    flavour = "Тіло провідника носить у собі чужу смерть: холод глибший, а рухи легші за живі.",
                    hearts = 5,
                    MobEffects.SPEED,
                ),
            ),
            5 to listOf(
                GatekeeperStep(),
                DoorToTheUnderworld(),
                InternalUnderworld(),
                PhysicalEnhancement(
                    identity = PHYSIQUE,
                    displayName = Component.literal("Тіло Вартового"),
                    flavour = "Тіло воротаря стоїть на межі життя й смерті: сила зростає, а кроки лишаються швидкими.",
                    hearts = 6,
                    MobEffects.SPEED,
                ),
            ),
        ),
    )

    /** The pathway's world-level hooks. A Fabric event cannot be unregistered, so they hang once. */
    fun install() {
        DeathTraces.install({ AbilityServices.scheduler.tick }) { entity ->
            Spirits.isSpirit(entity) || Retinue.isServant(entity) || Spirits.EPHEMERAL in entity.entityTags() ||
                (entity as? MarionetteEntity)?.vesselOf != null
        }
        Spirits.install()
        Retinue.install()
        Underworld.install()
        LanguageOfTheDead.install()
        InternalUnderworld.install()
    }

    fun reset() {
        DeathTraces.reset()
        Retinue.reset()
    }
}
