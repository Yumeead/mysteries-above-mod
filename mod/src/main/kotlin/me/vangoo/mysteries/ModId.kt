package me.vangoo.mysteries

import net.minecraft.resources.Identifier

/** Must match the `id` in fabric.mod.json. */
const val MOD_ID: String = "mysteries_above"

/** An identifier in the mod's namespace. Never build them from a string by hand anywhere. */
fun modId(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
