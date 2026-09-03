# Pathways Reference (mysteries-above)

**Authoritative source of sequence names in the mod:** each pathway's own factory under
`mod/src/main/kotlin/me/vangoo/mysteries/pathways/<pathway>/` (in the plugin it was
`application/services/PathwayManager.initializePathways()`, which held the names apart from the
abilities — that split is gone). The list is ordered **index 0 → 9 == Sequence 0 → 9**.
`Pathway.sequenceName(level)` does `sequenceNames[level]`, so **the sequence level IS the list index.**

**Ported so far:** `WhiteTower`, `Sun`, `Door`, `Error`, `Visionary` and `Tyrant` (phase 08,
complete for Seq 9-5). The other eighteen pathways are still plugin-only; `ModPathways.byName`
returns `null` for them.

**The plugin fills Seq 9-5 only.** No pathway in the plugin has a roster at Seq 4-0 - writing
those is new game design, not a port. Match the plugin's coverage unless told otherwise.

**Phase 08 rule: the plugin is the list of powers, not their implementation.** What an ability
*is* - pathway, sequence, place in the roster, lore - comes from the plugin as-is. What it
*does* is redesigned whenever the mod can do it better than Spigot could. Measured on WhiteTower:
`Agility` went from 149 lines of polling to two transient attribute modifiers; `MysticalReenactment`
became `CrimeReconstruction`, reading vanilla `lastHurtByMob` instead of keeping a world-change
journal; `Spellcasting`'s six-row chest editor became a vanilla book-and-quill you write an
incantation into; `RitualMagic`'s choice menu became a real altar of lit candles with the offering
lying on the ground. Same power, bigger effect, smaller file.

- Sequence **9** = the FIRST potion a player drinks = **weakest** (index 9 in the list).
- Sequence **0** = the LAST = **strongest / near-deity** (index 0 in the list).

**ALWAYS resolve a target sequence's name from this table (or from the live code), never from memory.**
The plugin uses its *own* sequence names — several differ from the LotM wiki. The name in this table wins.

> Lore note: where the project name matches canon, use canonical *Lord of the Mysteries* powers as inspiration. Where it's a project-original name, design from the **pathway theme** + the **already-implemented sibling abilities** in `domain/pathways/<pathway>/abilities/` (the established tone on this server). When unsure of a canonical power, WebSearch the LotM wiki rather than inventing — but the new ability must still feel consistent with siblings.

---

## Tyrant — group `GodAlmighty` (theme: sea, storm, wind, thunder) — **ported (phase 08)**
Sailor-family: the body belongs to the water, then the storm, then the sky. Utility and weather
before violence; the lightning only arrives at the top half.

| Seq | Name | Design hook | In the mod |
|----|------|-------------|---|
| 9 | Sailor | fish-like body, balance, swimming, sea affinity | `PhysicalEnhancement`, `IllusoryScales`, `Balance`, `SeaLunge` |
| 8 | Furious One | rage, raw striking power, storm-hardened body | `Wrath`, `RagingBlow` |
| 7 | Seafarer | water shaping, healing water, cleansing, sea memory | `SeaMemory`, `PreciseThrow`, `WaterFilm`, `AzureWave`, `RestorativeWater`, `CleansingLight` |
| 6 | Wind-Blessed | wind blades, flight, wind sprint, pressure waves | `Windblades`, `PressureWave`, `WindFlight`, `WindSprint`, `WindImbuedHands`, `WindPull`, `Eavesdrop`, `Balance` (Air Cushion tier), `WaterControl` |
| 5 | Ocean Songster | song, lightning, abyssal dominion, thunder voice | `StormHeart`, `AbyssalDominion`, `LightningStrike`, `LightningBoltArrow`, `ThunderVoice`, `Singing`, `SeaTongue`, `WaterCurtain`, `CorrosiveDeluge`, `SlickWater`, `WindBinding` |
| 4 | Calamity Patron | patronage over disaster, sea-borne calamity | — |
| 3 | Sea King | rule the ocean and what lives in it | — |
| 2 | Calamity | be the disaster: storm, flood, wreck | — |
| 1 | God of Thunder | thunder as authority over a region | — |
| 0 | Tyrant | command sea, storm and sky as one | — |

**`Balance` is Tyrant's shared stance layer** — parameterised like `PhysicalEnhancement`, not
subclassed: Seq. 6's Air Cushion is the same class with a stronger `resistance` and
`fallImmune = true`, so the identity `tyrant_stance` stays one class across tiers.

## Sun — group `GodAlmighty` (theme: holy light, oath, judgment, purification) — **ported (phase 08)**
Bard-family: light that sings first, then blesses, then burns. Anti-evil and anti-fear
throughout; contracts and notarization in the upper half.

| Seq | Name | Design hook | In the mod |
|----|------|-------------|---|
| 9 | Bard | singing, physical enhancement by light | `Singing`, `PhysicalEnhancement` (common) |
| 8 | Light Petitioner | sunshine, blessing, evil detection, daytime | `RitualMagic` (common), `Sunshine`, `Blessing`, `EvilDetection`, `Daytime`, `NightVision` |
| 7 | Solar High Priest | summon holy light, halo, purifying cleave, holy water, oath | `HolyLightSummoning`, `SunHalo`, `CleaveOfPurification`, `ResistanceToHorror`, `SunHolyWater`, `HolyOath`, `FireOfLight` |
| 6 | Notary | notarize, bind contracts | `Notarization`, `Contract` |
| 5 | Priest of Light | holy wave, light as a weapon | `HolyWave` |
| 4 | Shadowless | no shadow may touch or hide from you | — |
| 3 | Justice Mentor | teach/impose justice, judge by light | — |
| 2 | Light Seeker | seek and reveal what light does not reach | — |
| 1 | White Angel | angelic authority of the sun | — |
| 0 | Sun | be the sun: light, judgment, purification absolute | — |

**`abilities/HolyLight.kt` is Sun's shared layer** — `isProfane` (dark pathway or the vanilla
`SENSITIVE_TO_SMITE` tag), `smite` (holy damage through `HolyAffinity`), `cleanse` (strip every
`HARMFUL`-category effect). Reuse them; never hand-roll an undead list.

**A pathway with many abilities must be distinct inside itself.** Sun has four "hit things with
holy light" powers, so they are separated by *verb*, not by number: `Sunshine` hits one target,
`HolyLightSummoning` falls from open sky (fails under a roof), `FireOfLight` claims a spot for
seven seconds, `HolyWave` saves the party. When adding to a crowded pathway, find the verb first.

**A power that must extinguish itself is `ActiveAbility` + a session, and its price is all upkeep.**
`Blessing`, `Daytime`, `NightVision` and `SunHalo` declare `spiritualityCost = 0` and pay through
`periodicCost`, so re-casting to switch the power *off* is free — otherwise the player pays to stop
paying. A 3-4 s cooldown stays, against flicker.

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

## WhiteTower — group `GodAlmighty` (theme: knowledge, deduction, light, prophecy) — **ported (phase 08)**
Knowledge-seeker: read the world, deduce from it, then rewrite the rules of a spell.

| Seq | Name | Design hook | In the mod |
|----|------|-------------|---|
| 9 | Reader | enhanced mind, ritual magic | `RitualMagic` (common), `EnhancedMind` |
| 8 | Student | study a target, learn its weak point | `Deduction` |
| 7 | Detective | reconstruct events, agility, combat training | `CombatProficiency` + `PhysicalEnhancement` (common), `Agility`, `CrimeReconstruction` |
| 6 | Polymath | know a bit of everything - borrow another's power | `Analysis` |
| 5 | Mysticism Magister | compose spells, mirror curse | `Spellcasting`, `MirrorCurse` |
| 4 | Prophet | foresee/prophesy events | — |
| 3 | Cognizer | comprehend any knowledge instantly | — |
| 2 | Wisdom Angel | grant/withhold knowledge, holy insight | — |
| 1 | Omniscient Eye | perceive truth across distance | — |
| 0 | White Tower | near-omniscience, light/knowledge as power | — |

**Shared abilities live in `pathways/common/`** — `RitualMagic`, `CombatProficiency`,
`PhysicalEnhancement`. Reuse them; `PhysicalEnhancement` is parameterised (identity, name,
flavour, hearts, effects) and each pathway passes its **own** identity.

---

**Before designing, always:**
1. Confirm the exact name for your target sequence here (and against the pathway's factory in
   `mod/src/main/kotlin/me/vangoo/mysteries/pathways/<pathway>/`).
2. `Glob` the pathway's `abilities/` folder and read 2–3 sibling abilities — match their tone, and make sure your ability is **not a duplicate** of an existing one.
3. If the pathway has no folder in the mod yet, its siblings live only in the plugin
   (`C:\Users\yuryp\IdeaProjects\mysteries-above\src\main\java\me\vangoo\pathways\<pathway>\`) —
   read those for tone and coverage, and create the factory the way `Tyrant`/`Sun` do it.
