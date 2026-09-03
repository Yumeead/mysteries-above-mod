package me.vangoo.mysteries.pathways.whitetower.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

/**
 * WhiteTower, Seq. 5 (Master of Mysticism): **Composed Spell**.
 *
 * The Master has no list of ready spells — they **write** them. Take a book and quill, compose an
 * incantation, hold the book in hand and cast: the mod will find the words of power in your text
 * and assemble a spell from them.
 *
 * ```
 * Хай промінь вогню вдарить далеко й сильно
 * ```
 * → Beam · fire · far ×1 · strong ×1
 *
 * ## Why a book rather than a builder in a chest
 *
 * The plugin's `Spellcasting` is 562 lines, and 480 of them lay icons out into the slots of a
 * six-row chest: the shape slot, the modifier slots, the preview slot, the "create" slot, the
 * name randomiser slot. That is not spell design but drawing an interface by hand, because Spigot
 * had no other interface.
 *
 * A text editor in the game **already exists**, and it is vanilla: a book and quill. It can be
 * named, signed, put into a chest, given to another player, copied in a copier. All of those are
 * mechanics the chest builder gave none of, and not one of them cost a line of code here.
 *
 * A side effect that turned out to be the main one: **a spell became an item**. A Master's
 * grimoire can be stolen.
 *
 * ## Words are matched by stem, not by exact match
 *
 * Otherwise one would have to write "промінь вогонь сильно" — a list, not an incantation. The
 * stem ("вог" in "вогню", "вогнем", "вогненний") catches the grammatical case and lets the rest
 * of the text be whatever it likes. Words the mod does not know are simply not read — that is, a
 * poem around the words of power is allowed, and that is exactly what an incantation should be.
 *
 * ## The cost is declared as a constant and the surcharge is taken separately
 *
 * `AbilitySpec.spiritualityCost` is a property, and `Beyonder.authorize` reads it **before**
 * anyone has seen the book. So the ticket covers the cheapest form, and the surplus is taken by
 * `Beyonder.payUpkeep` during the cast: it spends everything or nothing, so a spell is never
 * half paid for.
 */
class Spellcasting : ActiveAbility(AbilityIdentity.of("whitetower_spellcasting")) {

    override val displayName: Component = Component.literal("Складене заклинання")

    /** Covers the cheapest form; the cast itself pays the rest. */
    override val spiritualityCost: Int = Shape.entries.minOf { it.cost }

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Тримайте книгу із замовлянням — мод знайде в тексті слова сили.\n" +
            "▪ Форма: ${Shape.entries.joinToString(", ") { it.label.lowercase() }}\n" +
            "▪ Стихія: ${Element.entries.joinToString(", ") { it.label }}\n" +
            "▪ Підсилення: сильно, далеко, довго (повторення додає ще)\n" +
            "▪ Ціна росте зі складністю; сила росте з Послідовністю",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val text = bookInHand(context)
            ?: return AbilityOutcome.failed("У руках немає книги: заклинання треба спершу написати")
        val spell = Incantation.read(text) ?: return AbilityOutcome.failed(
            "У тексті немає форми. Впишіть одне зі слів: " +
                Shape.entries.joinToString(", ") { it.label.lowercase() },
        )

        val surplus = spell.cost - spiritualityCost
        if (surplus > 0 && !context.beyonder.payUpkeep(surplus)) {
            return AbilityOutcome.failed("Забракло духовності: заклинання коштує ${spell.cost}")
        }

        val mote = ModParticles.mote(context.effects.pathwayColor)
        when (spell.shape) {
            Shape.BEAM -> beam(context, spell, mote)
            Shape.BURST -> burst(context, spell, mote)
            Shape.STEP -> step(context, spell, mote)
            Shape.WARD -> ward(context, spell)
            Shape.MEND -> mend(context, spell)
        }
        return AbilityOutcome.done(spell.summary())
    }

    // ── Shapes ──────────────────────────────────────────────────────────────────

    private fun beam(context: CastContext, spell: Incantation, mote: ParticleOptions) {
        val reach = spell.reach(BEAM_RANGE)
        val target = context.targeting.lookingAt(reach)
        val end = target?.let { it.position().add(0.0, it.bbHeight * 0.5, 0.0) }
            ?: context.eye.add(context.look.scale(reach))

        trace(context, context.eye, end, mote)
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.6f,
        )
        target?.let { strike(context, it, spell) }
    }

    private fun burst(context: CastContext, spell: Incantation, mote: ParticleOptions) {
        val centre = context.caster.pick(spell.reach(BURST_RANGE), 1.0f, false).location
        val radius = spell.reach(BURST_RADIUS)

        for (ring in 1..BURST_RINGS) {
            val r = radius * ring / BURST_RINGS
            repeat(BURST_POINTS) { i ->
                val angle = TAU * i / BURST_POINTS
                context.level.sendParticles(
                    mote,
                    centre.x + r * cos(angle), centre.y + 0.2, centre.z + r * sin(angle),
                    1, 0.0, 0.0, 0.0, 0.0,
                )
            }
        }
        context.level.playSound(
            null, centre.x, centre.y, centre.z,
            SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7f, 1.5f,
        )

        val box = AABB.ofSize(centre, radius * 2, radius * 2, radius * 2)
        context.level.getEntitiesOfClass(LivingEntity::class.java, box)
            .filter { it !== context.caster && it.distanceToSqr(centre) <= radius * radius }
            .forEach { strike(context, it, spell) }
    }

    private fun step(context: CastContext, spell: Incantation, mote: ParticleOptions) {
        val landing = context.caster.pick(spell.reach(STEP_RANGE), 1.0f, false).location

        puff(context, context.origin, mote)
        context.caster.teleportTo(landing.x, landing.y, landing.z)
        puff(context, landing, mote)
        context.level.playSound(
            null, landing.x, landing.y, landing.z,
            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.3f,
        )
    }

    private fun ward(context: CastContext, spell: Incantation) {
        val ticks = spell.length(WARD_SECONDS) * TICKS_PER_SECOND

        context.caster.addEffect(MobEffectInstance(MobEffects.ABSORPTION, ticks, spell.power, false, true, true))
        context.caster.addEffect(MobEffectInstance(MobEffects.RESISTANCE, ticks, 0, false, true, true))
        context.effects.bodyAura(context.caster, ticks)
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7f, 1.4f,
        )
    }

    private fun mend(context: CastContext, spell: Incantation) {
        val target = context.targeting.playerLookingAt(MEND_RANGE) ?: context.caster
        val ticks = spell.length(MEND_SECONDS) * TICKS_PER_SECOND

        target.addEffect(MobEffectInstance(MobEffects.REGENERATION, ticks, spell.power, false, true, true))
        context.effects.bodyAura(target, ticks, counterFlow = ParticleTypes.HEART)
        context.level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.0f,
        )
    }

    // ── Impact ──────────────────────────────────────────────────────────────────

    /** Damage plus whatever the element added. Forms that hit nobody never come in here. */
    private fun strike(context: CastContext, victim: LivingEntity, spell: Incantation) {
        victim.hurtServer(
            context.level,
            context.level.damageSources().indirectMagic(context.caster, context.caster),
            potency(context.sequence, spell).toFloat(),
        )

        val seconds = spell.length(ELEMENT_SECONDS)
        val ticks = seconds * TICKS_PER_SECOND
        when (spell.element) {
            Element.FIRE -> victim.igniteForSeconds(seconds.toFloat())
            Element.ICE -> victim.addEffect(MobEffectInstance(MobEffects.SLOWNESS, ticks, spell.power))
            Element.POISON -> victim.addEffect(MobEffectInstance(MobEffects.POISON, ticks, spell.power))
            Element.THUNDER -> thunder(context, victim)
            null -> Unit
        }
    }

    /**
     * The lightning is for show only: the blow has already been dealt, and a vanilla bolt would
     * add its own damage, its own ignition and — under open sky — its own rules. The balance stays
     * the number above, the effect is real lightning.
     */
    private fun thunder(context: CastContext, victim: LivingEntity) {
        val bolt = EntityTypes.LIGHTNING_BOLT.create(context.level, EntitySpawnReason.TRIGGERED) ?: return
        bolt.setVisualOnly(true)
        bolt.snapTo(victim.x, victim.y, victim.z)
        context.level.addFreshEntity(bolt)
    }

    // ── Drawing ─────────────────────────────────────────────────────────────────

    private fun trace(context: CastContext, from: Vec3, to: Vec3, mote: ParticleOptions) {
        val steps = (from.distanceTo(to) / BEAM_STEP).toInt().coerceIn(1, BEAM_MAX_STEPS)
        for (i in 0..steps) {
            val point = from.lerp(to, i.toDouble() / steps)
            context.level.sendParticles(mote, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.0)
        }
    }

    private fun puff(context: CastContext, at: Vec3, mote: ParticleOptions) {
        context.level.sendParticles(mote, at.x, at.y + 1.0, at.z, 20, 0.3, 0.6, 0.3, 0.02)
    }

    /** A spell's power: the words give the step, the Sequence the multiplier. */
    private fun potency(sequence: Sequence, spell: Incantation): Int =
        scale(BASE_POTENCY + spell.power * POTENCY_PER_WORD, sequence, SequenceScaler.ScalingStrategy.STRONG)

    // ── The book ────────────────────────────────────────────────────────────────

    /**
     * The text from a book in either hand. Both are accepted: an unwritten book and quill (it can
     * be edited on the move) and a signed one (it can be given away and copied).
     */
    private fun bookInHand(context: CastContext): String? =
        InteractionHand.entries.firstNotNullOfOrNull { hand -> textOf(context.caster.getItemInHand(hand)) }

    private fun textOf(stack: ItemStack): String? {
        stack.get(DataComponents.WRITABLE_BOOK_CONTENT)?.let { book ->
            return book.getPages(false).toList().joinToString(" ").ifBlank { null }
        }
        stack.get(DataComponents.WRITTEN_BOOK_CONTENT)?.let { book ->
            return book.getPages(false).joinToString(" ") { it.string }.ifBlank { null }
        }
        return null
    }

    // ── Grammar ─────────────────────────────────────────────────────────────────

    internal enum class Shape(val label: String, val cost: Int, val stems: List<String>) {
        BEAM("Промінь", 60, listOf("промін")),
        BURST("Вибух", 90, listOf("вибух")),
        STEP("Стрибок", 40, listOf("стриб")),
        WARD("Щит", 50, listOf("щит")),
        MEND("Зцілення", 70, listOf("зцілен", "зціли")),
    }

    internal enum class Element(val label: String, val stems: List<String>) {
        FIRE("вогонь", listOf("вог")),
        ICE("лід", listOf("лід", "льод", "криг", "криж")),
        THUNDER("грім", listOf("грім", "гром")),
        POISON("отрута", listOf("отру")),
    }

    /**
     * A parsed incantation.
     *
     * Unknown words are deliberately ignored: an incantation must read as text, not as a list of
     * arguments. The cost is computed right here — it is the measure of complexity.
     */
    internal class Incantation(
        val shape: Shape,
        val element: Element?,
        val power: Int,
        val reachWords: Int,
        val lengthWords: Int,
    ) {
        val cost: Int = shape.cost +
            (if (element == null) 0 else ELEMENT_COST) +
            (power + reachWords + lengthWords) * WORD_COST

        fun reach(base: Double): Double = base * (1.0 + reachWords * REACH_STEP)

        fun length(baseSeconds: Int): Int = baseSeconds + lengthWords * LENGTH_STEP_SECONDS

        fun summary(): String = buildString {
            append(shape.label)
            element?.let { append(" · ").append(it.label) }
            if (power > 0) append(" · сильно ×").append(power)
            if (reachWords > 0) append(" · далеко ×").append(reachWords)
            if (lengthWords > 0) append(" · довго ×").append(lengthWords)
            append(" · ").append(cost).append(" духовності")
        }

        companion object {
            private val POWER_STEMS = listOf("сильн", "міцн", "потужн")
            private val REACH_STEMS = listOf("далек", "дальн", "широк")
            private val LENGTH_STEMS = listOf("довг", "трив", "надовго")
            private val SPLIT = Regex("[^\\p{L}]+")

            fun read(text: String): Incantation? {
                val words = text.lowercase().split(SPLIT).filter { it.isNotBlank() }
                val shape = Shape.entries.firstOrNull { form -> words.any { w -> form.stems.any(w::contains) } }
                    ?: return null
                return Incantation(
                    shape = shape,
                    element = Element.entries.firstOrNull { e -> words.any { w -> e.stems.any(w::contains) } },
                    power = count(words, POWER_STEMS),
                    reachWords = count(words, REACH_STEMS),
                    lengthWords = count(words, LENGTH_STEMS),
                )
            }

            /** Repetition strengthens, but not endlessly: otherwise a page of "сильно" would be a spell. */
            private fun count(words: List<String>, stems: List<String>): Int =
                words.count { w -> stems.any(w::contains) }.coerceAtMost(MAX_WORD_STACKS)
        }
    }

    private companion object {
        const val COOLDOWN_SECONDS = 20
        const val MAX_WORD_STACKS = 3
        const val WORD_COST = 25
        const val ELEMENT_COST = 30
        const val REACH_STEP = 0.5
        const val LENGTH_STEP_SECONDS = 3

        const val BASE_POTENCY = 3
        const val POTENCY_PER_WORD = 2

        const val BEAM_RANGE = 20.0
        const val BEAM_STEP = 0.35
        const val BEAM_MAX_STEPS = 120
        const val BURST_RANGE = 16.0
        const val BURST_RADIUS = 3.5
        const val BURST_RINGS = 3
        const val BURST_POINTS = 16
        const val STEP_RANGE = 12.0
        const val MEND_RANGE = 16.0
        const val WARD_SECONDS = 8
        const val MEND_SECONDS = 5
        const val ELEMENT_SECONDS = 4

        const val TAU = 2.0 * Math.PI
    }
}
