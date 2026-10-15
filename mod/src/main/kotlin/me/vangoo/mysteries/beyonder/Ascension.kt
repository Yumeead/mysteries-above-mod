package me.vangoo.mysteries.beyonder

import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.item.PotionBrew
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects

/**
 * The potion is drunk — here is what comes of it.
 *
 * The descendant of the plugin's `PathwayPotionListener`, but split in two: [refusal] answers
 * **before** the bottle is raised to the lips, [ascend] acts **after**. In the plugin this was
 * one handler that cancelled the event halfway through; here there is nothing to cancel — an
 * item that cannot be drunk is never started on.
 *
 * The "may I" rules live in `core` ([Beyonder.canConsumePotion]); here there is only the wording
 * of the refusal and the consequences in the world.
 */
object Ascension {

    /** `null` — the potion may be drunk. Otherwise — why not, in Ukrainian. */
    fun refusal(player: ServerPlayer, brew: PotionBrew): Component? {
        val pathway = brew.resolvePathway()
            ?: return Component.literal("Шлях «${brew.pathway}» ще не існує в цьому світі")

        val beyonder = AbilityServices.beyonders.of(player.uuid)
            ?: return firstPotionRefusal(brew)

        return advancementRefusal(beyonder, pathway, brew.asSequence())
    }

    /**
     * Does what the potion grants the right to. Called only after [refusal] `== null` —
     * otherwise it fails, because that is a call-site bug, not a refusal to the player.
     */
    fun ascend(player: ServerPlayer, brew: PotionBrew) {
        val pathway = checkNotNull(brew.resolvePathway()) { "Зілля невідомого шляху: ${brew.pathway}" }
        val existing = AbilityServices.beyonders.of(player.uuid)

        if (existing == null) {
            initiate(player, pathway)
        } else {
            advance(player, existing, pathway)
        }

        // A stub pathway: the Sequence is real, the powers are not written yet — say so, or the
        // player hunts for an ability list that will never appear.
        if (!pathway.hasAnyAbility()) {
            AbilityServices.messaging.tell(
                player,
                Component.literal("Сили цього шляху ще не прокинулись у цьому світі — здібностей поки немає"),
            )
        }

        aftertaste(player)
        BeyonderStatus.push(player)
        // The ability list is different now — the roster flies exactly here and nowhere else.
        BeyonderStatus.pushRoster(player)
        AbilityServices.statePump.pushNow(player.uuid)
    }

    /**
     * Sets the pathway and the Sequence directly — an operator's shortcut to power.
     *
     * The ladder "hand out a potion → drink → top up the mastery → drink the next" is a dozen
     * commands and a dozen seconds per Sequence, and it has to be walked again after every edit
     * to an ability. Testing a mechanic must not cost that much; the potion chain itself stays
     * where it is and remains the only path for a player.
     *
     * The previous state is **removed entirely** — through [forget], not by replacing the
     * registry entry: otherwise the sessions, subscriptions and cooldowns of the old pathway
     * would remain on the new one.
     */
    fun bestow(player: ServerPlayer, pathway: Pathway, sequence: Sequence) {
        if (AbilityServices.beyonders.of(player.uuid) != null) forget(player)

        AbilityServices.beyonders.register(Beyonder(player.uuid, sequence, pathway))
        AbilityServices.passives.activate(player)

        BeyonderStatus.push(player)
        BeyonderStatus.pushRoster(player)
        AbilityServices.statePump.pushNow(player.uuid)
    }

    /** The player is no longer a Beyonder: everything the mod held on them is removed. */
    fun forget(player: ServerPlayer) {
        AbilityServices.passives.deactivate(player)
        AbilityServices.passives.forget(player.uuid)
        // `passives.deactivate` only removes the passives' subscriptions; whatever an active
        // ability subscribed to in `cast` would stay alive on someone who is no longer a Beyonder.
        AbilityServices.events.forget(player.uuid)
        AbilityServices.scheduling.stopAll(player.uuid)
        AbilityServices.cooldowns.forget(player.uuid)
        AbilityServices.selection.forget(player.uuid)
        AbilityServices.beyonders.forget(player.uuid)
        AbilityServices.statePump.forget(player.uuid)
        BeyonderStatus.push(player)
        // An empty roster **is** the message "you are no longer a Beyonder": no state flies
        // after it, because state only makes sense for someone who is one.
        BeyonderStatus.pushRoster(player)
    }

    // ── The first potion ────────────────────────────────────────────────────────

    private fun firstPotionRefusal(brew: PotionBrew): Component? =
        if (brew.sequence == STARTING_SEQUENCE) {
            null
        } else {
            Component.literal("Краще почати з Послідовності $STARTING_SEQUENCE…")
        }

    private fun initiate(player: ServerPlayer, pathway: Pathway) {
        val beyonder = Beyonder(player.uuid, Sequence.starter(), pathway)
        AbilityServices.beyonders.register(beyonder)
        AbilityServices.passives.activate(player)

        AbilityServices.messaging.tell(
            player,
            Component.literal("Вітаємо у світі Потойбічних, ${player.name.string}"),
        )
        AbilityServices.messaging.tell(
            player,
            Component.literal(
                "Шлях: ${pathway.name} · Посл. ${beyonder.sequenceLevel} " +
                    "(${pathway.sequenceName(beyonder.sequenceLevel)})",
            ),
        )
    }

    // ── Ascension ───────────────────────────────────────────────────────────────

    /**
     * Breaks `canConsumePotion` down into three separate reasons.
     *
     * In the plugin the refusal was one for every case — "Ви не можете споживати цей еліксір!"
     * — and the player never learned what exactly was wrong: the wrong group, the wrong
     * Sequence, or mastery that had not been topped up.
     */
    private fun advancementRefusal(
        beyonder: Beyonder,
        pathway: Pathway,
        sequence: Sequence,
    ): Component? {
        if (pathway.group != beyonder.pathway.group) {
            return Component.literal(
                "Це зілля групи «${pathway.group.displayName}», а ваш шлях — " +
                    "«${beyonder.pathway.group.displayName}»",
            )
        }
        if (beyonder.sequence.level != sequence.level + 1) {
            return Component.literal(
                "Наступна ваша Послідовність — ${beyonder.sequence.level - 1}, а не ${sequence.level}",
            )
        }
        if (!beyonder.mastery.canAdvance()) {
            return Component.literal("Засвоєння ще не повне: ${beyonder.mastery}")
        }
        // `canConsumePotion` does not know this, while `advanceWithPathwayChange` demands it,
        // and without a check here a sip of another pathway at Seq. 8 would fail a `check()`
        // inside the domain. The identity settles at the very first ascension; beyond that the
        // pathway is already one's own.
        if (pathway.name != beyonder.pathway.name &&
            beyonder.sequence.level < Beyonder.PATHWAY_CHANGE_MIN_SEQUENCE
        ) {
            return Component.literal(
                "Змінити шлях можна лише з Послідовності ${Beyonder.PATHWAY_CHANGE_MIN_SEQUENCE}",
            )
        }
        return null
    }

    private fun advance(player: ServerPlayer, beyonder: Beyonder, pathway: Pathway) {
        val changingPathway = pathway.name != beyonder.pathway.name
        if (changingPathway) beyonder.advanceWithPathwayChange(pathway) else beyonder.advance()

        // The ability list is different now — the permanent passives of the new Sequence must be
        // activated, the lost ones said goodbye to, and the toggles switched off.
        AbilityServices.passives.refresh(player)
        AbilityServices.selection.forget(player.uuid)

        AbilityServices.messaging.tell(
            player,
            Component.literal(
                "Ви піднеслись до Послідовності ${beyonder.sequenceLevel} — " +
                    "${pathway.sequenceName(beyonder.sequenceLevel)}",
            ),
        )
        if (changingPathway) {
            AbilityServices.messaging.tell(player, Component.literal("Ваш шлях відтепер — ${pathway.name}"))
        }
    }

    // ── Consequences in the world ───────────────────────────────────────────────

    /** The potion breaks the body before it grants power — blindness, nausea and a portal flash. */
    private fun aftertaste(player: ServerPlayer) {
        player.addEffect(MobEffectInstance(MobEffects.BLINDNESS, 100, 0))
        player.addEffect(MobEffectInstance(MobEffects.NAUSEA, 200, 1))

        val level = player.level()
        level.playSound(
            null, player.x, player.y, player.z,
            SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.8f,
        )
        level.sendParticles(
            ParticleTypes.PORTAL,
            player.x, player.y + 1.0, player.z,
            50, 0.5, 1.0, 0.5, 0.3,
        )
    }

    /** The first potion is always Seq. 9: an unprepared body simply cannot hold a stronger one. */
    private const val STARTING_SEQUENCE = 9
}
