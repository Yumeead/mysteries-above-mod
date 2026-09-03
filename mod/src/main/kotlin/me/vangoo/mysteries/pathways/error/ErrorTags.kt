package me.vangoo.mysteries.pathways.error

import me.vangoo.mysteries.modId
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block

/**
 * Error's lists live in a datapack, not in code.
 *
 * In the plugin these were three `Set<Material>`s and a `Map<Material, String>` right inside the
 * abilities' bodies: adding an ore or protecting an artefact meant rebuilding the plugin. A tag
 * does the same thing with data — and does it **extensibly**: the ingredient economy will add its
 * materials to [VALUABLE] with a single file, without touching a line of Kotlin. The same line
 * the mod has already drawn with the potion (a vanilla bottle with a tint) and the sounds
 * (`sounds.json` pointing at vanilla `.ogg`s).
 */
object ErrorTags {

    /** What no theft of the pathway takes — neither `ShadowTheft` nor, later, `MaterialTheft`. */
    val UNSTEALABLE: TagKey<Item> = TagKey.create(Registries.ITEM, modId("unstealable"))

    /** What Observation counts as a valuable in someone else's pocket. */
    val VALUABLE: TagKey<Item> = TagKey.create(Registries.ITEM, modId("valuable"))

    /**
     * What `MaterialTheft` is allowed to pull through a wall.
     *
     * This narrowing **is** the balance of that power: a theft that needs no line of sight must
     * not be able to take a totem. Today the tag holds vanilla brewing matter; the ingredient
     * economy will add its own.
     */
    val SPIRITUAL_MATERIAL: TagKey<Item> = TagKey.create(Registries.ITEM, modId("spiritual_material"))

    /** What Observation counts as valuable ore in a wall. */
    val VALUABLE_ORE: TagKey<Block> = TagKey.create(Registries.BLOCK, modId("valuable_ore"))
}
