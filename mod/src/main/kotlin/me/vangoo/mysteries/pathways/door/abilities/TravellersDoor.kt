package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.SessionScope
import me.vangoo.mysteries.ability.context.Messaging
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.ability.runtime.LiveTargeting
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import me.vangoo.mysteries.schedule.TaskHandle
import me.vangoo.mysteries.world.SpiritWorld
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.floor

/**
 * Door, Посл. 5 (Мандрівник): **Двері Мандрівника**.
 *
 * Головна сила шляху, і в ній два різні дієслова, розділені присіданням:
 *
 * | як | що | ціна |
 * |---|---|---|
 * | звичайний каст | двері в **духовний світ**: біла рівнина без стін і без відстані | каст + утримання |
 * | присівши, з компасом лодестона | двері **в точку**: миттєвий перехід до лодестона | каст |
 *
 * ## Двері — це двері, а не кнопка
 *
 * Каст нічого не переносить. Він **ставить двері** — вони стоять
 * [DOOR_TICKS] / [TICKS_PER_SECOND] с, і поки стоять, не сталось нічого: [AbilityOutcome.Awaiting]
 * тримає квиток непогашеним, тож Мандрівник, який передумав, не заплатив ані духовності,
 * ані перепочинку. Подорож починається в мить, коли він **увійшов**, — і рівно тоді
 * гаситься квиток.
 *
 * Перша редакція вмикала режим спостерігача просто в `cast`, і двері були декорацією
 * навколо телепорту. Тепер вони — сам перехід.
 *
 * ## Загін набирається поглядом, а не меню
 *
 * Поки двері стоять, погляд на гравця дає підказку, а присідання бере його з собою
 * ([MARK_RANGE] бл. на погляд, [FELLOW_RADIUS] бл. на мить входу). Ті, кого взяли,
 * переходять разом із Мандрівником і повертаються разом із ним — своїх дверей у них
 * немає, вони пасажири.
 *
 * ## Духовний світ — місце, а не режим
 *
 * За дверима лежить [SpiritWorld]: біле, беззвучне дзеркало реального світу з тією
 * самою сіткою координат. Гравець іде по ньому сам — без стін, без гір, без води, —
 * і вийде саме там, де зупиниться.
 *
 * Вихід теж двері: удар рукою ставить їх перед собою, і **за ними стоїть сам реальний
 * світ** — його блоки, перенесені в біле в тих самих координатах (див. [carve]).
 * Мандрівник бачить, чи виходить він у поле, в океан чи в чиюсь вітальню, **до** того,
 * як вийде.
 *
 * Час у білому обмежений ([TRAVEL_TICKS] / [TICKS_PER_SECOND] с) і коштує
 * [UPKEEP_PER_SECOND] духовності за секунду. Скінчиться час, духовність, зв'язок або
 * сам сервер — `finally` виведе назад усіх, і це той самий один шлях повернення, який
 * у плагіні збирався з чотирьох статичних мап.
 */
class TravellersDoor : ActiveAbility(AbilityIdentity.of("door_travellers_door")) {

    override val displayName: Component = Component.literal("Двері Мандрівника")

    override val spiritualityCost: Int = 300

    override fun periodicCost(sequence: Sequence): Int = UPKEEP_PER_SECOND

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override val icon: ItemStack get() = ItemStack(Items.ENDER_EYE)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Двері, які ведуть або в духовний світ, або в точку.\n" +
            "▪ Каст: двері стоять ${DOOR_TICKS / TICKS_PER_SECOND} с — увійдіть, щоб вирушити\n" +
            "▪ Погляд на гравця + присідання: взяти його з собою\n" +
            "▪ У білому: ${TRAVEL_TICKS / TICKS_PER_SECOND} с, $UPKEEP_PER_SECOND духовності/с; " +
            "удар рукою відчиняє двері назад\n" +
            "▪ Присівши з компасом лодестона: перехід просто до нього\n" +
            "▪ Перепочинок $COOLDOWN_SECONDS с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        // Повторний каст — це «зачинити», а не «відчинити другі». Платити за відмову
        // від власної подорожі нема за що, тож це Failed, а не Done.
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.failed("Ви зачиняєте двері")
        }
        if (SpiritWorld.holds(context.caster)) {
            return AbilityOutcome.failed("Тут двері не відчиняються — ви вже поза світом")
        }
        return if (context.caster.isShiftKeyDown) leap(context) else open(context)
    }

    // ── Двері в точку ───────────────────────────────────────────────────────────

    private fun leap(context: CastContext): AbilityOutcome {
        val tracker = context.caster.mainHandItem.get(DataComponents.LODESTONE_TRACKER)
            ?: return AbilityOutcome.failed("Присідання відкриває двері до лодестона — візьміть його компас")
        val target = tracker.target().orElse(null)
            ?: return AbilityOutcome.failed("Компас нічого не пам'ятає")
        if (target.dimension() != context.level.dimension()) {
            return AbilityOutcome.failed("Лодестон в іншому вимірі — туди двері не ведуть")
        }

        val to = Vec3(target.pos().x + 0.5, target.pos().y + 1.0, target.pos().z + 0.5)
        val forward = horizontal(context.look)
        doorway(
            context.scheduling, context.level, context.origin.add(forward.scale(STEP_OUT)),
            forward, LEAP_DOOR_TICKS, context.effects.pathwayColor,
        )
        context.caster.teleportTo(to.x, to.y, to.z)
        doorway(
            context.scheduling, context.level, to.add(forward.scale(STEP_OUT)),
            forward, LEAP_DOOR_TICKS, context.effects.pathwayColor,
        )
        context.level.playSound(
            null, to.x, to.y, to.z,
            SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 1.0f, 1.2f,
        )
        return AbilityOutcome.done("Двері вивели вас до лодестона")
    }

    // ── Двері в духовний світ ───────────────────────────────────────────────────

    private fun open(context: CastContext): AbilityOutcome {
        val server = context.level.server
        val spirit = SpiritWorld.of(server)
            ?: return AbilityOutcome.failed("Духовний світ не відгукується")

        val forward = horizontal(context.look)
        val footing = context.origin.add(forward.scale(STEP_OUT))
        val home = context.level.dimension()
        val color = context.effects.pathwayColor
        val scheduling = context.scheduling
        val messaging = context.messaging
        // Єдине, що здібності дозволено пережити виклик `cast`. Гаситься в мить входу.
        val ticket = context.cast

        doorway(scheduling, context.level, footing, forward, DOOR_TICKS, color)
        context.level.playSound(
            null, footing.x, footing.y, footing.z,
            SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.8f, 1.5f,
        )

        context.session {
            val fellowship = LinkedHashSet<UUID>()
            val party = LinkedHashSet<UUID>()
            try {
                if (!awaitEntry(footing, fellowship, messaging)) {
                    messaging.actionBar(ownerId, Component.literal("Двері зачинились — ніхто не увійшов"))
                    return@session
                }
                ticket.complete()

                party += gather(server, fellowship)
                party.forEach { id ->
                    val traveller = server.playerList.getPlayer(id) ?: return@forEach
                    SpiritWorld.enter(traveller, spirit)
                    messaging.actionBar(
                        traveller,
                        Component.literal("Удар рукою відчиняє двері назад; коліщатко змінює швидкість"),
                    )
                }
                wander(server, spirit, home, party, scheduling, messaging, color)
            } finally {
                val back = server.getLevel(home) ?: server.overworld()
                party.forEach { id ->
                    val traveller = server.playerList.getPlayer(id) ?: return@forEach
                    if (SpiritWorld.holds(traveller)) SpiritWorld.leave(traveller, back)
                }
            }
        }

        return AbilityOutcome.Awaiting(
            Component.literal("Двері стоять ${DOOR_TICKS / TICKS_PER_SECOND} с — увійдіть, щоб вирушити"),
        )
    }

    /** Поки двері стоять: набір загону поглядом і чекання, доки Мандрівник переступить поріг. */
    private suspend fun SessionScope.awaitEntry(
        footing: Vec3,
        fellowship: MutableSet<UUID>,
        messaging: Messaging,
    ): Boolean {
        val deadline = ticksAlive + DOOR_TICKS
        val threshold = threshold(footing)
        while (ticksAlive < deadline) {
            val walker = owner ?: return false
            if (walker.boundingBox.intersects(threshold)) return true
            mark(walker, fellowship, messaging)
            waitTicks(WATCH_PERIOD_TICKS)
        }
        return false
    }

    /**
     * Погляд питає, присідання відповідає.
     *
     * Окремої клавіші тут немає навмисно: присідання сервер бачить сам
     * (`isShiftKeyDown`), тож набір загону не коштує ні пакета, ні keybind'у.
     */
    private fun mark(walker: ServerPlayer, fellowship: MutableSet<UUID>, messaging: Messaging) {
        val seen = LiveTargeting(walker).playerLookingAt(MARK_RANGE) ?: return
        if (!walker.isShiftKeyDown) {
            if (seen.uuid !in fellowship) {
                messaging.actionBar(
                    walker,
                    Component.literal("Присядьте, щоб узяти ${seen.name.string} із собою"),
                )
            }
            return
        }
        if (!fellowship.add(seen.uuid)) return
        messaging.actionBar(walker, Component.literal("${seen.name.string} піде з вами"))
        messaging.tell(
            seen,
            Component.literal("Мандрівник бере вас у духовний світ — тримайтесь ближче за ${FELLOW_RADIUS.toInt()} бл."),
        )
        seen.level().playSound(
            null, seen.x, seen.y, seen.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.4f,
        )
    }

    /** Хто справді пішов: сам Мандрівник і ті з покликаних, хто в мить входу був поруч. */
    private fun SessionScope.gather(server: MinecraftServer, fellowship: Set<UUID>): Set<UUID> {
        val walker = owner ?: return emptySet()
        val party = LinkedHashSet<UUID>()
        party += walker.uuid
        fellowship.forEach { id ->
            val friend = server.playerList.getPlayer(id) ?: return@forEach
            if (friend.level() === walker.level() && friend.distanceToSqr(walker) <= FELLOW_RADIUS * FELLOW_RADIUS) {
                party += id
            }
        }
        return party
    }

    /** Подорож білим: доки не скінчиться час, духовність — або доки не знайдуться двері назад. */
    private suspend fun SessionScope.wander(
        server: MinecraftServer,
        spirit: ServerLevel,
        home: ResourceKey<Level>,
        party: Set<UUID>,
        scheduling: Scheduling,
        messaging: Messaging,
        color: RgbColor,
    ) {
        val back = server.getLevel(home) ?: server.overworld()
        val deadline = ticksAlive + TRAVEL_TICKS
        var exit: Exit? = null
        var exitCloses = 0L
        var sinceUpkeep = 0

        try {
            while (ticksAlive < deadline) {
                val walker = owner ?: return
                if (!SpiritWorld.holds(walker)) return
                // Місцевість проступає **навколо** дверей, тобто й там, де стоїть сам
                // загін: колонка сусіда може виявитись вищою за ту, у якій відчинено
                // двері, і блок народжується просто в мандрівникові. У пригодницькому
                // режимі це задуха, тож кожного, кого замурувало, виштовхує вгору.
                party.forEach { id ->
                    val traveller = server.playerList.getPlayer(id) ?: return@forEach
                    if (SpiritWorld.holds(traveller) && traveller.isInWall) {
                        traveller.teleportTo(traveller.x, traveller.y + 1.0, traveller.z)
                    }
                }
                // Швидкість польоту — активний слот пояса: коліщатко в білому нічого
                // не перемикає, зате сервер бачить його без жодного пакета від мода.
                // Кожен із загону крутить своє.
                party.forEach { id ->
                    val traveller = server.playerList.getPlayer(id) ?: return@forEach
                    if (SpiritWorld.holds(traveller)) {
                        SpiritWorld.drift(traveller, traveller.inventory.selectedSlot)
                    }
                }
                spirit.sendParticles(
                    ModParticles.mote(color),
                    walker.x, walker.y + 1.0, walker.z,
                    2, 0.35, 0.6, 0.35, 0.0,
                )

                val open = exit
                if (open == null) {
                    // Удар рукою, а не присідання: присідання в білому означало б, що
                    // Мандрівник не може ні красти, ні ховатись, не відчинивши дверей.
                    // Замах сервер бачить сам (`swinging` тримається кілька тіків після
                    // пакета), тож своя клавіша тут не потрібна — а мобів, у яких можна
                    // влучити випадково, у духовному світі немає.
                    if (walker.swinging) {
                        exit = openExit(scheduling, spirit, back, walker, color)
                        exitCloses = ticksAlive + EXIT_TICKS
                        messaging.actionBar(
                            walker,
                            Component.literal("Двері назад відчинено — за ними ваше місце виходу"),
                        )
                    }
                } else if (ticksAlive >= exitCloses) {
                    open.close()
                    exit = null
                } else if (walker.boundingBox.intersects(open.threshold)) {
                    return
                }

                sinceUpkeep += WATCH_PERIOD_TICKS
                if (sinceUpkeep >= TICKS_PER_SECOND) {
                    sinceUpkeep -= TICKS_PER_SECOND
                    if (!payUpkeep()) {
                        messaging.actionBar(ownerId, Component.literal("Духовності не стало — біле виштовхує вас"))
                        return
                    }
                }
                waitTicks(WATCH_PERIOD_TICKS)
            }
            messaging.actionBar(ownerId, Component.literal("Час у білому вийшов"))
        } finally {
            // Проріз — це справжні блоки в справжньому світі. Не засипати його назад
            // означає лишити дірку в білому після себе, і це єдина річ у цій здібності,
            // яка переживає сервер.
            exit?.close()
        }
    }

    /** Відчинені двері назад: поріг, у який треба ступити, і проріз, який треба засипати. */
    private class Exit(val threshold: AABB, val close: () -> Unit)

    private fun openExit(
        scheduling: Scheduling,
        spirit: ServerLevel,
        home: ServerLevel,
        walker: ServerPlayer,
        color: RgbColor,
    ): Exit {
        val forward = horizontal(walker.getViewVector(1.0f))
        val footing = walker.position().add(forward.scale(STEP_OUT))
        doorway(scheduling, spirit, footing, forward, EXIT_TICKS, color)
        spirit.playSound(
            null, footing.x, footing.y, footing.z,
            SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.8f, 0.9f,
        )
        return Exit(threshold(footing), carve(scheduling, spirit, home, footing))
    }

    /**
     * **Проріз у білому**: за дверима стоїть не голограма, а справжній шматок реального
     * світу — його блоки, вставлені в духовний світ у **тих самих координатах**.
     *
     * ## Чому не «портал»
     *
     * Справжній портальний рендер — це другий світ у пам'яті клієнта, стрімінг його
     * чанків і другий прохід камери з відсіканням по площині дверей: підсистема
     * розміром з окремий мод. Перша редакція намагалась обійтись частинками
     * (`BLOCK_MARKER` — текстура блока на клітину), і виглядало це рівно так, як і є:
     * три голограми в рамі.
     *
     * Тут інша відповідь на те саме питання. Клієнт **уже** вміє малювати блоки того
     * світу, у якому стоїть, — тож блоки туди й приносяться. Проріз має справжню
     * геометрію, справжні текстури, справжню глибину й паралакс: голову вбік — і видно
     * за косяк. Ціна — те, що це **копія**, знята в мить відчинення: без сутностей і без
     * чужих змін.
     *
     * ## Коло, а не конус
     *
     * Перша редакція копіювала конус видимості крізь раму — те, що видно з одного місця
     * й в один бік. Але Мандрівникові треба не зазирнути в шпарину, а **впізнати
     * місцевість**: куди він виходить, що поруч, чи це взагалі те місце. Тому копіюється
     * коло радіусом [CARVE_RADIUS] навколо дверей — і воно проступає від дверей назовні,
     * колонка за колонкою.
     *
     * Висоти беруть за землю реального світу: рівень [SpiritWorld.surface] лягає на
     * поріг, тож те, що там стоїть на землі, тут стоїть на порозі.
     *
     * Повертає те, чим проріз засипають назад. Викликати **обов'язково** — це єдине,
     * що ця здібність лишає у світі після себе.
     */
    private fun carve(
        scheduling: Scheduling,
        spirit: ServerLevel,
        home: ServerLevel,
        footing: Vec3,
    ): () -> Unit {
        // ponytail: читання чужого світу вантажить (і за потреби генерує) його чанки
        // синхронно. Одна пауза на одні двері — і вона все одно сталася б за мить,
        // коли гравець крізь них вийде.
        val ground = SpiritWorld.surface(home, footing.x, footing.z).toInt()
        val base = BlockPos(floor(footing.x).toInt(), floor(footing.y).toInt(), floor(footing.z).toInt())
        val saved = HashMap<BlockPos, BlockState>()

        // Колонки за зростанням відстані: місцевість проступає від дверей назовні,
        // а не лягає готовою плитою.
        val columns = buildList {
            for (dx in -CARVE_RADIUS..CARVE_RADIUS) {
                for (dz in -CARVE_RADIUS..CARVE_RADIUS) {
                    if (dx * dx + dz * dz <= CARVE_RADIUS * CARVE_RADIUS) add(dx to dz)
                }
            }
        }.sortedBy { (dx, dz) -> dx * dx + dz * dz }

        var next = 0
        lateinit var handle: TaskHandle
        // Колонками по тіках, а не одним махом: коло на 50 бл. — це сім на сім чанків
        // реального світу, і згенерувати їх усі в одному тіку означає секунди
        // застиглого сервера.
        handle = scheduling.every(1, delayTicks = 0) {
            if (next >= columns.size) return@every handle.cancel()
            val until = minOf(next + COLUMNS_PER_TICK, columns.size)
            while (next < until) {
                val (dx, dz) = columns[next++]
                column(spirit, home, base, ground, dx, dz, saved)
            }
        }

        return {
            handle.cancel()
            saved.forEach { (spot, state) -> spirit.setBlock(spot, state, CARVE_FLAGS) }
        }
    }

    /**
     * Одна колонка місцевості: шкіра реального рельєфу плюс проріз у білому над нею.
     *
     * Копіюється не вся товща, а **шкіра** — від [SKIN_BELOW] блоків під ґрунтом до
     * верхівки того, що в цій колонці стоїть (`WORLD_SURFACE` бере й дерево, і дах).
     * Товщу копіювати нема сенсу: крізь власну поверхню її все одно не видно, а коштує
     * вона стільки ж, скільки все інше разом.
     *
     * Западину рельєфу білa плита накрила б згори, тож над шкірою прорізається стеля —
     * до [CLEAR_CAP] блоків, доки не почнеться повітря.
     */
    private fun column(
        spirit: ServerLevel,
        home: ServerLevel,
        base: BlockPos,
        ground: Int,
        dx: Int,
        dz: Int,
        saved: MutableMap<BlockPos, BlockState>,
    ) {
        val x = base.x + dx
        val z = base.z + dz
        // Той самий чанк-перед-heightmap, що й у SpiritWorld.surface: незавантажений
        // чанк віддає sea_level + 1, і вся колонка лягла б не туди.
        home.getChunk(x shr 4, z shr 4)
        val top = home.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)
        val solid = home.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)

        for (ry in (solid - SKIN_BELOW) until top) {
            val state = home.getBlockState(BlockPos(x, ry, z))
            // Скриня без своєї сутності — блок, який ламає все, що по ній ходить.
            if (state.hasBlockEntity()) continue
            put(spirit, BlockPos(x, base.y + (ry - ground), z), state, saved)
        }

        var lid = base.y + (top - ground)
        val ceiling = lid + CLEAR_CAP
        while (lid < ceiling) {
            val spot = BlockPos(x, lid, z)
            if (spirit.getBlockState(spot).isAir) break
            put(spirit, spot, Blocks.AIR.defaultBlockState(), saved)
            lid++
        }
    }

    /** Кладе блок, запам'ятавши, що тут було до нього. Однакове не чіпає зовсім. */
    private fun put(
        spirit: ServerLevel,
        spot: BlockPos,
        state: BlockState,
        saved: MutableMap<BlockPos, BlockState>,
    ) {
        val was = spirit.getBlockState(spot)
        // Повітря над білою поверхнею вже повітря — а це більшість кола.
        if (was == state) return
        if (spot !in saved) saved[spot] = was
        spirit.setBlock(spot, state, CARVE_FLAGS)
    }

    /** Поріг: об'єм, у який треба ступити, щоб двері спрацювали. */
    private fun threshold(footing: Vec3): AABB =
        AABB.ofSize(
            footing.add(0.0, THRESHOLD_HEIGHT / 2.0, 0.0),
            THRESHOLD_WIDTH, THRESHOLD_HEIGHT, THRESHOLD_WIDTH,
        )

    private companion object {
        const val COOLDOWN_SECONDS = 50
        const val UPKEEP_PER_SECOND = 8

        /** Скільки двері чекають на того, хто в них увійде. */
        const val DOOR_TICKS = 10 * TICKS_PER_SECOND
        const val TRAVEL_TICKS = 60 * TICKS_PER_SECOND
        /** Стільки стоять двері назад: коло проступає ~1,5 с, решта — щоб роздивитись. */
        const val EXIT_TICKS = 15 * TICKS_PER_SECOND
        const val LEAP_DOOR_TICKS = 3 * TICKS_PER_SECOND

        const val WATCH_PERIOD_TICKS = 4
        const val MARK_RANGE = 12.0
        const val FELLOW_RADIUS = 10.0

        /** Крок уперед: двері мають стояти перед Мандрівником, а не на ньому. */
        const val STEP_OUT = 1.6
        const val THRESHOLD_WIDTH = 1.3
        const val THRESHOLD_HEIGHT = 2.2

        /** Радіус кола місцевості навколо дверей — навсібіч, а не лише вперед. */
        const val CARVE_RADIUS = 50
        /** Скільки колон за тік: коло проступає за ~26 тіків. */
        const val COLUMNS_PER_TICK = 300
        /** Скільки ґрунту під поверхнею: рівно щоб край не читався як папір. */
        const val SKIN_BELOW = 3
        /** Наскільки прорізається біла стеля над западиною рельєфу. */
        const val CLEAR_CAP = 16

        /**
         * `UPDATE_CLIENTS or UPDATE_SKIP_ALL_SIDEEFFECTS` (2 + 816).
         *
         * Клієнт блок бачить, а світ його «появи» не помічає взагалі. Кожен доданок
         * тут закриває свою поломку, і найдорожча — `UPDATE_SKIP_ON_PLACE` (512):
         * без неї `LevelChunk.setBlockState` кличе `onPlace`, `LiquidBlock.onPlace`
         * планує тік розтікання, і перенесена вода тече в клітини, яких немає в
         * `saved`, — тобто лишається в білому **назавжди**.
         */
        const val CARVE_FLAGS = Block.UPDATE_CLIENTS or Block.UPDATE_SKIP_ALL_SIDEEFFECTS
    }
}
