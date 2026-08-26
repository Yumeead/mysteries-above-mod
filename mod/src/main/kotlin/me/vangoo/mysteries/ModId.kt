package me.vangoo.mysteries

import net.minecraft.resources.Identifier

/** Мусить збігатися з `id` у fabric.mod.json. */
const val MOD_ID: String = "mysteries_above"

/** Ідентифікатор у просторі імен мода. Ніде не будувати їх рядком вручну. */
fun modId(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
