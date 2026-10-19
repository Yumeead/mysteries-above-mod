package me.vangoo.mysteries.client.render

import net.minecraft.client.gui.GuiGraphicsExtractor

/** Small engraved pictograms describe the power itself; the type remains a separate label. */
object BookAbilityIcons {
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
        "chains" to "justiciar_brand_of_restraint justiciar_power_prohibition justiciar_spawn_prohibition",
        "spiral" to "error_mental_disruption visionary_surge_of_insanity",
        "crown" to "justiciar_authority",
    )

    val abilities = families.flatMap { (symbol, ids) -> ids.split(' ').map { it to symbol } }.toMap()

    fun symbol(identity: String): String = abilities[identity] ?: when {
        identity.startsWith("error_conceptual_") -> "dagger"
        identity.startsWith("door_recorded_") -> "scroll"
        else -> "star"
    }

    fun draw(graphics: GuiGraphicsExtractor, identity: String, x: Int, y: Int, rgb: Int) {
        val ink = UiTheme.argb(rgb, 1f)
        val highlight = UiTheme.argb(0xE6D4AB, 1f)
        patterns.getValue(symbol(identity)).forEachIndexed { dy, row ->
            row.forEachIndexed { dx, pixel ->
                if (pixel != '.') graphics.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, if (pixel == '+') highlight else ink)
            }
        }
    }
}
