package me.vangoo.mysteries.client.render

import me.vangoo.mysteries.modId
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier

/** Painted ability sprites, with engraved pictograms for powers awaiting their own artwork. */
object BookAbilityIcons {
    const val SIZE = 24
    val textures = listOf(
        "sun_fire_of_light", "sun_holy_oath", "fool_paper_substitution", "door_blink", "tyrant_windblades",
        "fool_divination_arts", "fool_spirit_vision", "fool_danger_intuition", "fool_paper_cutter",
        "fool_expression_control", "fool_agility", "fool_flame_jump", "fool_air_bullet", "fool_damage_transfer",
        "fool_aquatic_breath", "fool_illusion_creation", "fool_shapeshifting", "fool_thread_sight",
        "fool_marionettist_control", "fool_marionette_possession",
        "door_opening", "door_flash", "door_escape_trick", "door_burning", "door_electric_shock",
        "door_divination_arts", "door_spiritual_vision", "door_spiritual_intuition", "door_record",
        "door_decrypt_patterns", "door_physical_enhancement", "door_travellers_door",
        "sun_singing", "sun_physical_enhancement", "sun_sunshine", "sun_blessing", "sun_evil_detection",
        "sun_daytime", "sun_night_vision", "sun_holy_light_summoning", "sun_halo", "sun_cleave_of_purification",
        "sun_resistance_to_horror", "sun_holy_water", "sun_notarization", "sun_contract", "sun_holy_wave",
        "justiciar_physique", "justiciar_authority", "justiciar_arbiters_gaze", "justiciar_area_of_jurisdiction",
        "justiciar_recognition", "justiciar_intuition", "justiciar_whip_of_pain", "justiciar_psychic_piercing",
        "justiciar_brand_of_restraint", "justiciar_psychic_lashing", "justiciar_verdict", "justiciar_power_prohibition",
        "justiciar_punishment",
        "death_undead_knowledge", "death_cadaveric_resilience", "death_gloomy_presence", "death_death_sight",
        "death_spirit_vision", "death_physique", "death_spirit_communication", "death_eye_of_death",
        "death_spirit_perception", "death_voice_of_the_dead", "death_spirit_channeling", "death_corpse_guise",
        "death_spirit_world_knowledge", "death_language_of_the_dead", "death_resurrection", "death_spirit_pact",
        "death_gatekeeper_step", "death_door_to_the_underworld", "death_internal_underworld",
        "error_shadow_theft", "error_observation", "error_physique", "error_swindler_charm", "error_eloquence",
        "error_thought_misdirection", "error_mental_disruption", "error_material_theft", "error_decryption",
        "error_power_theft", "error_mental_fortitude", "error_conceptual_thought", "error_conceptual_attack",
        "error_conceptual_ideal", "error_conceptual_dream", "error_conceptual_capacity", "error_conceptual_heart",
        "error_theft_of_heavenly_secrets",
        "tyrant_physique", "tyrant_illusory_scales", "tyrant_stance", "tyrant_sea_lunge", "tyrant_wrath",
        "tyrant_raging_blow", "tyrant_sea_memory", "tyrant_precise_throw", "tyrant_water_film", "tyrant_azure_wave",
        "tyrant_restorative_water", "tyrant_cleansing_light", "tyrant_pressure_wave", "tyrant_wind_flight",
        "tyrant_wind_sprint", "tyrant_wind_imbued_hands", "tyrant_wind_pull", "tyrant_eavesdrop", "tyrant_water_control",
        "tyrant_storm_heart", "tyrant_abyssal_dominion", "tyrant_lightning_strike", "tyrant_lightning_bolt_arrow",
        "tyrant_thunder_voice", "tyrant_singing", "tyrant_sea_tongue", "tyrant_water_curtain",
        "tyrant_corrosive_deluge", "tyrant_slick_water", "tyrant_wind_binding",
        "whitetower_enhanced_mind", "whitetower_deduction", "whitetower_agility", "whitetower_physical_enhancement",
        "whitetower_crime_reconstruction", "whitetower_analysis", "whitetower_spellcasting", "whitetower_mirror_curse",
    ).associateWith { modId("ability/$it") }

    val foolOverrides = mapOf(
        "common_ritual_magic" to modId("ability/fool_ritual_magic"),
        "door_burning" to modId("ability/fool_burning"),
    )

    val doorOverrides = mapOf(
        "door_anti_divination" to modId("ability/door_anti_divination"),
    )

    val sunOverrides = mapOf(
        "common_ritual_magic" to modId("ability/sun_ritual_magic"),
    )

    val justiciarOverrides = mapOf(
        "common_combat_proficiency" to modId("ability/justiciar_combat_proficiency"),
        "door_anti_divination" to modId("ability/justiciar_anti_divination"),
    )

    val deathOverrides = mapOf(
        "common_ritual_magic" to modId("ability/death_ritual_magic"),
    )

    val errorOverrides = mapOf(
        "common_combat_proficiency" to modId("ability/error_combat_proficiency"),
    )

    val whiteTowerOverrides = mapOf(
        "common_ritual_magic" to modId("ability/whitetower_ritual_magic"),
        "common_combat_proficiency" to modId("ability/whitetower_combat_proficiency"),
    )

    fun texture(identity: String, pathway: String? = ClientAbilityState.roster?.pathwayId): Identifier? =
        when (pathway) {
            "Fool" -> foolOverrides[identity]
            "Door" -> doorOverrides[identity]
            "Sun" -> sunOverrides[identity]
            "Justiciar" -> justiciarOverrides[identity]
            "Death" -> deathOverrides[identity]
            "Error" -> errorOverrides[identity]
            "WhiteTower" -> whiteTowerOverrides[identity]
            else -> null
        } ?: textures[identity]

    fun drawTexture(graphics: GuiGraphicsExtractor, identity: String, x: Int, y: Int, size: Int, alpha: Float = 1f): Boolean {
        val texture = texture(identity) ?: return false
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, texture, x, y, size, size, UiTheme.argb(0xFFFFFF, alpha))
        // Keep the common bevel one GUI pixel wide at every display size.
        val light = UiTheme.argb(0xAAAAB4, alpha)
        val shade = UiTheme.argb(0x4B4C58, alpha)
        graphics.fill(x, y, x + size, y + 1, light)
        graphics.fill(x, y + 1, x + 1, y + size, light)
        graphics.fill(x + 1, y + size - 1, x + size, y + size, shade)
        graphics.fill(x + size - 1, y + 1, x + size, y + size - 1, shade)
        return true
    }

    val patterns = mapOf(
        "lyre" to "##.....##/#+#...#+#/##+###+##/.#.#.#.#./.#.#.#.#./.#.#.#.#./..#.#.#../...###...",
        "eye" to "....###..../..##...##../.#...+...#./#...+++...#/.#...+...#./..##...##../....###....",
        "hand" to ".....##..../..##.#+#.../..#+##+#.../...#+#++#../.###+#++#../#+##++++#../.##+++++#../..#++++#.../...####....",
        "bolt" to "......+++/.....++#./....++#../...++#.../..++####./.++++++../...#++.../..#++..../.#++...../+++......",
        "flame" to ".....+...../....#+...../...#+#..+../..#++#.#.../.#+++##+#../.#++++++#../#+++##+++#/#++#++#++#/.#+#++#+#./..#++++#../...####...",
        "wave" to ".........+./..##...##../.#++#.#++#./#....#....#/.........../..##...##../.#++#.#++#./#....#....#/.........../..##...##..",
        "wing" to ".........++/.#......++#/.##....++#./.#.#..++#../.#++#++#.../..#+++#..../...#+#...../..#+#....../.#+#......./###........",
        "drop" to "....+..../...#+..../..#++#.../..#++#.../.#++++#../.#++#+#../#++#+++#./#+++#++#./.#++++#../..#####..",
        "shield" to ".#########./.#+++++++#./.#++#++++#./.#++#++++#./.#+++++++#./..#+++++#../..#+++++#../...#+++#.../....#+#..../.....#.....",
        "scroll" to "..########./.#+++++++#./###++++++#./..#.####.#./..#++++++#./..#.####.#./..#++++++#./..#+++++###/..#+++++#.#/...#######.",
        "seal" to "....+..../..##+##../.#..#..#./#..#+#..#/#.#+++#.#/+++#.#+++/..#+++#../..#...#../.##...##.",
        "book" to ".####.####./#++++#++++#/#.##.#.##.#/#++++#++++#/#.##.#.##.#/#++++#++++#/#.##.#.##.#/#++++#++++#/.####+####.",
        "mirror" to "...#####.../..#+++++#../.#+++#+++#./.#++#++++#./.#+#+++++#./.#+++++++#./..#+++++#../...#####.../.....#...../...#####...",
        "mind" to "..###.###../.#++##+++#./#++#++#+++#/#++++#++++#/#.##+#.##.#/#++#++#+++#/.#++##+++#./..###.###../.....#...../....###....",
        "mask" to ".#########./#+++++++++#/#.##...##.#/#+++++++++#/.#..###..#./.#.#...#.#./..#.....#../...#...#.../....###....",
        "pendulum" to "....+..../....#..../....#..../....#..../...###.../..#+++#../.#+++++#./..#+++#../...#+#.../....#....",
        "gate" to "...#####.../..#+++++#../.#++###++#./.#++#.#++#./.#++#.#++#./.#++#.#++#./.#++#.#+##/.#++#.#++#./.#++#.#++#./.#########.",
        "dagger" to "........++/.......++#/......++#./.....++#../....++#.../.#.++#..../..#++#..../...##...../..#.##..../.#...#..../#.........",
        "thread" to "++...++...++/.#....#....#./.##########./......#....../...#######.../...#..#..#.../...#..#..#.../...+..+..+.../..+#..+#..+#.",
        "skull" to "...#####.../..#+++++#../.#+++++++#./.#.##.##.#./.#.##.##.#./..#++#++#../...#+++#.../...#.#.#.../....###....",
        "speech" to ".#########./#+++++++++#/#.##.##.#.#/#+++++++++#/#.#####.#.#/#+++++++++#/.###..####./...#.#...../...##......",
        "heart" to "..###.###../.#+++#+++#./#+++++++++#/#+++++++++#/.#+++++++#./..#+++++#../...#+++#.../....#+#..../.....#.....",
        "blade" to "......+++/.....++#./....++#../...++#.../..++#..../.#+#...../..##...../.#.##..../#...#....",
        "boot" to "..######.../..#++++#.../..#++++#.../..#++++#.../..#++++###./..#+++++++#/..#+++++++#/..#########/..#.#.#.#.#",
        "sun" to ".....+...../.+...#...+./..#..#..#../...#####.../..#+++++#../###+++++###/..#+++++#../...#####.../..#..#..#../.+...#...+./.....+.....",
        "moon" to "....####.../..##++#..../.#+++#...../.#++#....../#+++#....../#+++#....../.#++#....../.#+++#...#./..##++###../....####...",
        "chains" to "....####.../...#++++#../..#++##+#../.#++#..#.../#++#..#..../.#++##++#../..#++++#.../...####....",
        "star" to ".....+...../.....#...../....#+#..../...#+++#.../+##+++++##+/..#+++++#../...#+++#.../..#+#.#+#../..##...##..",
        "arrow" to ".....+...../....++#..../...++#.#.../..++#..#.../.++#...#.../++#....#.../.......#.../.......#.../.......#.../.......+...",
        "scales" to ".....+...../.....#...../.#########./..#..#..#../.#.#.#.#.#./#...###...#/#####.#####/.....#...../.....#...../...#####...",
        "spiral" to "...#####.../..#+++++#../.#++###++#./#++#...#++#/#++#.#+#++#/#++#.#+++#./#++#..###../.#++#....../..#++#####./...#++++++#/....######.",
        "crown" to "+....+....+/##...#...##/#+#.#.#.#+#/.#+#+++#++#/.#+++++++#./..#+++++#../..#######../..#+++++#../..#######..",
        "card" to ".########./.#++++++#./.#..+...#./.#.#+#..#./.##+++#.#./.#.#+#..#./.#..+...#./.#++++++#./.########.",
    ).mapValues { (_, source) ->
        val rows = source.split('/')
        val points = rows.flatMapIndexed { y, row -> row.indices.filter { row[it] != '.' }.map { x -> x to y } }
        val minX = points.minOf { it.first }
        val maxX = points.maxOf { it.first }
        val minY = points.minOf { it.second }
        val maxY = points.maxOf { it.second }
        val w = maxX - minX + 1
        val left = (15 - w) / 2
        val top = (15 - (maxY - minY + 1)) / 2
        List(15) { y ->
            rows.getOrNull(y - top + minY)?.takeIf { y >= top && y - top + minY <= maxY }
                ?.padEnd(maxX + 1, '.')?.substring(minX, maxX + 1)
                ?.let { ".".repeat(left) + it + ".".repeat(15 - left - w) }
                ?: ".".repeat(15)
        }
    }

    private val families = mapOf(
        "lyre" to "sun_singing tyrant_singing tyrant_thunder_voice",
        "eye" to "sun_evil_detection death_death_sight death_eye_of_death death_spirit_perception death_spirit_vision door_spiritual_vision error_observation fool_danger_intuition fool_spirit_vision fool_thread_sight justiciar_arbiters_gaze justiciar_intuition justiciar_recognition visionary_danger_sense visionary_intent_reader visionary_scan_gaze visionary_scan_gaze_passive visionary_sharp_vision",
        "hand" to "sun_physical_enhancement death_physique door_physical_enhancement error_physique justiciar_physique tyrant_physique tyrant_raging_blow tyrant_wind_imbued_hands visionary_physical_enhancement whitetower_physical_enhancement",
        "bolt" to "door_electric_shock tyrant_lightning_bolt_arrow tyrant_lightning_strike tyrant_storm_heart tyrant_wrath",
        "flame" to "door_burning fool_flame_jump sun_fire_of_light",
        "wave" to "sun_holy_wave tyrant_abyssal_dominion tyrant_azure_wave tyrant_corrosive_deluge tyrant_pressure_wave tyrant_sea_memory tyrant_sea_tongue tyrant_slick_water tyrant_water_control tyrant_water_curtain tyrant_water_film",
        "wing" to "tyrant_wind_binding tyrant_wind_flight tyrant_wind_pull tyrant_wind_sprint tyrant_windblades",
        "drop" to "sun_holy_water fool_aquatic_breath tyrant_restorative_water",
        "shield" to "sun_resistance_to_horror death_cadaveric_resilience death_gloomy_presence door_anti_divination error_mental_fortitude tyrant_illusory_scales visionary_dragon_scale",
        "scroll" to "sun_contract sun_holy_oath sun_notarization death_spirit_pact door_record",
        "seal" to "common_ritual_magic sun_blessing",
        "book" to "death_spirit_world_knowledge death_undead_knowledge whitetower_analysis whitetower_crime_reconstruction whitetower_deduction whitetower_spellcasting",
        "mirror" to "whitetower_mirror_curse",
        "mind" to "error_decryption error_thought_misdirection visionary_alteration visionary_appeasement visionary_battle_hypnotism visionary_good_memory visionary_guidance visionary_psychic_cue visionary_telepathy whitetower_enhanced_mind door_decrypt_patterns",
        "mask" to "death_corpse_guise error_swindler_charm fool_expression_control fool_illusion_creation fool_shapeshifting visionary_psychological_invisibility",
        "pendulum" to "door_divination_arts door_spiritual_intuition fool_divination_arts",
        "gate" to "death_door_to_the_underworld death_gatekeeper_step death_internal_underworld door_blink door_escape_trick door_opening door_travellers_door",
        "dagger" to "error_material_theft error_power_theft error_shadow_theft error_theft_of_heavenly_secrets",
        "thread" to "fool_marionette_possession fool_marionettist_control",
        "skull" to "death_resurrection death_spirit_channeling",
        "speech" to "death_language_of_the_dead death_spirit_communication death_voice_of_the_dead error_eloquence tyrant_eavesdrop",
        "heart" to "fool_damage_transfer",
        "blade" to "common_combat_proficiency sun_cleave_of_purification fool_paper_cutter justiciar_psychic_lashing justiciar_psychic_piercing justiciar_punishment justiciar_whip_of_pain",
        "boot" to "fool_agility tyrant_sea_lunge tyrant_stance whitetower_agility",
        "sun" to "sun_daytime sun_halo sun_holy_light_summoning sun_sunshine tyrant_cleansing_light door_flash",
        "arrow" to "fool_air_bullet tyrant_precise_throw",
        "scales" to "justiciar_area_of_jurisdiction justiciar_verdict",
        "card" to "fool_paper_substitution",
        "moon" to "sun_night_vision visionary_dream_traversal",
        "chains" to "justiciar_brand_of_restraint justiciar_power_prohibition",
        "spiral" to "error_mental_disruption visionary_surge_of_insanity",
        "crown" to "justiciar_authority",
    )

    val abilities = families.flatMap { (symbol, ids) -> ids.split(' ').map { it to symbol } }.toMap()

    fun symbol(identity: String): String = abilities[identity] ?: when {
        identity.startsWith("error_conceptual_") -> "dagger"
        identity.startsWith("door_recorded_") -> "scroll"
        else -> "star"
    }

    fun draw(graphics: GuiGraphicsExtractor, identity: String, x: Int, y: Int, rgb: Int, size: Int = SIZE) {
        if (drawTexture(graphics, identity, x, y, size)) return
        val left = x + (size - 15) / 2
        val top = y + (size - 15) / 2
        val ink = UiTheme.argb(rgb, 1f)
        val highlight = UiTheme.argb(0xE6D4AB, 1f)
        patterns.getValue(symbol(identity)).forEachIndexed { dy, row ->
            row.forEachIndexed { dx, pixel ->
                if (pixel != '.') graphics.fill(left + dx, top + dy, left + dx + 1, top + dy + 1, if (pixel == '+') highlight else ink)
            }
        }
    }
}
