package me.vangoo.mysteries.client.render

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.client.Camera
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.state.level.QuadParticleRenderState
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.particles.ColorParticleOption
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3

/**
 * Знак духу: та сама текстура, що в [SpiritMoteParticle], але **стала** — і, якщо його
 * поставили на гравця, така, що **їде разом із ним**.
 *
 * ## Чому іскра не годиться для фігури
 *
 * Порошинка духу навмисно жива: випадкове життя (16–28 тіків), випадковий розмір, спалах
 * на початку, згасання в кінці, від'ємна гравітація. Розсипу, покриву й сліду це потрібно
 * — вони й мусять мерехтіти. Сталій фігурі це заважає: німб із мотів читався як хмара,
 * що зринає.
 *
 * ## Розкид домішує сам конструктор — тож береться інший
 *
 * Ванільний `Particle(level, x, y, z, xd, yd, zd)` **не бере швидкість як є**: він
 * домішує випадковий розкид і перенормовує результат. Поруч є
 * `SingleQuadParticle(level, x, y, z, sprite)` — той самий батько **без** швидкості й без
 * розкиду. Знак будується через нього.
 *
 * ## Головне: знак чіпляється до власника
 *
 * Порошинка статична **у світі**: поставлена, вона лишається де поставлена. Фігура над
 * головою від цього неминуче відстає, і жодне з двох очевидних лікувань не спрацювало:
 *
 * | спроба | чому не вийшло |
 * |---|---|
 * | коротше життя плюс перемальовування щотіку | слід став коротшим, але лишився |
 * | віддати порошинці швидкість гравця | пакет доходить із затримкою, тож кільце просто стало стабільно позаду голови |
 *
 * Обидві лікували наслідок. Причина в тому, що фігура **належить сутності**, а не точці
 * світу, — тож знак і тримає сутність: щотіку він ставить себе туди, де власник **зараз**.
 *
 * ## Зсув приїжджає в пакеті, а не рахується з позиції
 *
 * І цього замало, якщо зсув виводити з місця народження: сервер надсилає координату, поки
 * пакет іде — гравець зрушив, і знак закріплюється зі зсувом, зміщеним рівно на пройдену
 * за цей час відстань. Кожне покоління закріплюється зі своїм, і замість кільця виходить
 * жменька кілець позаду — той самий слід, лише коротший.
 *
 * Тому зсув **передається явно**, полем швидкості пакета: у формі `count = 0` трійка
 * дельт доїжджає до клієнта незміненою, а рухати знак усе одно нічим — він не летить.
 * Позиція в пакеті після цього потрібна рівно для одного: знайти власника.
 *
 * Результат — фігура, жорстко зчеплена з власником: затримка мережі на неї не впливає
 * взагалі, бо ніщо в ній не виражене у світових координатах.
 *
 * ponytail: власник шукається як **найближчий гравець** у межах [LATCH_RANGE] бл. — це
 * однозначно для фігури, що обвиває голову (німб має радіус 0,28), і коштує нуль нових
 * пакетів. Якщо колись знадобиться фігура, що чіпляється до довільної сутності здалеку,
 * доведеться везти її ідентифікатор у власному типі опцій: `ColorParticleOption` місця
 * під це не має.
 */
class SpiritGlyphParticle private constructor(
    level: ClientLevel,
    x: Double,
    y: Double,
    z: Double,
    /** Зсув від маківки власника. Приїхав у пакеті — не рахується з позиції. */
    private val offset: Vec3,
    options: ColorParticleOption,
    sprite: TextureAtlasSprite,
    // Конструктор **без** швидкості: саме він не домішує розкиду.
) : SingleQuadParticle(level, x, y, z, sprite) {

    /**
     * Кого тримати. `null` — знак поставлено не на гравця, тож він просто стоїть.
     *
     * Міряється відстань до **маківки**, а не до `Entity.distanceToSqr`, яка рахує від
     * **ніг**. На цьому й зірвалась перша редакція зчеплення: німб висить на ~2,1 бл.
     * вище ніг, тобто далі за будь-яку розумну межу, і власник не знаходився ніколи —
     * знак мовчки лишався статичним, а слід нікуди не дівався.
     */
    private val owner: AbstractClientPlayer? = level.players()
        // Нульовий зсув означає «фігура стоїть у світі», а не «на маківці»: її ставлять
        // формою `count = 1` з нульовими дельтами. Без цієї перевірки двері, намальовані
        // за крок від гравця, мовчки чіплялись би йому на голову й їхали разом із ним —
        // і виглядало б це не як помилка, а як «ефект чомусь не там».
        .takeIf { offset != Vec3.ZERO }
        ?.minByOrNull { headOf(it).distanceToSqr(x, y, z) }
        ?.takeIf { headOf(it).distanceToSqr(x, y, z) <= LATCH_RANGE * LATCH_RANGE }

    init {
        setColor(options.red, options.green, options.blue)
        lifetime = LIFETIME
        gravity = 0f
        friction = 1f
        hasPhysics = false
        quadSize = SIZE
        alpha = 1f
        pin(0f)
    }

    override fun getLayer(): SingleQuadParticle.Layer = SingleQuadParticle.Layer.TRANSLUCENT

    /** Світиться сам: фігура чужої сили не має темніти в тіні. */
    override fun getLightCoords(partialTick: Float): Int = LightCoordsUtil.FULL_BRIGHT

    /** Тільки вік: місце знака рахується в мить рендеру, а не тут. */
    override fun tick() {
        if (age++ >= lifetime || owner?.isRemoved == true) remove()
    }

    /**
     * Місце знака рахується **в мить рендеру**, а не в такті.
     *
     * Це і був залишковий слід. Власник знаходився, зсув доїжджав точним, знак чесно
     * ставив себе в такті туди, де власник **у цьому тіку**, — але частинки цокають
     * окремо від руху гравця, тож знак стабільно відставав рівно на тік. При швидкості
     * 0,2 бл./тік і двох поколіннях це й давало короткий шлейф, який не брало жодне
     * лікування на боці такту.
     *
     * Тут знак бере інтерпольовану маківку за тим самим [partialTick], яким рендериться
     * сама модель гравця, і ставить `xo == x`, щоб лерп рендера не зсунув його назад.
     * Після цього фігура рухається **кадр у кадр** із власником: відставати нема від чого.
     */
    override fun extract(state: QuadParticleRenderState, camera: Camera, partialTick: Float) {
        pin(partialTick)
        super.extract(state, camera, partialTick)
    }

    /** Ставить знак на інтерпольовану маківку власника. Без власника — лишає, де є. */
    private fun pin(partialTick: Float) {
        val rider = owner ?: return
        val t = partialTick.toDouble()
        x = Mth.lerp(t, rider.xOld, rider.x) + offset.x
        y = Mth.lerp(t, rider.yOld, rider.y) + rider.bbHeight + offset.y
        z = Mth.lerp(t, rider.zOld, rider.z) + offset.z
        // Без цього рендер лерпнув би від минулого місця — тобто знову назад.
        xo = x
        yo = y
        zo = z
    }

    class Provider(private val sprites: SpriteSet) : ParticleProvider<ColorParticleOption> {
        override fun createParticle(
            options: ColorParticleOption,
            level: ClientLevel,
            x: Double,
            y: Double,
            z: Double,
            xd: Double,
            yd: Double,
            zd: Double,
            random: RandomSource,
            // Дельти в цій формі пакета — не швидкість, а **зсув від маківки**: знак
            // нікуди не летить, тож поле вільне, а зсув мусить приїхати точним.
        ): Particle = SpiritGlyphParticle(level, x, y, z, Vec3(xd, yd, zd), options, sprites.get(random))
    }

    private companion object {
        /**
         * Маківка власника — точка, від якої міряється зчеплення.
         *
         * `Entity.distanceToSqr` рахує від **ніг**, і саме на цьому зірвалась перша
         * редакція зчеплення: німб висить на ~2,1 бл. вище, тобто далі за будь-яку
         * розумну межу, тож власник не знаходився ніколи.
         */
        fun headOf(player: AbstractClientPlayer): Vec3 =
            Vec3(player.x, player.y + player.bbHeight, player.z)

        /**
         * Знак чіпляється до власника, тож довге життя вже не шкодить: сліду більше
         * немає. Чотири тіки — два покоління при перемальовуванні раз на два тіки, щоб
         * кільце не блимало на стику.
         */
        const val LIFETIME = 4

        /** Сталий і трохи дрібніший за пікову іскру: форму дає кільце, а не пляма. */
        const val SIZE = 0.075f

        /** Метр від маківки: фігура на голові, а не сусід, що проходив повз. */
        const val LATCH_RANGE = 1.0

    }
}
