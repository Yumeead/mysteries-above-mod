package me.vangoo.mysteries.item

import me.vangoo.mysteries.modId
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries

/**
 * The mod's data components — what an item carries inside it.
 *
 * The same trick as in `ModChannels`: declaring them here **is** registering them, so a
 * component declared past this object will not be in the registry, and an item carrying it will
 * not fail to save silently — it will fail on serialisation.
 */
object ModComponents {

    /** The contents of a Beyonder potion: the pathway and the Sequence. */
    val POTION_BREW: DataComponentType<PotionBrew> = register("potion_brew") {
        it.persistent(PotionBrew.CODEC).networkSynchronized(PotionBrew.STREAM_CODEC)
    }

    /**
     * Touches the class, forcing it to initialise.
     *
     * Kotlin initialises an `object` lazily, while Minecraft's registries close after loading —
     * a component declared here and needed by nobody so far would get into the registry too
     * late. Called from the entrypoint.
     */
    fun register() = Unit

    private fun <T : Any> register(
        path: String,
        configure: (DataComponentType.Builder<T>) -> DataComponentType.Builder<T>,
    ): DataComponentType<T> = Registry.register(
        BuiltInRegistries.DATA_COMPONENT_TYPE,
        modId(path),
        configure(DataComponentType.builder()).build(),
    )
}
