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
import me.vangoo.mysteries.ability.context.Messaging
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
 * The shared Seq. 9 ability: **Ritual Magic**.
 *
 * Set lit candles around you, lay an offering on the ground, stand in the circle and begin the
 * rite. The mod works out which rite you have put together.
 *
 * ## The altar is a real altar, not a chest full of icons
 *
 * In the plugin a rite began with `context.ui().openChoiceMenu(...)`: the player opened a
 * six-row chest, clicked the icon of the ritual they wanted, the chest closed, and then the mod
 * checked whether the required items were in the **inventory**. Candles were in that code too —
 * but only as a number to reach; the altar itself decided nothing.
 *
 * Here it is the altar that decides. A rite is not picked from a list — it is **assembled**: how
 * many candles burn, what lies on the ground, what is held in hand. From what came out, the mod
 * takes the richest rite that altar supports. The menu disappeared not because there was nothing
 * to draw it with, but because it stood in place of a mechanic.
 *
 * Three consequences, none of which cost a line of code:
 *
 * - **The altar is visible.** Someone else's rite is noticeable from afar — the candles burn,
 *   the offering lies there.
 * - **The offering can be stolen.** It lies on the ground, not in a protected inventory.
 * - **An altar is a place.** It is built, returned to, destroyed.
 *
 * ## Candles are counted by candles, not by blocks
 *
 * The vanilla `CandleBlock` holds one to four candles in a single block, so
 * `CandleBlock.CANDLES` is the very number a recipe asks for. One block with four candles covers
 * a third-tier rite; four separate ones do too. The shape of the altar is left to the player.
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

    /** A second rite on top of the first is not a rite. A player has one session. */
    override fun canCast(context: CastContext): Boolean = !context.sessionRunning

    override fun cast(context: CastContext): AbilityOutcome {
        val level = context.level
        val centre = context.caster.blockPosition()

        val candles = countCandles(level, centre)
        if (candles < MIN_CANDLES) {
            return AbilityOutcome.failed(
                "Вівтар не складено: навколо вас має горіти щонайменше $MIN_CANDLES свічок (зараз $candles)",
            )
        }

        val offerings = offerings(level, centre)
        val hand = context.caster.getItemInHand(InteractionHand.MAIN_HAND)
        val recipe = choose(context.sequence, candles, offerings, hand)
            ?: return AbilityOutcome.failed(missingHint(context.sequence, candles))

        // Everything the rite will need after this line is taken now: the context lives one call,
        // and a `ServerPlayer` until the next death. Beyond this the session takes the player
        // from `owner` every tick, and everything else from here.
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
            messaging = context.messaging,
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
                // Cancellation (a recast, leaving, a server shutdown) puts the rite out too —
                // and the ticket stays unsettled, i.e. free.
                if (!settled) rite.messaging.actionBar(ownerId, Component.literal("Обряд згас"))
            }
        }

        level.playSound(
            null, rite.anchor.x, rite.anchor.y, rite.anchor.z,
            SoundEvents.CANDLE_AMBIENT, SoundSource.PLAYERS, 1.2f, 0.6f,
        )
        return AbilityOutcome.Awaiting(Component.literal("Обряд почався: ${recipe.displayName}"))
    }

    // ── Altar ───────────────────────────────────────────────────────────────────

    /** Both readings live in `Altar` since Error's disguise started asking the same questions. */
    private fun countCandles(level: ServerLevel, centre: BlockPos): Int = Altar.candles(level, centre)

    private fun offerings(level: ServerLevel, centre: BlockPos): List<ItemEntity> =
        Altar.offerings(level, centre)

    /**
     * The richest rite this altar supports.
     *
     * Sorted by the number of candles and the length of the ingredient list rather than by the
     * order in the catalogue: a player who built a five-candle altar with amethyst was clearly
     * not asking for a prayer of luck. Cheap rites stay available — just do not lay out extras.
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
     * The rites a Beyonder is already able to perform.
     *
     * ponytail: `BESTOWMENT` is excluded — its answer is a hint about where to look for an
     * ingredient, and the source index (`IngredientSourceIndex`) feeds on creature and forage
     * definitions, i.e. on phase 12 and its config. To bring it back, remove one line here once
     * the index has something to fill it with.
     */
    private fun available(sequence: Sequence): List<RitualRecipe> =
        RitualCatalog.availableFor(sequence.level).filter { it.type != RitualType.BESTOWMENT }

    private fun covered(recipe: RitualRecipe, offerings: List<ItemEntity>): Boolean =
        recipe.ingredients.all { (id, needed) ->
            offerings.filter { matches(it.item, id) }.sumOf { it.item.count } >= needed
        }

    /**
     * The ingredient names arrived from the plugin's config in the form `GOLD_INGOT` — that is
     * an `org.bukkit.Material`, and it matches the vanilla identifier one for one, because
     * Bukkit built its own list from it. So there is no translation, only case.
     */
    private fun matches(stack: ItemStack, id: String): Boolean {
        val key = Identifier.tryParse(id.lowercase()) ?: return false
        return BuiltInRegistries.ITEM.getValue(key) === stack.item
    }

    /** A refusal must teach: it lists what this altar would already accept. */
    private fun missingHint(sequence: Sequence, candles: Int): String {
        val reachable = available(sequence).filter { it.candlesRequired <= candles }
        if (reachable.isEmpty()) {
            return "Свічок ($candles) замало для будь-якого відомого вам обряду"
        }
        return "Приношення не підходить до жодного обряду. Вівтар із $candles свічок прийме: " +
            reachable.joinToString("; ") { recipe ->
                val what = recipe.ingredients.entries
                    .joinToString(", ") { "${it.key} ×${it.value}" }
                    .ifBlank { "предмет у руці" }
                "${recipe.displayName} ($what)"
            }
    }

    // ── The rite ────────────────────────────────────────────────────────────────

    /** The flame reaches towards the centre: a rite is visible to those nearby, not only to whoever leads it. */
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
        rite.messaging.actionBar(player, Component.literal("Ви вийшли з кола — обряд обірвався"))
        // "A broken ritual brings misfortune" is a promise from the catalogue, and it must come true.
        if (rite.recipe.type == RitualType.LUCK_PRAYER) {
            player.addEffect(MobEffectInstance(MobEffects.UNLUCK, BACKLASH_TICKS, 0, false, true, true))
        }
        (player.level() as ServerLevel).playSound(
            null, player.x, player.y, player.z,
            SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.7f,
        )
    }

    /** The offering burns exactly in the amount the recipe asked for: the rest stays lying there. */
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
        rite.messaging.actionBar(player, Component.literal("Доля прихильна: ${ticks / TICKS_PER_SECOND} с"))
    }

    private fun sanctify(player: ServerPlayer, rite: Rite) {
        val stack = player.getItemInHand(InteractionHand.MAIN_HAND)
        if (!stack.isDamageableItem || stack.damageValue == 0) {
            rite.messaging.actionBar(player, Component.literal("Освячувати нічого: предмет у руці цілий"))
            return
        }
        val repair = rite.scaled(RitualEffectMath.SANCTIFY_BASE_DURABILITY, SequenceScaler.ScalingStrategy.MODERATE)
        stack.damageValue = (stack.damageValue - repair).coerceAtLeast(0)
        rite.messaging.actionBar(player, Component.literal("Предмет освячено: +$repair міцності"))
    }

    /** The item has already burned in [consume] — what is appraised is a copy taken at cast time. */
    private fun burnOffering(player: ServerPlayer, self: Beyonder, rite: Rite) {
        val gain = SacrificeAppraiser.spiritualityFor(sacrificeKind(rite.sacrifice))
        self.setSpirituality(self.spirituality.increment(gain))
        rite.messaging.actionBar(player, Component.literal("Жертву прийнято: +$gain духовності"))
    }

    /**
     * The spirits show who is nearby — through walls and for half a minute.
     *
     * The catalogue promises "a tale of past events", but the mod does not record the past and
     * will not start: a world-change journal for the sake of one ability is exactly what
     * `CrimeReconstruction` turned down. What the spirits know without a journal is who is
     * **here now**.
     */
    private fun reveal(player: ServerPlayer, level: ServerLevel, rite: Rite) {
        val ticks = rite.scaled(RitualEffectMath.WALL_BASE_TICKS, SequenceScaler.ScalingStrategy.MODERATE)
        val box = AABB.ofSize(player.position(), REVEAL_SPAN, REVEAL_SPAN, REVEAL_SPAN)
        val seen: List<UUID> = level.getEntitiesOfClass(LivingEntity::class.java, box)
            .filter { it !== player }
            .onEach { it.setGlowingTag(true) }
            .map { it.uuid }

        // Clearing has to go by identifier: in half a minute half of those shown may be dead, and
        // an outline cannot be taken off a dead entity.
        rite.scheduling.after(ticks) {
            seen.forEach { id -> (level.getEntityInAnyDimension(id) as? LivingEntity)?.setGlowingTag(false) }
        }
        rite.messaging.actionBar(
            player,
            Component.literal("Духи вказують: ${seen.size} присутніх на ${ticks / TICKS_PER_SECOND} с"),
        )
    }

    /** The mirror shows not a place but people: who in this world carries power, and which. */
    private fun divine(player: ServerPlayer, rite: Rite) {
        val others = rite.beyonders.online.filter { it.playerId != player.uuid }
        if (others.isEmpty()) {
            rite.messaging.actionBar(player, Component.literal("Дзеркало порожнє: інших носіїв сили немає"))
            return
        }

        rite.messaging.tell(player, Component.literal("Дзеркало показує тих, хто носить силу:"))
        others.forEach { other ->
            val who = player.level().server.playerList.getPlayer(other.playerId)
            val name = who?.name?.string ?: "невідомий"
            val where = when {
                who == null -> "поза світом"
                who.level() !== player.level() -> "в іншому світі"
                else -> "${player.position().distanceTo(who.position()).toInt()} бл."
            }
            rite.messaging.tell(
                player,
                Component.literal(
                    "  $name — ${other.pathway.name} ${other.sequenceLevel} " +
                        "(${other.pathway.sequenceName(other.sequenceLevel)}) · $where",
                ),
            )
        }
    }

    /** The spirit wall: a circle around the altar holds those inside it, and it is visible. */
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
        rite.messaging.actionBar(player, Component.literal("Стіна духовності стоїть ${ticks / TICKS_PER_SECOND} с"))
    }

    /** Vanilla rarity is a ready-made scale of worth; the mod keeps no material list of its own. */
    private fun sacrificeKind(stack: ItemStack): SacrificeKind = when {
        stack.isEmpty -> SacrificeKind.TRIFLE
        stack.get(DataComponents.RARITY) == Rarity.EPIC -> SacrificeKind.PRECIOUS
        stack.get(DataComponents.RARITY) == Rarity.RARE -> SacrificeKind.VALUABLE
        stack.get(DataComponents.RARITY) == Rarity.UNCOMMON || stack.isEnchanted -> SacrificeKind.VALUABLE
        else -> SacrificeKind.TRIFLE
    }

    /**
     * Everything the rite takes from the context at cast time.
     *
     * The context lives one call and carries the caster's identity, so it does not go into the
     * session — only values and services that outlive the context move here.
     */
    private inner class Rite(
        val recipe: RitualRecipe,
        val sequence: Sequence,
        val anchor: Vec3,
        val mote: ParticleOptions,
        val candles: Int,
        /** A copy of the item in hand: by the time the sacrifice is appraised the original has burned. */
        val sacrifice: ItemStack,
        val offerings: List<ItemEntity>,
        val scheduling: Scheduling,
        val beyonders: Beyonders,
        val messaging: Messaging,
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
        /** 20 s of bad luck for a broken prayer. */
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
