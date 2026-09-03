package me.vangoo.mysteries

import net.minecraft.SharedConstants
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.Bootstrap

/**
 * Registries for tests that bring up no world.
 *
 * Half the mod's tests have to build vanilla constants — `MobEffects.NAUSEA` in an ability
 * field, `ItemStack(Items.AMETHYST_SHARD)` in an icon — and without registries those fail with
 * an `ExceptionInInitializerError`. These are registries, not a world: one second for the whole
 * run.
 *
 * ## `Bootstrap` on 26.x no longer binds item components
 *
 * On 1.21.x `ItemStack(Item)` simply worked after `bootStrap()`. On 26.2 an item's default
 * components became **data**: they are assembled by a separate
 * `BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider)` step after the registries, and
 * without it the `ItemStack` constructor fails with `NullPointerException: Components not bound
 * yet`. It looks like a bug in the icon rather than an unfinished bring-up.
 *
 * The standard step does not work here: the `fireResistant()` initialiser asks for the
 * `minecraft:damage_type/is_fire` tag, and tags come from a datapack, i.e. require a
 * `ResourceManager` and essentially half the server. So the prototypes are bound **empty**: the
 * tests need an item that can be put into a stack and sent over the wire, not its vanilla
 * balance. Everything the mod itself puts into a stack lives in a patch on top of the prototype
 * — and is checked as it is.
 *
 * A test that needs the real default components (max stack size, food) does not belong here:
 * that is already a game test.
 *
 * Idempotent: the first test to call it pays, the rest do not.
 */
object GameRegistries {

    @Volatile
    private var ready = false

    @Synchronized
    fun bootstrap() {
        if (ready) return
        ready = true

        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()

        BuiltInRegistries.ITEM.listElements().forEach { holder ->
            if (!holder.areComponentsBound()) holder.bindComponents(DataComponentMap.EMPTY)
        }
    }

    /** The same access to the registries — for `RegistryFriendlyByteBuf` in the codec tests. */
    fun access(): RegistryAccess {
        bootstrap()
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
    }
}
