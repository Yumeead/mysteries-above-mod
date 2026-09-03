package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance
import me.vangoo.mysteries.ability.Ability
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.Subscription
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.beyonder.BeyonderStatus
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.event.AbilityCast
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Door, Seq. 6 (Scribe): **Record Ability**.
 *
 * The Scribe takes aim at a Beyonder and waits. The moment that Beyonder uses a power, the Scribe
 * writes it down for themselves, **for one use**.
 *
 * ## Waiting instead of a menu, and why it is the same thing
 *
 * The plugin opened a list of nearby players' heads and only then waited ten seconds for someone
 * else's cast. The list is not needed here: the target has to be nearby anyway, and "nearby and in
 * the crosshair" is already a choice made by hand. The same move as in the White Tower's `Analysis`.
 *
 * ## One record at a time
 *
 * The plugin kept up to twenty recorded powers, computed the limit from the Sequence and mastery
 * (22 lines) and had to have a deletion mode so the player could free up room. Here there is one
 * record: a new one displaces the previous. Gone are the limit, the mode and the question of
 * "what does persistence do with that list" — and the power stayed the same: someone else's
 * technique that you have to spend.
 *
 * ## The ticket is settled on the record, not on the cast
 *
 * The cast only **begins** the wait, so it returns [AbilityOutcome.Awaiting], and the spirituality
 * is spent when a power actually lands in the notebook. A target that never used anything costs
 * the Scribe nothing.
 */
class Record : ActiveAbility(AbilityIdentity.of("door_record")) {

    override val displayName: Component = Component.literal("Запис здібності")

    override val spiritualityCost: Int = 80

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override val icon: ItemStack get() = ItemStack(Items.WRITABLE_BOOK)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви берете Потойбічного на приціл і чекаєте його сили.\n" +
            "▪ Ціль: Потойбічний у ${RANGE.toInt()} бл.; чекання ${WATCH_TICKS / TICKS_PER_SECOND} с\n" +
            "▪ Записана сила лишається до першого використання\n" +
            "▪ Новий запис витісняє попередній\n" +
            "▪ Чим вища Послідовність сили, тим імовірніший провал",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE)
            ?: return AbilityOutcome.failed("Наведіть погляд на Потойбічного")
        val donor = context.beyonders.of(target)
            ?: return AbilityOutcome.failed("${target.name.string} — не Потойбічний")

        val pending = context.cast
        val scribe = context.beyonder
        val messaging = context.messaging
        val owner = context.caster.uuid
        val donorId = donor.playerId
        val donorSequence = donor.sequenceLevel
        val mySequence = scribe.sequenceLevel

        // The subscription must remove itself on the first hit, i.e. the handler needs a reference
        // to it and it to the handler. Resolved with `lateinit`, as in `bodyAura`.
        lateinit var watch: Subscription
        watch = context.events.on<AbilityCast>(WATCH_TICKS) { event ->
            if (event.caster.uuid != donorId) return@on
            val spec = event.spec
            if (!isRecordable(spec)) {
                messaging.actionBar(owner, Component.literal("Ця сила надто абстрактна для запису"))
                return@on
            }
            watch.cancel()

            val chance = SequenceBasedSuccessChance(mySequence, donorSequence)
            if (!chance.rollSuccess()) {
                messaging.actionBar(
                    owner,
                    Component.literal("Запис зірвався (шанс ${chance.getFormattedChance()})"),
                )
                return@on
            }

            // Displace the previous record: there is one place in the notebook.
            scribe.abilities.filterIsInstance<OneShot>().forEach { scribe.remove(it.identity) }
            val recorded = OneShot(spec as ActiveAbility)
            if (!scribe.grantOffPathway(recorded)) return@on

            pending.complete()
            messaging.actionBar(owner, Component.literal("✓ Записано: ${nameOf(spec)} (на один раз)"))
            (event.caster.level().getPlayerByUUID(owner) as? ServerPlayer)?.let { scribeSelf ->
                BeyonderStatus.pushRoster(scribeSelf)
                scribeSelf.level().playSound(
                    null, scribeSelf.x, scribeSelf.y, scribeSelf.z,
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.5f,
                )
            }
        }

        context.level.sendParticles(
            ModParticles.mote(context.effects.pathwayColor),
            target.x, target.y + target.bbHeight * 0.7, target.z,
            12, 0.3, 0.3, 0.3, 0.0,
        )
        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.NOTE_BLOCK_PLING, SoundSource.PLAYERS, 1.0f, 2.0f,
        )
        return AbilityOutcome.Awaiting(
            Component.literal("Чекаєте сили: ${target.name.string}"),
        )
    }

    /**
     * Only what is **done** can be recorded — that is, an active power, and not a reflexive one.
     *
     * The ban on `Аналіз`, `Складене заклинання` and `Запис` itself comes from the plugin, and the
     * reason for all three is one: they have no effect of their own but merely handle other
     * people's powers. A recorded `Запис` would give an endless ladder, a recorded `Аналіз` a
     * second one just like it.
     */
    private fun isRecordable(spec: AbilitySpec): Boolean =
        spec is ActiveAbility && spec.identity.id !in FORBIDDEN

    private fun nameOf(spec: AbilitySpec): String =
        (spec as? Ability)?.displayName?.string ?: spec.identity.id

    /**
     * Someone else's power that disappears after its first use.
     *
     * A wrapper, not a copy: the cost, the cooldown, the description and the effect itself stay
     * the donor's, and the notebook adds exactly one thing — it removes itself the moment it
     * fires. The identity is its own, because otherwise `grantOffPathway` would see it as the same
     * power the donor has.
     */
    private class OneShot(private val inner: ActiveAbility) :
        ActiveAbility(AbilityIdentity.of("door_recorded_${inner.identity.id}")) {

        override val displayName: Component =
            Component.literal("${inner.displayName.string} (запис)")

        override val spiritualityCost: Int get() = inner.spiritualityCost

        override fun cooldownSeconds(sequence: Sequence): Int = inner.cooldownSeconds(sequence)

        override fun periodicCost(sequence: Sequence): Int = inner.periodicCost(sequence)

        override val icon: ItemStack get() = ItemStack(Items.WRITTEN_BOOK)

        override fun description(sequence: Sequence): Component = Component.literal(
            "Записана чужа сила. Зникне одразу після використання.\n" +
                inner.description(sequence).string,
        )

        override fun cast(context: CastContext): AbilityOutcome {
            val outcome = inner.cast(context)
            // A failed cast does not spend the page: there is nothing to pay and nothing to erase.
            if (outcome is AbilityOutcome.Failed) return outcome
            context.beyonder.remove(identity)
            BeyonderStatus.pushRoster(context.caster)
            context.messaging.actionBar(
                context.caster,
                Component.literal("Сторінку витрачено: ${inner.displayName.string}"),
            )
            return outcome
        }
    }

    private companion object {
        const val RANGE = 15.0
        const val COOLDOWN_SECONDS = 60
        const val WATCH_TICKS = 10 * TICKS_PER_SECOND
        val FORBIDDEN = setOf(
            "door_record",
            "whitetower_analysis",
            "whitetower_spellcasting",
        )
    }
}
