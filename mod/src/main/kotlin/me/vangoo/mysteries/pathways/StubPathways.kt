package me.vangoo.mysteries.pathways

import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.entities.PathwayGroup
import me.vangoo.domain.entities.PathwayGroup.CalamityOfDestruction
import me.vangoo.domain.entities.PathwayGroup.DemonOfKnowledge
import me.vangoo.domain.entities.PathwayGroup.EternalDarkness
import me.vangoo.domain.entities.PathwayGroup.FatherOfDevils
import me.vangoo.domain.entities.PathwayGroup.GodAlmighty
import me.vangoo.domain.entities.PathwayGroup.GoddessOfOrigin
import me.vangoo.domain.entities.PathwayGroup.KeyOfLight
import me.vangoo.domain.entities.PathwayGroup.TheAnarchy

/**
 * The thirteen pathways the plugin declared but never filled: a name, a group and ten Sequence
 * names, no abilities. The potion, the Characteristic and the colour (`PathwayBranding`) already
 * work for them, so a player can walk one — and switch to a filled pathway of the same group.
 *
 * The plugin spent two classes per stub (an empty `Pathway` subclass and an empty potion set);
 * here a stub is one line, and filling it means moving that line into its own factory.
 * Sequence names are copied from the plugin's `PathwayManager`; index **equals** the Sequence.
 */
object StubPathways {

    fun create(): List<Pathway> = listOf(
        stub(
            "HangedMan", GodAlmighty,
            "Hanged Man", "Dark Angel", "Profaner Presbyter", "Trinity Templar", "Black Knight",
            "Shepherd", "Rose Bishop", "Shadow Ascetic", "Listener", "Secrets Supplicant",
        ),
        stub(
            "Hermit", DemonOfKnowledge,
            "Hermit", "Sage", "Emperor of Knowledge", "Clairvoyant", "Mysticologist",
            "Constellation Magister", "Scroll Professor", "Warlock", "Adjacent Scholar", "Seeker of Mysteries",
        ),
        stub(
            "Paragon", DemonOfKnowledge,
            "Paragon", "Illuminator", "Knowledge Mentor", "Arcane Scholar", "Alchemist",
            "Astronomer", "Craftsman", "Appraiser", "Archaeologist", "Scholar",
        ),
        stub(
            "BlackEmperor", TheAnarchy,
            "Black Emperor", "Fallen Angel", "Duke of Entropy", "Prince of Abrogation", "Count of the Fallen",
            "Mentor of Disorder", "Baron of Corruption", "Briber", "Barbarian", "Advocate",
        ),
        stub(
            "Darkness", EternalDarkness,
            "Darkness", "Knight of Misfortune", "Concealed Servitor", "Bishop of Horror", "Nightwatcher",
            "Spirit Sorcerer", "Soul Assurer", "Nightmare", "Midnight Poet", "Sleepless",
        ),
        stub(
            "TwilightGiant", EternalDarkness,
            "Twilight Giant", "Hand of God", "Glory", "Silver Knight", "Demon Hunter",
            "Guardian", "Dawn Paladin", "Weapon Master", "Pugilist", "Warrior",
        ),
        stub(
            "Mother", GoddessOfOrigin,
            "Mother", "Nature Walker", "Matriarch of Desolation", "Life Giver", "Ancient Alchemist",
            "Druid", "Biologist", "Harvest Priest", "Physician", "Gardener",
        ),
        stub(
            "Moon", GoddessOfOrigin,
            "Moon", "Goddess of Beauty", "Moon Duke", "High Sorcerer", "Shaman King",
            "Crimson Scholar", "Potions Professor", "Vampire", "Beast Tamer", "Apothecary",
        ),
        stub(
            "RedPriest", CalamityOfDestruction,
            "Red Priest", "Conqueror", "Weather Warlock", "War Bishop", "Iron-Blooded Knight",
            "Reaper", "Conspirator", "Pyromaniac", "Provoker", "Hunter",
        ),
        stub(
            "Demoness", CalamityOfDestruction,
            "Demoness", "Apocalypse", "Catastrophe", "Everlasting", "Despair",
            "Suffering", "Pleasure", "Witch", "Instigator", "Assassin",
        ),
        stub(
            "Abyss", FatherOfDevils,
            "Abyss", "Filthy Monarch", "Bloody Archduke", "Prattler", "Demon",
            "Apostle of Desire", "Devil", "Serial Killer", "Wingless Angel", "Criminal",
        ),
        stub(
            "Chained", FatherOfDevils,
            "Chained", "Abomination", "Ancient Curse", "Disciple of Silence", "Doll",
            "Ghost", "Zombie", "Werewolf", "Sleepwalker", "Prisoner",
        ),
        stub(
            "WheelOfFortune", KeyOfLight,
            "Wheel of Fortune", "Mercury Serpent", "Diviner", "Mage of Misfortune", "Chaos Walker",
            "Victor", "Priest of Misfortune", "Lucky One", "Robot", "Monster",
        ),
    )

    private fun stub(name: String, group: PathwayGroup, vararg sequenceNames: String) =
        Pathway(name = name, group = group, sequenceNames = sequenceNames.toList())
}
