package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.DivinationOdds
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import kotlin.math.roundToInt

/**
 * Door, Посл. 7 (Астролог): **Мистецтво ворожіння**.
 *
 * Найбільша здібність плагіна на цьому шляху — 813 рядків — і майже вся вона була **меню**:
 * `openChoiceMenu` вибирав спосіб, друге меню — питання маятника, третє — що шукати лозою,
 * четверте — за ким підглядати. Механіки в цьому не було жодної.
 *
 * ## Інструмент у руці замість меню
 *
 * Спосіб ворожіння обирає предмет у головній руці — той самий хід, що зробив `RitualMagic`
 * зі справжнім вівтарем. Це не обхід відсутніх екранів (фаза 11): у ворожбита **мусить**
 * бути інструмент, тож питати про нього окремим списком означало питати двічі.
 *
 * | інструмент | спосіб |
 * |---|---|
 * | далекогляд | астрологія — зірки віщують удачу або біду |
 * | залізний ланцюг | духовний маятник — відповідь «так» чи «ні» на те, що перед вами |
 * | палиця | лозошукання — веде до найціннішої руди, яку тягне ваша Послідовність |
 * | аметистова друза | кришталева куля — де хтось із живих і чого він вартий |
 *
 * ## Чого немає: сонного провидіння
 *
 * П'ятий спосіб плагіна саджав ворожбита в режим спостерігача над обраним гравцем. Це
 * рівно те, що робить `TravellersDoor` на Посл. 5, лише з двома Послідовностями фори —
 * тобто шлях мав би дві сили з тим самим дієсловом, а сильнішу отримував пізніше. Те,
 * що з нього справді цінне («дізнатись, де хтось і як він»), лишилось у кришталевій кулі.
 */
class DivinationArts : ActiveAbility(AbilityIdentity.of("door_divination_arts")) {

    override val displayName: Component = Component.literal("Мистецтво ворожіння")

    override val spiritualityCost: Int = 120

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override val icon: ItemStack get() = ItemStack(Items.AMETHYST_CLUSTER)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Спосіб ворожіння обирає інструмент у руці.\n" +
            "▪ Далекогляд — астрологія: зірки віщують удачу або біду\n" +
            "▪ Залізний ланцюг — маятник: «так» чи «ні» про те, що перед вами\n" +
            "▪ Палиця — лоза: веде до ${rodQuarry(sequence)?.name ?: "руди"} в ${ROD_RADIUS} бл.\n" +
            "▪ Аметистова друза — куля: де хтось із живих і чого він вартий\n" +
            "▪ Перепочинок ${COOLDOWN_SECONDS} с",
    )

    override fun cast(context: CastContext): AbilityOutcome =
        when (context.caster.mainHandItem.item) {
            Items.SPYGLASS -> astrology(context)
            Items.IRON_CHAIN -> pendulum(context)
            Items.STICK -> diviningRod(context)
            Items.AMETHYST_CLUSTER -> crystalBall(context)
            else -> AbilityOutcome.failed(
                "Ворожать інструментом: далекогляд, залізний ланцюг, палиця або аметистова друза",
            )
        }

    // ── Астрологія ──────────────────────────────────────────────────────────────

    /**
     * Чесна монета: половина на квапливість, половина на слабкість. Ставка тут не в
     * силі ефекту, а в тому, що Астролог сам не знає, що витягне, — і платить за це
     * повну ціну касту.
     */
    private fun astrology(context: CastContext): AbilityOutcome {
        val kind = context.level.random.nextBoolean()
        val effect = if (kind) MobEffects.HASTE else MobEffects.WEAKNESS
        context.caster.addEffect(MobEffectInstance(effect, OMEN_TICKS, 0))

        val at = context.origin
        context.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 2.0, at.z, 30, 0.6, 0.4, 0.6, 0.02)
        context.level.playSound(
            null, at.x, at.y, at.z,
            if (kind) SoundEvents.PLAYER_LEVELUP else SoundEvents.ELDER_GUARDIAN_CURSE,
            SoundSource.PLAYERS, 0.8f, if (kind) 2.0f else 1.0f,
        )

        return AbilityOutcome.done(
            if (kind) {
                "✦ Зірки прихильні: квапливість на ${OMEN_TICKS / TICKS_PER_SECOND / 60} хв"
            } else {
                "✦ Зірки застерігають: слабкість на ${OMEN_TICKS / TICKS_PER_SECOND / 60} хв"
            },
        )
    }

    // ── Маятник ─────────────────────────────────────────────────────────────────

    /**
     * Питання маятника не вибирають — його задає те, на що дивиться ворожбит.
     *
     * Плагінних питань було шість, і чотири з них питали про підсистеми, яких у моді
     * нема (журнал подій місця, рецепти зілля, готовність до ритуалу). Лишились дві, які
     * гра здатна чесно перевірити, і саме вони й розрізняються поглядом: дивишся на
     * когось — питаєш про нього, не дивишся — питаєш про околицю.
     */
    private fun pendulum(context: CastContext): AbilityOutcome {
        chime(context)

        val watched = context.targeting.playerLookingAt(PENDULUM_RANGE)
        if (watched != null) {
            val theirs = context.beyonders.of(watched.uuid)
                ?: return AbilityOutcome.done("❓ Сильніший за вас? — Ні: перед вами звичайна людина")
            val mine = context.beyonder.sequenceLevel
            return AbilityOutcome.done(
                when {
                    theirs.sequenceLevel < mine ->
                        "❓ Сильніший за вас? — Так, і помітно: маятник рветься з рук"
                    theirs.sequenceLevel == mine -> "❓ Сильніший за вас? — Ні: рівний"
                    else -> "❓ Сильніший за вас? — Ні: слабший"
                },
            )
        }

        val others = context.targeting.playersNear(PENDULUM_RANGE)
            .count { context.beyonders.isBeyonder(it.uuid) }
        return AbilityOutcome.done(
            when (others) {
                0 -> "❓ Є поблизу Потойбічні? — Ні: навколо лише звичайні люди"
                1 -> "❓ Є поблизу Потойбічні? — Так: один"
                else -> "❓ Є поблизу Потойбічні? — Так: $others, будьте обережні"
            },
        )
    }

    // ── Лозошукання ─────────────────────────────────────────────────────────────

    /**
     * Лоза шукає **найцінніше, що тягне Послідовність**, і веде до нього десять секунд.
     *
     * Плагінне меню з дев'яти цілей тут не потрібне з тієї ж причини, з якої не потрібне
     * головне: вибір між «шукати вугілля» і «шукати алмази» — це не вибір. Лоза й так
     * веде до кращого, а ростуть із Послідовністю саме межі того, що вона бачить.
     */
    private fun diviningRod(context: CastContext): AbilityOutcome {
        val quarry = rodQuarry(context.sequence)
            ?: return AbilityOutcome.failed("На вашому рівні лоза ще нічого не чує")

        val found = nearest(context.level, context.caster.blockPosition(), context.sequence)
            ?: return AbilityOutcome.failed("Нічого не знайдено в радіусі $ROD_RADIUS бл.")

        val (tier, at) = found
        val target = Vec3(at.x + 0.5, at.y + 0.5, at.z + 0.5)
        val glyph = ModParticles.glyph(context.effects.pathwayColor)
        val messaging = context.messaging

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.3f,
        )

        context.session {
            sustain(periodTicks = ROD_PERIOD_TICKS, durationTicks = ROD_TICKS) { diviner ->
                val level = diviner.level() as? ServerLevel ?: return@sustain
                val from = diviner.eyePosition
                val heading = target.subtract(from)
                val distance = heading.length()
                val step = heading.normalize()
                for (i in 1..ROD_NEEDLE_GLYPHS) {
                    val point = from.add(step.scale(i * ROD_NEEDLE_SPACING))
                    level.sendParticles(glyph, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
                }
                messaging.actionBar(
                    diviner,
                    Component.literal("🔍 ${tier.name}: ${distance.roundToInt()} бл."),
                )
            }
        }

        return AbilityOutcome.done("Лоза веде до: ${quarry.name}")
    }

    /** Найкраще, що ця Послідовність узагалі здатна відчути; `null` — жодного рівня не відкрито. */
    private fun rodQuarry(sequence: Sequence): Quarry? =
        QUARRIES.firstOrNull { sequence.level <= it.unlockSequence }

    /**
     * Один прохід по кубу замість дев'яти.
     *
     * Плагін сканував радіус 50 **для кожного матеріалу цілі окремо** — тобто до
     * мільйона читань блоку на матеріал. Тут прохід один, а «цінніше» вирішує номер
     * рівня: перше входження з меншим номером витісняє знайдене, рівне за номером —
     * лише якщо ближче.
     *
     * ponytail: суцільний скан куба 41³; якщо колись стане дорого, підказка — ходити
     * по секціях чанка й пропускати ті, де потрібного блоку немає взагалі.
     */
    private fun nearest(level: ServerLevel, from: BlockPos, sequence: Sequence): Pair<Quarry, BlockPos>? {
        val allowed = QUARRIES.filter { sequence.level <= it.unlockSequence }
        if (allowed.isEmpty()) return null
        val lookup = HashMap<Block, Quarry>()
        allowed.forEach { quarry -> quarry.blocks.forEach { lookup[it] = quarry } }

        var bestQuarry: Quarry? = null
        var bestPos: BlockPos? = null
        var bestDistance = Double.MAX_VALUE

        val corner = from.offset(-ROD_RADIUS, -ROD_RADIUS, -ROD_RADIUS)
        val opposite = from.offset(ROD_RADIUS, ROD_RADIUS, ROD_RADIUS)
        for (pos in BlockPos.betweenClosed(corner, opposite)) {
            val quarry = lookup[level.getBlockState(pos).block] ?: continue
            val distance = from.distSqr(pos)
            val best = bestQuarry
            val better = best == null ||
                quarry.rank < best.rank ||
                (quarry.rank == best.rank && distance < bestDistance)
            if (better) {
                bestQuarry = quarry
                bestPos = pos.immutable()
                bestDistance = distance
            }
        }
        return bestQuarry?.let { it to bestPos!! }
    }

    // ── Кришталева куля ─────────────────────────────────────────────────────────

    /**
     * Куля показує **когось із живих**, а не обраного: обирає вона, не ворожбит. Саме
     * тому вона й окрема від маятника — той питає про те, на що ти дивишся.
     *
     * Опір ([AntiDivination]) кидається тут і тільки тут: решта способів ні на кого не
     * дивиться, тож і опиратись їм нема кому.
     */
    private fun crystalBall(context: CastContext): AbilityOutcome {
        val others = context.level.server.playerList.players.filter { it.uuid != context.caster.uuid }
        if (others.isEmpty()) return AbilityOutcome.done(omen(context))

        val seen = others[context.level.random.nextInt(others.size)]
        val at = context.origin
        context.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.5, at.z, 30, 0.5, 0.5, 0.5, 0.0)
        context.level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.8f,
        )

        val odds = resistance(context, seen)
        if (odds != null && context.level.random.nextDouble() >= odds.successProbability()) {
            return AbilityOutcome.done(
                "🔮 Бачення каламутне (шанс ${odds.formattedPercent()}) — ${omen(context)}",
            )
        }

        return AbilityOutcome.done("🔮 ${reveal(context, seen)}")
    }

    /**
     * `null` — ціль не опирається взагалі, і кидати нема чого.
     *
     * Дві умови, обидві плагінні: щит увімкнено **і** ціль доросла до нього
     * ([AntiDivination.UNLOCK_SEQUENCE]).
     */
    private fun resistance(context: CastContext, seen: ServerPlayer): DivinationOdds? {
        val theirs: Beyonder = context.beyonders.of(seen.uuid) ?: return null
        if (theirs.sequenceLevel > AntiDivination.UNLOCK_SEQUENCE) return null
        if (!context.beyonders.isToggled(seen.uuid, AntiDivination.IDENTITY)) return null
        return DivinationOdds(context.beyonder.sequenceLevel, theirs.sequenceLevel)
    }

    private fun reveal(context: CastContext, seen: ServerPlayer): String {
        val where = seen.level().dimension().identifier().path
        val vitals = "❤ ${seen.health.roundToInt()}/${seen.maxHealth.roundToInt()}"
        val who = context.beyonders.of(seen.uuid)?.let {
            " · ${it.pathway.name} ${it.sequenceLevel} (${it.pathway.sequenceName(it.sequenceLevel)})"
        } ?: " · без ознак потойбічності"
        return "${seen.name.string}: $where ${seen.blockX} ${seen.blockY} ${seen.blockZ} · $vitals$who"
    }

    /** Куля мовчить про людей — тоді вона каже про небо. Дешева правда замість порожнечі. */
    private fun omen(context: CastContext): String {
        val day = context.level.gameTime / DAY_TICKS
        val sky = if (context.level.isRaining) "дощ" else "ясно"
        return "у небі $sky, іде ${day + 1}-й день світу"
    }

    private fun chime(context: CastContext) {
        val at = context.origin
        context.level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS, 0.4f, 1.5f,
        )
        context.level.sendParticles(
            ModParticles.mote(context.effects.pathwayColor),
            at.x, at.y + 1.4, at.z, 12, 0.3, 0.3, 0.3, 0.0,
        )
    }

    /**
     * Що лоза вміє відчути. [rank] — цінність (0 — найцінніше), [unlockSequence] — найслабша
     * Послідовність, якій це вже доступне.
     */
    private class Quarry(
        val name: String,
        val rank: Int,
        val unlockSequence: Int,
        vararg val blocks: Block,
    )

    private companion object {
        const val COOLDOWN_SECONDS = 60
        const val OMEN_TICKS = 10 * 60 * TICKS_PER_SECOND
        const val PENDULUM_RANGE = 30.0
        const val ROD_RADIUS = 20
        const val ROD_TICKS = 10 * TICKS_PER_SECOND
        const val ROD_PERIOD_TICKS = 10
        const val ROD_NEEDLE_GLYPHS = 5
        const val ROD_NEEDLE_SPACING = 0.6
        const val DAY_TICKS = 24_000L

        /**
         * Від найціннішого до найдешевшого. Списки блоків тут руками, а не тегом:
         * ванілька 26.2 має теги лише на три руди (`IRON_ORES`, `GOLD_ORES`,
         * `COPPER_ORES`), тож половина рівнів однаково лишилась би переліком, а мішанина
         * з тегів і списків читається гірше за просто список.
         */
        val QUARRIES = listOf(
            Quarry("Стародавні уламки", 0, 5, Blocks.ANCIENT_DEBRIS),
            Quarry("Алмази", 1, 7, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
            Quarry("Смарагди", 2, 8, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
            Quarry("Золото", 3, 9, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE),
            Quarry("Лазурит", 4, 9, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
            Quarry("Редстоун", 5, 9, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
            Quarry("Залізо", 6, 9, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
            Quarry("Мідь", 7, 9, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
            Quarry("Вугілля", 8, 9, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
        )
    }
}
