package me.vangoo.mysteries.item

import me.vangoo.mysteries.modId
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries

/**
 * Компоненти даних мода — те, що предмет носить у собі.
 *
 * Той самий прийом, що й у `ModChannels`: оголошення тут **і є** реєстрацією, тож
 * компонента, оголошеного повз цей об'єкт, не буде в реєстрі, і предмет із ним не
 * збережеться мовчки — впаде на серіалізації.
 */
object ModComponents {

    /** Вміст потойбічного зілля: шлях і Послідовність. */
    val POTION_BREW: DataComponentType<PotionBrew> = register("potion_brew") {
        it.persistent(PotionBrew.CODEC).networkSynchronized(PotionBrew.STREAM_CODEC)
    }

    /**
     * Торкається класу, змушуючи його ініціалізуватись.
     *
     * Kotlin ініціалізує `object` ліниво, а реєстри Minecraft закриваються після
     * завантаження — компонент, оголошений тут і нікому досі не потрібний, потрапив би
     * в реєстр надто пізно. Кличеться з entrypoint'у.
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
