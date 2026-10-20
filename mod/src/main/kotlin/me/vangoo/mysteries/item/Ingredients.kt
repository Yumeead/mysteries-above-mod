package me.vangoo.mysteries.item

import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents
import net.minecraft.ChatFormatting
import net.minecraft.ChatFormatting.AQUA
import net.minecraft.ChatFormatting.BLUE
import net.minecraft.ChatFormatting.DARK_AQUA
import net.minecraft.ChatFormatting.DARK_BLUE
import net.minecraft.ChatFormatting.DARK_GRAY
import net.minecraft.ChatFormatting.DARK_GREEN
import net.minecraft.ChatFormatting.DARK_PURPLE
import net.minecraft.ChatFormatting.DARK_RED
import net.minecraft.ChatFormatting.GOLD
import net.minecraft.ChatFormatting.GRAY
import net.minecraft.ChatFormatting.GREEN
import net.minecraft.ChatFormatting.LIGHT_PURPLE
import net.minecraft.ChatFormatting.RED
import net.minecraft.ChatFormatting.WHITE
import net.minecraft.ChatFormatting.YELLOW
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTabs
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/**
 * The potion ingredients: 163 registered items, one per thing the plugin's `custom-items.yml` named.
 *
 * The plugin had one material for all of them (a music disc, so Fool's paper stayed paper) and told
 * them apart by a `custom_model_data` string that a separately served resource pack mapped to a
 * texture. Here each is an item of its own: `/give mysteries_above:sphinx_brain` works, a loot table
 * names it directly, a recipe matches it by id, and it stacks like any other ingredient. Name colours
 * are the plugin's, one per line.
 *
 * Grouped as the plugin grouped them, by the pathway and Sequence whose potion first asks for it.
 * The recipes themselves are not here — they arrive with brewing.
 */
object Ingredients {

    private class Ingredient(properties: Item.Properties, private val colour: ChatFormatting) : Item(properties) {
        override fun getName(stack: ItemStack): Component = super.getName(stack).copy().withStyle(colour)
    }

    /** Every ingredient by its id, in catalogue order. */
    val all: Map<String, Item> = listOf(
        // Visionary, Посл. 9
        ingredient("manhal_fish_eyeball", GRAY),
        ingredient("goat_horned_blackfish_blood", GRAY),
        ingredient("autumn_crocus_essence", GRAY),
        ingredient("elf_flower_petals", GRAY),
        // Visionary, Посл. 8
        ingredient("crystalline_moon_orchid", GRAY),
        ingredient("thought_essence", GRAY),
        ingredient("iridescent_pearl_dust", GRAY),
        // Visionary, Посл. 7
        ingredient("tree_of_elders_fruit", AQUA),
        ingredient("mirror_dragon_eyes", AQUA),
        ingredient("mirror_dragon_blood", AQUA),
        // Visionary, Посл. 6
        ingredient("black_hunting_lizard_spinal_fluid", AQUA),
        ingredient("illusory_chime_tree_fruit", AQUA),
        ingredient("mind_crystal_powder", AQUA),
        ingredient("split_personality_essence", AQUA),
        // Visionary, Посл. 5
        ingredient("dream_catcher_heart", GOLD),
        ingredient("mind_illusion_crystal", GOLD),
        ingredient("adult_mind_dragon_blood", GOLD),

        // Door, Посл. 9
        ingredient("dimensional_wanderer_eye", GRAY),
        ingredient("ever_shifting_lotus", GRAY),
        // Door, Посл. 8
        ingredient("spirit_eater_pouch", GRAY),
        ingredient("deep_sea_marlin_blood", GRAY),
        ingredient("string_grass_powder", GRAY),
        ingredient("red_chestnut_flower", GRAY),
        // Door, Посл. 7
        ingredient("meteorite_crystal", AQUA),
        ingredient("lavos_squid_blood", AQUA),
        ingredient("starfaced_stone", AQUA),
        // Door, Посл. 6
        ingredient("asmann_complete_brain", AQUA),
        ingredient("cursed_wraith_artifact", AQUA),
        ingredient("imbued_ink_sac", AQUA),
        // Door, Посл. 5
        ingredient("void_drifter_heart", GREEN),
        ingredient("astral_mist_essence", GREEN),
        ingredient("wayfinder_tree_root", YELLOW),

        // Justiciar, Посл. 9
        ingredient("heart_of_unquestioned_command", GOLD),
        ingredient("bone_of_the_unyielding_frame", GOLD),
        ingredient("eye_of_judicial_insight", GOLD),
        // Justiciar, Посл. 8
        ingredient("pair_of_terror_demon_worms_eyes", GOLD),
        ingredient("silver_war_bears_right_palm", GOLD),
        ingredient("eye_of_perfect_recognition", GOLD),
        ingredient("thread_of_supernatural_intuition", GOLD),
        // Justiciar, Посл. 7
        ingredient("horn_of_a_flash_patterned_black_snake", GOLD),
        ingredient("dust_of_lake_spirit", GOLD),
        ingredient("ashes_of_a_broken_oath_seal", GOLD),
        // Justiciar, Посл. 6
        ingredient("heart_of_the_silent_verdict", GOLD),
        ingredient("fragment_of_a_sealed_domain", GOLD),
        ingredient("ashes_of_a_forbidden_decree", GOLD),
        ingredient("chains_of_collective_fear", GOLD),
        // Justiciar, Посл. 5
        ingredient("core_of_retributive_authority", GOLD),
        ingredient("brand_of_the_unforgiving_oath", DARK_AQUA),
        ingredient("ashes_of_a_public_execution", DARK_BLUE),
        ingredient("shackles_of_the_guilty", DARK_GREEN),

        // WhiteTower, Посл. 9
        ingredient("manticore_bird_pituitary_gland", GOLD),
        ingredient("light_antelope_blood", GOLD),
        ingredient("rock_crystal_powder", GOLD),
        ingredient("peppermint_extract", GOLD),
        // WhiteTower, Посл. 8
        ingredient("crystallized_cave_monkey_brain", GOLD),
        ingredient("memory_flower_petals", GOLD),
        ingredient("tree_of_wisdom_sap", GOLD),
        ingredient("yellow_amber_powder", GOLD),
        // WhiteTower, Посл. 7
        ingredient("phantom_python_vertical_eye", GOLD),
        ingredient("steel_toothed_wolf_heart", GOLD),
        ingredient("mica_powder", GOLD),
        // WhiteTower, Посл. 6
        ingredient("prismatic_chameleon_heart", GOLD),
        ingredient("liquid_silver_phantom_residue", GOLD),
        ingredient("mimicry_moss_spores", GOLD),
        ingredient("devastated_blank_mask", GOLD),
        // WhiteTower, Посл. 5
        ingredient("void_beholder_petrified_eye", GOLD),
        ingredient("condensed_astral_nebula", DARK_AQUA),
        ingredient("ancient_rune_powder", DARK_BLUE),
        ingredient("pure_spirituality_tear", DARK_GREEN),

        // Fool, Посл. 9
        ingredient("star_crystal", AQUA),
        ingredient("poison_hemlock", GRAY),
        ingredient("dragon_blood_grass_powder", GRAY),
        // Fool, Посл. 8
        ingredient("hornacis_goat_horn", GRAY),
        ingredient("human_faced_rose_stalk", GRAY),
        ingredient("black_rimmed_sunflower_powder", GRAY),
        ingredient("golden_cloak_grass_powder", GRAY),
        // Fool, Посл. 7
        ingredient("mist_treant_root", GRAY),
        ingredient("dark_panther_spinal_fluid", GRAY),
        ingredient("fantasy_grass_oil", GRAY),
        ingredient("droplet_gem_powder", GRAY),
        // Fool, Посл. 6
        ingredient("thousand_faced_hunter_pituitary", GRAY),
        ingredient("human_skin_shadow_characteristic", GRAY),
        ingredient("thousand_faced_hunter_blood", GRAY),
        ingredient("deep_sea_naga_hair", GRAY),
        // Fool, Посл. 5
        ingredient("ancient_wraith_dust", GRAY),
        ingredient("six_winged_gargoyle_core", GRAY),
        ingredient("ancient_wraith_spirituality", GRAY),

        // Sun, Посл. 9
        ingredient("crystal_sunflower", YELLOW),
        ingredient("siren_rock", AQUA),
        ingredient("elf_dark_leaf", DARK_GRAY),
        ingredient("midsummer_grass", GREEN),
        // Sun, Посл. 8
        ingredient("dazzling_soul_powder", YELLOW),
        ingredient("heart_of_magma_titan", RED),
        ingredient("aconite_juice", GRAY),
        // Sun, Посл. 7
        ingredient("dawn_rooster_comb", RED),
        ingredient("radiance_pact_tree_fruit", GOLD),
        ingredient("solidified_magma", DARK_RED),
        ingredient("fingered_citron_powder", YELLOW),
        // Sun, Посл. 6
        ingredient("tree_of_elders_crystalized_roots", AQUA),
        ingredient("spirit_pact_bird_feather", WHITE),
        ingredient("radiance_pact_tree_juice", GOLD),
        ingredient("aqua_fern_juice", DARK_AQUA),
        // Sun, Посл. 5
        ingredient("king_dawn_rooster_comb", RED),
        ingredient("pure_white_brilliant_rock", WHITE),
        ingredient("rosemary", DARK_GREEN),
        ingredient("king_dawn_rooster_blood", DARK_RED),

        // Tyrant, Посл. 9
        ingredient("murloc_bladder", DARK_AQUA),
        ingredient("man_eating_conch", AQUA),
        ingredient("murloc_scales", DARK_GREEN),
        // Tyrant, Посл. 8
        ingredient("deep_diver_alveoli", DARK_AQUA),
        ingredient("war_cry_bird_breastbone", WHITE),
        ingredient("deep_diver_tongue_half", RED),
        ingredient("war_cry_bird_beak_segment", YELLOW),
        // Tyrant, Посл. 7
        ingredient("black_scaled_shark_pituitary", DARK_GRAY),
        ingredient("raging_white_dolphin_heart", WHITE),
        ingredient("black_scaled_shark_fin", DARK_GRAY),
        ingredient("raging_white_dolphin_blood", DARK_RED),
        // Tyrant, Посл. 6
        ingredient("blue_shadow_falcon_feather", AQUA),
        ingredient("sea_condor_eyeballs", DARK_PURPLE),
        ingredient("blue_shadow_falcon_bone", WHITE),
        ingredient("sea_condor_feathers", DARK_AQUA),
        // Tyrant, Посл. 5
        ingredient("siren_throat", AQUA),
        ingredient("crystal_jellyfish_cluster", AQUA),
        ingredient("siren_hair_strands", DARK_AQUA),
        ingredient("crystal_jellyfish_tentacles", AQUA),

        // Error, Посл. 9
        ingredient("blood_speckled_black_mosquito", BLUE),
        ingredient("candle_devourer_core", BLUE, glint = true),
        ingredient("verbena_powder", DARK_GREEN),
        // Error, Посл. 8
        ingredient("human_faced_pitcher_plant", BLUE),
        ingredient("soul_bewitching_insect_larvae", BLUE),
        ingredient("anothers_tears", DARK_AQUA),
        ingredient("white_chestnut_balm", DARK_GREEN),
        // Error, Посл. 7
        ingredient("sphinx_brain", BLUE),
        ingredient("soul_bewitching_adult_insect", BLUE),
        ingredient("sphinx_blood", DARK_AQUA),
        ingredient("soul_bewitching_colony_mucus", DARK_AQUA),
        // Error, Посл. 6
        ingredient("crystal_threadworm", BLUE, glint = true),
        ingredient("cloaked_specter_attachment", BLUE),
        ingredient("cloaked_specter_residual_powder", DARK_GRAY),
        ingredient("larch_essential_oil", DARK_GREEN),
        // Error, Посл. 5
        ingredient("dream_eating_rat_heart", BLUE, glint = true),
        ingredient("spirit_of_depraved_breath", BLUE, glint = true),
        ingredient("dream_eating_rat_blood", DARK_AQUA),
        ingredient("despiritualized_depraved_breath", DARK_GRAY),

        // Death, Посл. 9
        ingredient("desiccated_mature_black_spotted_frog", DARK_GREEN),
        ingredient("cloth_wrapped_persons_crystal", DARK_GRAY, glint = true),
        ingredient("black_lily", DARK_PURPLE),
        // Death, Посл. 8
        ingredient("death_calling_crows_eyeball", DARK_GRAY),
        ingredient("cemetery_blood_rose", DARK_RED),
        ingredient("death_calling_crow_feather", DARK_GRAY),
        ingredient("corpse_scented_essential_oil", DARK_GREEN),
        // Death, Посл. 7
        ingredient("phantom_cats_forepaw", GRAY),
        ingredient("piece_of_spirit_world_crystal", LIGHT_PURPLE, glint = true),
        ingredient("phantom_cat_blood", DARK_AQUA),
        ingredient("leaf_blessed_by_a_natural_spirit", GREEN),
        // Death, Посл. 6
        ingredient("shadow_of_death", DARK_GRAY),
        ingredient("soul_of_a_pale_lich", WHITE, glint = true),
        ingredient("fragmented_bones_of_pale_lich", GRAY),
        ingredient("decomposing_fluid_from_the_dead", DARK_GREEN),
        // Death, Посл. 5
        ingredient("skull_of_an_underworld_wanderer", DARK_GRAY),
        ingredient("crystal_core_left_by_a_thousand_armed_wraith", DARK_PURPLE, glint = true),
        ingredient("piece_of_rotten_flesh_from_underworld_wanderer", DARK_GREEN),
        ingredient("residual_powder_of_a_thousand_armed_wraith", LIGHT_PURPLE),
    ).toMap()

    /** Forces the catalogue to register before the registries close, and shelves it in creative. */
    fun register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register { output ->
            all.values.forEach(output::accept)
        }
    }

    private fun ingredient(id: String, colour: ChatFormatting, glint: Boolean = false): Pair<String, Item> {
        val key = ResourceKey.create(Registries.ITEM, modId(id))
        val properties = Item.Properties().setId(key)
        if (glint) properties.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        return id to Registry.register(BuiltInRegistries.ITEM, key, Ingredient(properties, colour))
    }
}
