# Pathways Reference (mysteries-above)

**Authoritative source of sequence names:** `application/services/PathwayManager.initializePathways()`.
The name list passed to each pathway is ordered **index 0 → 9 == Sequence 0 → 9**.
`Pathway.getSequenceName(level)` does `sequenceNames.get(level)`, so **the sequence level IS the list index.**

- Sequence **9** = the FIRST potion a player drinks = **weakest** (index 9 in the list).
- Sequence **0** = the LAST = **strongest / near-deity** (index 0 in the list).

**ALWAYS resolve a target sequence's name from this table (or from the live code), never from memory.**
The plugin uses its *own* sequence names — several differ from the LotM wiki. The name in this table wins.

> Lore note: where the project name matches canon, use canonical *Lord of the Mysteries* powers as inspiration. Where it's a project-original name, design from the **pathway theme** + the **already-implemented sibling abilities** in `domain/pathways/<pathway>/abilities/` (the established tone on this server). When unsure of a canonical power, WebSearch the LotM wiki rather than inventing — but the new ability must still feel consistent with siblings.

---

## Error — group `LordOfMysteries` (theme: theft, deception, fate, observation, time)
Marauder-family rogue: steals powers/objects/luck, sees through everything, bends fate and time.

| Seq | Name | Design hook |
|----|------|-------------|
| 9 | Marauder | sleight-of-hand, theft of items, keen observation |
| 8 | Swindler | charm, deceit, misdirection, disguise of intent |
| 7 | Cryptologist | decode/decrypt secrets, read hidden patterns |
| 6 | Prometheus | steal Beyonder powers/elements/objects (see `TheftAbility`) |
| 5 | Dream Stealer | steal from minds/dreams, take memories |
| 4 | Parasite | attach to / drain a victim, live off others |
| 3 | Mentor of Deceit | mass deception, puppet others through lies |
| 2 | Fate Stealer | steal luck/fate, redirect misfortune |
| 1 | Worm of Time | rewind/skip time, undo events |
| 0 | Error | rewrite reality as if it were a flaw (see `FractureOfRealitiesAbility`) |

## Door — group `LordOfMysteries` (theme: space, travel, secrets/spells, prophecy)
Apprentice-family: teleportation, portals, divination, recorded spells, the stars.

| Seq | Name | Design hook |
|----|------|-------------|
| 9 | Apprentice | learn/cast minor spells, spiritual intuition/vision |
| 8 | Trickmaster | flashy spell tricks, electric/fire/flash effects, escape |
| 7 | Astrologer | star divination, read fate, anti-divination |
| 6 | Scribe | record events/places, decrypt patterns |
| 5 | Traveler | short-range teleport / blink (see `Blink`, `TravellersDoor`) |
| 4 | Secrets Sorcerer | open doors anywhere, secret passages |
| 3 | Wanderer | long-range travel across space |
| 2 | Planeswalker | cross dimensions / planes |
| 1 | Key of Stars | unlock any space/lock, starlight authority |
| 0 | Door | command space itself, teleport anything/anywhere |

## Justiciar — group `TheAnarchy` (theme: law, order, command, judgment, combat)
Order-enforcer: imperatives others must obey, jurisdiction zones, judgment, anti-power.

| Seq | Name | Design hook |
|----|------|-------------|
| 9 | Arbiter | combat proficiency, physical enhancement, intuition |
| 8 | Sheriff | restraint/branding, recognition of wrongdoers |
| 7 | Interrogator | force truth, psychic piercing/lashing |
| 6 | Judge | verdicts, punishment, prohibitions (see `Verdict`, `Punishment`) |
| 5 | Paladin | area of jurisdiction, protective authority |
| 4 | Imperative Mage | spoken commands targets must obey, power prohibition |
| 3 | Chaos Hunter | hunt/suppress chaotic Beyonders |
| 2 | Balancer | restore order, nullify imbalance |
| 1 | Hand of Order | impose law over a region |
| 0 | Justiciar | absolute order / law as reality |

## Visionary — group `GodAlmighty` (theme: mind, spirit, dreams, telepathy, hypnosis)
Spectator-family: read/heal/manipulate minds, hypnosis, dream-walking, illusion of self.

| Seq | Name | Design hook |
|----|------|-------------|
| 9 | Spectator | observe minds, danger sense, sharp vision, scan gaze |
| 8 | Telepathist | telepathy, read thoughts/secrets (see `Telepathy`) |
| 7 | Psychiatrist | soothe/agitate emotions, mental healing, appeasement, surge of insanity |
| 6 | Hypnotist | hypnosis, implant commands, battle hypnotism, psychic cue |
| 5 | Dreamwalker | enter/alter dreams, dream traversal |
| 4 | Manipulator | puppet/control a target's actions |
| 3 | Dream Weaver | craft dream realms / shared illusions |
| 2 | Discerner | see through illusion, perceive truth/intent |
| 1 | Author | write a target's behaviour/fate like a story |
| 0 | Visionary | treat minds & reality as a manuscript to rewrite |

## WhiteTower — group `GodAlmighty` (theme: knowledge, deduction, light, prophecy)
Knowledge-seeker: analysis, deduction, mysticism, enhanced mind, prophecy, holy light.

| Seq | Name | Design hook |
|----|------|-------------|
| 9 | Reader | analysis, enhanced mental attributes, agility |
| 8 | Student | combat/spell proficiency, mystical reenactment |
| 7 | Detective | deduce facts from clues, reconstruct events |
| 6 | Polymath | broad spellcasting, mirror curse |
| 5 | Mysticism Magister | mastery of mystical arts/rituals |
| 4 | Prophet | foresee/prophesy events |
| 3 | Cognizer | comprehend any knowledge instantly |
| 2 | Wisdom Angel | grant/withhold knowledge, holy insight |
| 1 | Omniscient Eye | perceive truth across distance |
| 0 | White Tower | near-omniscience, light/knowledge as power |

---

**Before designing, always:**
1. Confirm the exact name for your target sequence here (and against live `PathwayManager`).
2. `Glob` the pathway's `abilities/` folder and read 2–3 sibling abilities — match their tone, and make sure your ability is **not a duplicate** of an existing one.
