package me.vangoo.mysteries

import net.minecraft.SharedConstants
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.Bootstrap

/**
 * Реєстри для тестів, які світу не піднімають.
 *
 * Половина тестів мода мусить будувати ванільні константи — `MobEffects.NAUSEA` у полі
 * здібності, `ItemStack(Items.AMETHYST_SHARD)` в іконці, — а ті без реєстрів падають
 * `ExceptionInInitializerError`. Це реєстри, а не світ: секунда на весь прогін.
 *
 * ## Компонентів предметів `Bootstrap` на 26.x більше не прив'язує
 *
 * На 1.21.x `ItemStack(Item)` після `bootStrap()` просто працював. На 26.2 типові
 * компоненти предмета стали **даними**: їх збирає окремий крок
 * `BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider)` уже після реєстрів,
 * і без нього конструктор `ItemStack` падає `NullPointerException: Components not
 * bound yet`. Виглядає це як помилка в іконці, а не як недороблене підняття.
 *
 * Штатний крок тут не проходить: ініціалізатор `fireResistant()` питає тег
 * `minecraft:damage_type/is_fire`, а теги приїжджають із датапаку, тобто вимагають
 * `ResourceManager` і, по суті, половини сервера. Тому прототипи прив'язуються
 * **порожніми**: тестам потрібен предмет, який можна покласти в стек і провести по
 * дроту, а не його ванільний баланс. Усе, що мод сам кладе в стек, живе в патчі
 * поверх прототипу — і перевіряється як є.
 *
 * Тест, якому знадобляться справжні типові компоненти (макс. розмір стека, харчування),
 * тут не місце: це вже ігровий тест.
 *
 * Ідемпотентний: перший тест, що покликав, платить, решта — ні.
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

    /** Той самий доступ до реєстрів — для `RegistryFriendlyByteBuf` у кодек-тестах. */
    fun access(): RegistryAccess {
        bootstrap()
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
    }
}
