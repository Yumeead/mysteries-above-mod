package me.vangoo.mysteries.world

import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import java.util.UUID
import kotlin.math.floor

/**
 * **Духовний світ** — перший із світів мода за межами трьох ванільних.
 *
 * Біле, беззвучне дзеркало реального світу: та сама сітка координат, але без жодного
 * його блока. Мандрівник заходить сюди дверима, перетинає білу рівнину пішки й виходить
 * там, де захотів, — і виходить **у ту саму точку X/Z**, з якої дивився б, стоячи тут.
 *
 * ## Світ — це дані, а не код
 *
 * Усе, чим він є, лежить у `data/mysteries_above/`: `dimension_type` (біла імла, вічний
 * полудень, `coordinate_scale: 1.0` — та сама сітка), `dimension` (генератор і біом),
 * `worldgen/noise_settings` (кальцит під сніговою кіркою, поверхня хвилею ±4 бл. навколо
 * y = 64) і `worldgen/biome` (жодної фічі, жодного спавну). Коду тут рівно стільки,
 * скільки треба, щоб гравця сюди привести й повернути назад.
 *
 * ## Співвідношення 1 до 1 — по X і Z, не по Y
 *
 * Вертикаль не дзеркалиться навмисно: реальна печера на y = -40 у білому світі
 * дорівнювала б порожнечі під його підлогою. Гравець стає на білу поверхню в тих самих
 * X/Z, а Y отримує заново — і так само отримує його назад, виходячи.
 */
object SpiritWorld {

    val LEVEL: ResourceKey<Level> = ResourceKey.create(Registries.DIMENSION, modId("spirit_world"))

    fun of(server: MinecraftServer): ServerLevel? = server.getLevel(LEVEL)

    fun holds(player: ServerPlayer): Boolean = player.level().dimension() == LEVEL

    /**
     * Веде гравця в біле: ті самі X/Z, поверхня замість висоти, невагоме тіло.
     *
     * Режим стає **пригодницьким**: духовне тіло не тримає інструмента й не мурує стін,
     * а біле не належить нікому, щоб його розбирати. Попередній режим запам'ятовується
     * тут же — інакше повертати гравця було б нікуди.
     *
     * ⚠ Порядок обов'язковий: `setGameMode` **скидає** здібності під новий режим
     * (`GameType.updatePlayerAbilities`), тож політ дається після нього, а не до.
     */
    fun enter(player: ServerPlayer, spirit: ServerLevel) {
        modes.putIfAbsent(player.uuid, player.gameMode())
        place(player, spirit)
        player.setGameMode(GameType.ADVENTURE)
        player.abilities.mayfly = true
        player.abilities.setFlyingSpeed(flySpeed(DEFAULT_NOTCH))
        player.onUpdateAbilities()
    }

    /** Виводить гравця назад у [home] — знову в ті самі X/Z, у свій режим і свою вагу. */
    fun leave(player: ServerPlayer, home: ServerLevel) {
        place(player, home)
        // Режим сервера — запасний варіант рівно на один випадок: сервер перезапустили,
        // поки гравець був у білому, і пам'ять про його режим не пережила цього.
        player.setGameMode(modes.remove(player.uuid) ?: home.server.defaultGameType)
        player.abilities.flying = false
        player.abilities.setFlyingSpeed(VANILLA_FLY_SPEED)
        player.onUpdateAbilities()
    }

    /**
     * Швидкість польоту за поділкою [notch] (0–8).
     *
     * Коліщатко в духовному світі не має чим перемикати предмети, зате сервер бачить
     * його як зміну активного слота — тож саме воно й керує швидкістю, як у режимі
     * спостерігача. Своєї клавіші й свого пакета для цього не треба.
     */
    fun drift(player: ServerPlayer, notch: Int) {
        val speed = flySpeed(notch)
        if (player.abilities.flyingSpeed == speed) return
        player.abilities.setFlyingSpeed(speed)
        player.onUpdateAbilities()
    }

    fun flySpeed(notch: Int): Float =
        SLOWEST_FLY_SPEED + notch.coerceIn(0, NOTCHES - 1) * FLY_SPEED_STEP

    /**
     * Перша вільна висота над ґрунтом у цій колонці.
     *
     * ⚠ `Level.getHeight` питає heightmap **лише в завантаженого** чанку, а інакше мовчки
     * віддає `sea_level + 1`. У духовному світі це y = 1, тобто дно під усією товщею
     * кальциту: гравець з'являвся замурованим і задихався, і те саме траплялось на
     * поверненні. Тому чанк спершу вантажиться (за потреби генерується) — телепорт усе
     * одно завантажить його за мить, — і лише потім питається висота.
     */
    fun surface(level: ServerLevel, x: Double, z: Double): Double {
        val bx = floor(x).toInt()
        val bz = floor(z).toInt()
        level.getChunk(bx shr 4, bz shr 4)
        val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz)
        return roomFor(level, bx, top, bz).toDouble()
    }

    /** Перше місце від [from] угору, де гравцеві вистачає двох клітин. */
    private fun roomFor(level: ServerLevel, x: Int, from: Int, z: Int): Int {
        val cursor = BlockPos.MutableBlockPos()
        val ceiling = level.maxY - 2
        var y = from.coerceIn(level.minY + 1, ceiling)
        while (y < ceiling) {
            val free = level.getBlockState(cursor.set(x, y, z)).isAir &&
                level.getBlockState(cursor.set(x, y + 1, z)).isAir
            if (free) return y
            y++
        }
        return from
    }

    /**
     * Гравець, що вийшов із гри посеред подорожі, інакше лишився б у білому назавжди:
     * сесія гасне разом із його з'єднанням, і повертати його вже нікому.
     */
    fun guardStranded() {
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            val player = handler.player
            if (holds(player)) leave(player, server.overworld())
        }
    }

    private fun place(player: ServerPlayer, into: ServerLevel) {
        val landing = surface(into, player.x, player.z)
        player.teleportTo(into, player.x, landing, player.z, emptySet(), player.yRot, player.xRot, true)
        player.resetFallDistance()
    }

    /**
     * Чий режим повернути, коли гравець вийде.
     *
     * Живе рівно в пам'яті сервера: подорож коротша за нього, а гравця, який вийшов
     * із гри посеред неї, повертає [guardStranded] — уже режимом сервера.
     */
    private val modes = HashMap<UUID, GameType>()

    /** Скільки поділок швидкості дає коліщатко: рівно стільки, скільки слотів у поясі. */
    private const val NOTCHES = 9
    private const val DEFAULT_NOTCH = 2
    private const val SLOWEST_FLY_SPEED = 0.05f
    private const val FLY_SPEED_STEP = 0.075f
    private const val VANILLA_FLY_SPEED = 0.05f
}
