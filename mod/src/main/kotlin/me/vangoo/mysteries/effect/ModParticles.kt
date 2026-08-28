package me.vangoo.mysteries.effect

import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes
import net.minecraft.core.Registry
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.core.particles.ParticleType
import net.minecraft.core.registries.BuiltInRegistries

/**
 * Частинки мода. Оголошення тут — і є реєстрацією, як у `ModItems` та `ModChannels`.
 *
 * ## Перша власна текстура мода
 *
 * До цієї фази мод не ніс жодної: зілля — ванільна пляшка з тінтом, іконки —
 * ванільні шарди. Порошинка духу її ламає, і навмисно: ванільний `DUST` — розмита
 * квадратна пляма, і двадцять два шляхи, розрізнені лише кольором цієї плями,
 * виглядали б однією силою в двадцяти двох відтінках.
 *
 * ## Чому колір, а не `SimpleParticleType`
 *
 * Увесь словник фігур побудований навколо кольору шляху, тож безбарвна частинка
 * була б у ньому нікому не потрібна. Носій кольору не пишеться: ванільний
 * [ColorParticleOption] уже є, і разом із ним — обидва кодеки. Свій клас опцій
 * додав би до цього рівно нуль.
 */
object ModParticles {

    /**
     * Порошинка духу: ромб із твердими краями, що спалахує й гасне.
     *
     * Форма загальна навмисно — «крихта живої речовини», — а чим вона є, каже колір:
     * вода Мореплавця, світло Сонця, попіл Смерті.
     */
    val SPIRIT_MOTE: ParticleType<ColorParticleOption> = FabricParticleTypes.complex(
        { type -> ColorParticleOption.codec(type) },
        { type -> ColorParticleOption.streamCodec(type) },
    )

    /** Змушує клас ініціалізуватись до того, як реєстри закриються. */
    fun register() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, modId("spirit_mote"), SPIRIT_MOTE)
    }

    /**
     * Порошинка в заданому кольорі.
     *
     * Альфа виставляється тут: [RgbColor.packed] несе лише RGB, а нуль у старшому
     * байті зробив би частинку прозорою — тобто невидимою, і без жодної помилки в лозі.
     */
    fun mote(color: RgbColor): ColorParticleOption =
        ColorParticleOption.create(SPIRIT_MOTE, OPAQUE or color.packed)

    private const val OPAQUE = 0xFF shl 24
}
