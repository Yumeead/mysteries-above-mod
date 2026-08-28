package me.vangoo.mysteries.pathways.common

import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.rituals.RitualCatalog
import me.vangoo.domain.rituals.RitualEffectMath
import me.vangoo.domain.rituals.RitualRecipe
import me.vangoo.domain.rituals.RitualType
import me.vangoo.domain.rituals.SacrificeAppraiser
import me.vangoo.domain.rituals.SacrificeKind
import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.Beyonders
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import net.minecraft.world.level.block.AbstractCandleBlock
import net.minecraft.world.level.block.CandleBlock
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * Спільна здібність Посл. 9: **Ритуальна магія**.
 *
 * Розставте навколо себе запалені свічки, покладіть на землю приношення, станьте в коло
 * й почніть обряд. Мод сам знайде, який саме обряд ви склали.
 *
 * ## Вівтар — це справжній вівтар, а не скриня з іконками
 *
 * У плагіні обряд починався з `context.ui().openChoiceMenu(...)`: гравець відкривав
 * шестирядкову скриню, тицяв іконку потрібного ритуалу, скриня зачинялась, і далі мод
 * перевіряв, чи є в **інвентарі** потрібні предмети. Свічки в тому коді теж були — але
 * лише як число, яке треба набрати; сам вівтар нічого не вирішував.
 *
 * Тут вирішує саме він. Обряд не обирають зі списку — його **складають**: скільки свічок
 * горить, що лежить на землі, що затиснуто в руці. З того, що вийшло, мод бере
 * найбагатший обряд, який цей вівтар витягує. Меню зникло не тому, що його нема чим
 * намалювати, а тому що воно було замість механіки.
 *
 * Три наслідки, жоден з яких не коштував рядка коду:
 *
 * - **Вівтар видно.** Чужий обряд помітно здалеку — свічки горять, приношення лежить.
 * - **Приношення можна вкрасти.** Воно лежить на землі, а не в захищеному інвентарі.
 * - **Вівтар — це місце.** Його будують, до нього повертаються, його руйнують.
 *
 * ## Свічки рахуються по свічках, а не по блоках
 *
 * Ванільний `CandleBlock` тримає від однієї до чотирьох свічок в одному блоці, тож
 * `CandleBlock.CANDLES` — це і є число, яке просить рецепт. Один блок із чотирма
 * свічками закриває обряд третього рівня; чотири окремі — теж. Форма вівтаря лишається
 * за гравцем.
 */
class RitualMagic : ActiveAbility(AbilityIdentity.of("common_ritual_magic")) {

    override val displayName: Component = Component.literal("Ритуальна магія")

    override val spiritualityCost: Int = 50

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Знання обрядів: вівтар зі свічок, приношення на землі й прохання до тих, хто відповідає.\n" +
            "▪ Станьте посеред запалених свічок ($MIN_CANDLES+), покладіть приношення на землю\n" +
            "▪ Обряд визначає сам вівтар — найбагатший із тих, що він витягує\n" +
            "▪ Доступно обрядів: ${available(sequence).size} із ${RitualCatalog.ALL.size}\n" +
            "▪ Обряд триває ${RITE_TICKS / TICKS_PER_SECOND} с; відійдете — згасне",
    )

    /** Другий обряд поверх першого — це не обряд. Сесія в гравця одна. */
    override fun canCast(context: CastContext): Boolean = !context.sessionRunning

    override fun cast(context: CastContext): AbilityOutcome {
        val level = context.level
        val centre = context.caster.blockPosition()

        val candles = countCandles(level, centre)
        if (candles < MIN_CANDLES) {
            return AbilityOutcome.failed(
                "Вівтар не складено: навколо вас має горіти щонайменше $MIN_CANDLES свічки (зараз $candles)",
            )
        }

        val offerings = offerings(level, centre)
        val hand = context.caster.getItemInHand(InteractionHand.MAIN_HAND)
        val recipe = choose(context.sequence, candles, offerings, hand)
            ?: return AbilityOutcome.failed(missingHint(context.sequence, candles))

        // Усе, що обряду знадобиться після цього рядка, знімається зараз: контекст живе
        // один виклик, а `ServerPlayer` — до найближчої смерті. Далі сесія бере гравця
        // з `owner` щотакту, а решту — звідси.
        val rite = Rite(
            recipe = recipe,
            sequence = context.sequence,
            anchor = Vec3.atCenterOf(centre),
            mote = ModParticles.mote(context.effects.pathwayColor),
            candles = candles,
            sacrifice = hand.copy(),
            offerings = offerings,
            scheduling = context.scheduling,
            beyonders = context.beyonders,
        )
        val pending = context.cast

        context.session {
            var settled = false
            try {
                repeat(RITE_TICKS / RITE_PERIOD_TICKS) { step ->
                    val player = owner ?: return@session
                    if (player.position().distanceToSqr(rite.anchor) > STRAY_RANGE * STRAY_RANGE) {
                        settled = true
                        stray(player, rite)
                        return@session
                    }
                    burn(player.level() as ServerLevel, rite, step)
                    waitTicks(RITE_PERIOD_TICKS)
                }

                val player = owner ?: return@session
                val self = beyonder ?: return@session
                consume(player, rite)
                pending.complete()
                settled = true
                perform(player, self, rite)
            } finally {
                // Скасування (перекаст, вихід, зупинка сервера) теж гасить обряд —
                // і квиток лишається непогашеним, тобто безкоштовним.
                if (!settled) owner?.sendSystemMessage(Component.literal("Обряд згас"), true)
            }
        }

        level.playSound(
            null, rite.anchor.x, rite.anchor.y, rite.anchor.z,
            SoundEvents.CANDLE_AMBIENT, SoundSource.PLAYERS, 1.2f, 0.6f,
        )
        return AbilityOutcome.Awaiting(Component.literal("Обряд почався: ${recipe.displayName}"))
    }

    // ── Вівтар ──────────────────────────────────────────────────────────────────

    /** Скільки свічок горить навколо. Ванільний блок тримає до чотирьох — рахуємо їх. */
    private fun countCandles(level: ServerLevel, centre: BlockPos): Int =
        BlockPos.betweenClosed(
            centre.offset(-ALTAR_RADIUS, -1, -ALTAR_RADIUS),
            centre.offset(ALTAR_RADIUS, 2, ALTAR_RADIUS),
        ).sumOf { pos ->
            val state = level.getBlockState(pos)
            when {
                state.block !is AbstractCandleBlock -> 0
                !AbstractCandleBlock.isLit(state) -> 0
                state.hasProperty(CandleBlock.CANDLES) -> state.getValue(CandleBlock.CANDLES)
                else -> 1
            }
        }

    /** Приношення — те, що **лежить на землі** у колі, а не те, що сховано в інвентарі. */
    private fun offerings(level: ServerLevel, centre: BlockPos): List<ItemEntity> =
        level.getEntitiesOfClass(
            ItemEntity::class.java,
            AABB.ofSize(Vec3.atCenterOf(centre), ALTAR_SPAN, ALTAR_HEIGHT, ALTAR_SPAN),
        )

    /**
     * Найбагатший обряд, який цей вівтар витягує.
     *
     * Сортування за числом свічок і довжиною списку інгредієнтів, а не за порядком у
     * каталозі: гравець, що склав вівтар на п'ять свічок з аметистом, просив явно не
     * молитву удачі. Дешеві обряди лишаються доступними — досить не класти зайвого.
     */
    private fun choose(
        sequence: Sequence,
        candles: Int,
        offerings: List<ItemEntity>,
        hand: ItemStack,
    ): RitualRecipe? = available(sequence)
        .filter { it.candlesRequired <= candles }
        .filter { !it.requiresHandSacrifice || !hand.isEmpty }
        .filter { covered(it, offerings) }
        .maxByOrNull { it.candlesRequired * 10 + it.ingredients.size }

    /**
     * Обряди, які Потойбічний уже здатен провести.
     *
     * ponytail: `BESTOWMENT` виключено — його відповідь це підказка, де шукати інгредієнт,
     * а індекс джерел (`IngredientSourceIndex`) живиться означеннями істот і фореджу,
     * тобто фазою 12 та її конфігом. Повернути — прибрати один рядок звідси, коли індекс
     * буде чим наповнити.
     */
    private fun available(sequence: Sequence): List<RitualRecipe> =
        RitualCatalog.availableFor(sequence.level).filter { it.type != RitualType.BESTOWMENT }

    private fun covered(recipe: RitualRecipe, offerings: List<ItemEntity>): Boolean =
        recipe.ingredients.all { (id, needed) ->
            offerings.filter { matches(it.item, id) }.sumOf { it.item.count } >= needed
        }

    /**
     * Імена інгредієнтів приїхали з конфіга плагіна у вигляді `GOLD_INGOT` — це
     * `org.bukkit.Material`, і збігається воно з ванільним ідентифікатором один в один,
     * бо Bukkit будував свій перелік із нього ж. Тому перекладу немає, є регістр.
     */
    private fun matches(stack: ItemStack, id: String): Boolean {
        val key = Identifier.tryParse(id.lowercase()) ?: return false
        return BuiltInRegistries.ITEM.getValue(key) === stack.item
    }

    /** Відмова мусить навчати: вона перелічує, що саме цей вівтар уже прийняв би. */
    private fun missingHint(sequence: Sequence, candles: Int): String {
        val reachable = available(sequence).filter { it.candlesRequired <= candles }
        if (reachable.isEmpty()) {
            return "Свічок ($candles) замало для будь-якого відомого вам обряду"
        }
        return "Приношення не збігається з жодним обрядом. Вівтар на $candles свічк(и) приймає: " +
            reachable.joinToString("; ") { recipe ->
                val what = recipe.ingredients.entries
                    .joinToString(", ") { "${it.key} ×${it.value}" }
                    .ifBlank { "предмет у руці" }
                "${recipe.displayName} ($what)"
            }
    }

    // ── Обряд ───────────────────────────────────────────────────────────────────

    /** Полум'я тягнеться до центру: обряд видно тим, хто поруч, а не лише тому, хто веде. */
    private fun burn(level: ServerLevel, rite: Rite, step: Int) {
        val arms = rite.candles.coerceAtMost(MAX_ARMS)
        val progress = (step % ARM_STEPS).toDouble() / ARM_STEPS
        repeat(arms) { arm ->
            val angle = TAU * arm / arms + step * SWIRL
            val radius = ALTAR_RADIUS * (1.0 - progress)
            level.sendParticles(
                rite.mote,
                rite.anchor.x + radius * cos(angle),
                rite.anchor.y + 0.4 + progress * 1.2,
                rite.anchor.z + radius * sin(angle),
                1, 0.0, 0.0, 0.0, 0.0,
            )
        }
    }

    private fun stray(player: ServerPlayer, rite: Rite) {
        player.sendSystemMessage(Component.literal("Ви вийшли з кола — обряд обірвався"), true)
        // «Зірваний ритуал накличе невдачу» — обіцянка з каталогу, і вона мусить справдитись.
        if (rite.recipe.type == RitualType.LUCK_PRAYER) {
            player.addEffect(MobEffectInstance(MobEffects.UNLUCK, BACKLASH_TICKS, 0, false, true, true))
        }
        (player.level() as ServerLevel).playSound(
            null, player.x, player.y, player.z,
            SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.7f,
        )
    }

    /** Приношення згорає рівно в тому обсязі, який просив рецепт: решта лишається лежати. */
    private fun consume(player: ServerPlayer, rite: Rite) {
        rite.recipe.ingredients.forEach { (id, needed) ->
            var left = needed
            rite.offerings.filter { it.isAlive && matches(it.item, id) }.forEach { entity ->
                if (left <= 0) return@forEach
                val taken = minOf(left, entity.item.count)
                left -= taken
                entity.item.shrink(taken)
                if (entity.item.isEmpty) entity.discard()
            }
        }
        if (rite.recipe.requiresHandSacrifice) {
            player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1)
        }
    }

    private fun perform(player: ServerPlayer, self: Beyonder, rite: Rite) {
        val level = player.level() as ServerLevel
        val at = rite.anchor
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.8f)
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 30, 0.6, 0.8, 0.6, 0.05)

        when (rite.recipe.type) {
            RitualType.LUCK_PRAYER -> pray(player, rite)
            RitualType.SANCTIFICATION -> sanctify(player, rite)
            RitualType.SACRIFICE -> burnOffering(player, self, rite)
            RitualType.MEDIUMSHIP -> reveal(player, level, rite)
            RitualType.MIRROR_DIVINATION -> divine(player, rite)
            RitualType.SPIRIT_WALL -> wall(player, level, rite)
            RitualType.BESTOWMENT -> Unit
        }
    }

    private fun pray(player: ServerPlayer, rite: Rite) {
        val ticks = rite.scaled(RitualEffectMath.LUCK_BASE_TICKS, SequenceScaler.ScalingStrategy.WEAK)
        player.addEffect(MobEffectInstance(MobEffects.LUCK, ticks, 0, false, true, true))
        player.sendSystemMessage(Component.literal("Доля прихильна: ${ticks / TICKS_PER_SECOND} с"), true)
    }

    private fun sanctify(player: ServerPlayer, rite: Rite) {
        val stack = player.getItemInHand(InteractionHand.MAIN_HAND)
        if (!stack.isDamageableItem || stack.damageValue == 0) {
            player.sendSystemMessage(Component.literal("Освячувати нічого: предмет у руці цілий"), true)
            return
        }
        val repair = rite.scaled(RitualEffectMath.SANCTIFY_BASE_DURABILITY, SequenceScaler.ScalingStrategy.MODERATE)
        stack.damageValue = (stack.damageValue - repair).coerceAtLeast(0)
        player.sendSystemMessage(Component.literal("Предмет освячено: +$repair міцності"), true)
    }

    /** Предмет уже згорів у [consume] — оцінюється копія, знята в мить касту. */
    private fun burnOffering(player: ServerPlayer, self: Beyonder, rite: Rite) {
        val gain = SacrificeAppraiser.spiritualityFor(sacrificeKind(rite.sacrifice))
        self.setSpirituality(self.spirituality.increment(gain))
        player.sendSystemMessage(Component.literal("Жертву прийнято: +$gain духовності"), true)
    }

    /**
     * Духи показують, хто поруч, — крізь стіни й на пів хвилини.
     *
     * Каталог обіцяє «розповідь про минулі події», але минулого мод не пише і не почне:
     * журнал змін світу заради однієї здібності — саме те, від чого відмовилась
     * `CrimeReconstruction`. Те, що духи знають без журналу, — хто **зараз** тут.
     */
    private fun reveal(player: ServerPlayer, level: ServerLevel, rite: Rite) {
        val ticks = rite.scaled(RitualEffectMath.WALL_BASE_TICKS, SequenceScaler.ScalingStrategy.MODERATE)
        val box = AABB.ofSize(player.position(), REVEAL_SPAN, REVEAL_SPAN, REVEAL_SPAN)
        val seen: List<UUID> = level.getEntitiesOfClass(LivingEntity::class.java, box)
            .filter { it !== player }
            .onEach { it.setGlowingTag(true) }
            .map { it.uuid }

        // Гасити доводиться за ідентифікаторами: за пів хвилини половина показаних може
        // померти, а мертвій сутності контур уже не знімеш.
        rite.scheduling.after(ticks) {
            seen.forEach { id -> (level.getEntityInAnyDimension(id) as? LivingEntity)?.setGlowingTag(false) }
        }
        player.sendSystemMessage(
            Component.literal("Духи вказують: ${seen.size} присутніх на ${ticks / TICKS_PER_SECOND} с"),
            true,
        )
    }

    /** Дзеркало показує не місце, а людей: хто в цьому світі носить силу і яку. */
    private fun divine(player: ServerPlayer, rite: Rite) {
        val others = rite.beyonders.online.filter { it.playerId != player.uuid }
        if (others.isEmpty()) {
            player.sendSystemMessage(Component.literal("Дзеркало порожнє: інших носіїв сили немає"), true)
            return
        }

        player.sendSystemMessage(Component.literal("Дзеркало показує тих, хто носить силу:"))
        others.forEach { other ->
            val who = player.level().server.playerList.getPlayer(other.playerId)
            val name = who?.name?.string ?: "невідомий"
            val where = when {
                who == null -> "поза світом"
                who.level() !== player.level() -> "в іншому світі"
                else -> "${player.position().distanceTo(who.position()).toInt()} бл."
            }
            player.sendSystemMessage(
                Component.literal(
                    "  $name — ${other.pathway.name} ${other.sequenceLevel} " +
                        "(${other.pathway.sequenceName(other.sequenceLevel)}) · $where",
                ),
            )
        }
    }

    /** Стіна духовності: коло навколо вівтаря тримає тих, хто в ньому, і його видно. */
    private fun wall(player: ServerPlayer, level: ServerLevel, rite: Rite) {
        val ticks = rite.scaled(RitualEffectMath.WALL_BASE_TICKS, SequenceScaler.ScalingStrategy.MODERATE)
        val at = rite.anchor
        var step = 0

        lateinit var handle: TaskHandle
        handle = rite.scheduling.every(WALL_PERIOD_TICKS) {
            if (step * WALL_PERIOD_TICKS >= ticks) return@every handle.cancel()
            repeat(WALL_POINTS) { i ->
                val angle = TAU * i / WALL_POINTS + step * WALL_SWIRL
                level.sendParticles(
                    rite.mote,
                    at.x + WALL_RADIUS * cos(angle),
                    at.y + 0.3 + (i % 3) * 0.4,
                    at.z + WALL_RADIUS * sin(angle),
                    1, 0.0, 0.0, 0.0, 0.0,
                )
            }
            level.getEntitiesOfClass(ServerPlayer::class.java, AABB.ofSize(at, WALL_SPAN, WALL_SPAN, WALL_SPAN))
                .filter { it.position().distanceToSqr(at) <= WALL_RADIUS * WALL_RADIUS }
                .forEach {
                    it.addEffect(MobEffectInstance(MobEffects.RESISTANCE, WALL_TOP_UP_TICKS, 1, true, false, true))
                }
            step++
        }
        player.sendSystemMessage(Component.literal("Стіна духовності стоїть ${ticks / TICKS_PER_SECOND} с"), true)
    }

    /** Ванільна рідкісність — готова шкала цінності; свого переліку матеріалів мод не тримає. */
    private fun sacrificeKind(stack: ItemStack): SacrificeKind = when {
        stack.isEmpty -> SacrificeKind.TRIFLE
        stack.get(DataComponents.RARITY) == Rarity.EPIC -> SacrificeKind.PRECIOUS
        stack.get(DataComponents.RARITY) == Rarity.RARE -> SacrificeKind.VALUABLE
        stack.get(DataComponents.RARITY) == Rarity.UNCOMMON || stack.isEnchanted -> SacrificeKind.VALUABLE
        else -> SacrificeKind.TRIFLE
    }

    /**
     * Усе, що обряд забирає з контексту в мить касту.
     *
     * Контекст живе один виклик і несе особу кастера, тож у сесію він не їде — сюди
     * переїжджають лише значення та служби, які контекст переживають.
     */
    private inner class Rite(
        val recipe: RitualRecipe,
        val sequence: Sequence,
        val anchor: Vec3,
        val mote: ParticleOptions,
        val candles: Int,
        /** Копія предмета з руки: до розрахунку жертви оригінал уже згорить. */
        val sacrifice: ItemStack,
        val offerings: List<ItemEntity>,
        val scheduling: Scheduling,
        val beyonders: Beyonders,
    ) {
        fun scaled(base: Int, strategy: SequenceScaler.ScalingStrategy): Int = scale(base, sequence, strategy)
    }

    private companion object {
        const val COOLDOWN_SECONDS = 60
        const val MIN_CANDLES = 3
        const val ALTAR_RADIUS = 3
        const val ALTAR_SPAN = 7.0
        const val ALTAR_HEIGHT = 4.0

        const val RITE_TICKS = 8 * TICKS_PER_SECOND
        const val RITE_PERIOD_TICKS = 2
        const val STRAY_RANGE = 3.0
        /** 20 с невдачі за зірвану молитву. */
        const val BACKLASH_TICKS = 20 * TICKS_PER_SECOND

        const val MAX_ARMS = 8
        const val ARM_STEPS = 20
        const val SWIRL = 0.08

        const val REVEAL_SPAN = 64.0

        const val WALL_RADIUS = 5.0
        const val WALL_SPAN = 12.0
        const val WALL_POINTS = 18
        const val WALL_PERIOD_TICKS = 4
        const val WALL_SWIRL = 0.05
        const val WALL_TOP_UP_TICKS = 20

        const val TAU = 2.0 * Math.PI
    }
}
