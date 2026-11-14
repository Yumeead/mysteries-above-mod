package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.contracts.ContractTerm
import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.effect.ward
import me.vangoo.mysteries.effect.worldText
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import me.vangoo.mysteries.pathways.sun.Oaths
import me.vangoo.domain.contracts.Contract as DomainContract

/**
 * Sun, Seq. 6 (Notary): **Magic Notarization**.
 *
 * The Notary offers a player peace — and the peace only takes effect when that player **bows
 * back**: sneaks and looks at the Notary for [OFFER_SECONDS] s. While the contract holds,
 * neither of the two can harm the other.
 *
 * ## A bow instead of a menu
 *
 * The plugin's `ContractMenu` is 437 lines: a chest with "Peace", "Trade", "Debt" icons, a second
 * page with a confirmation, a third with the item to trade. The menu existed there not because
 * the deal is complicated but because on Spigot there was no other way to ask "do you agree?".
 *
 * There is a way, and it has been in the game since day one: **two players look at each other and
 * sneak**. It is the same gesture Minecraft players have greeted each other with for twenty
 * years — and, unlike a chest, it is visible from the outside, pauses nobody and needs no client.
 *
 * ## The promise is kept rather than punished
 *
 * Peace does not "forbid" damage with a penalty for breaking it — it **cancels** it
 * (`IncomingDamage.cancel`). Breaking the contract is impossible, so no "BREACHED" state, no
 * punishment and no check are needed. The promise holds itself, and that is exactly what makes
 * it worth Seq. 6.
 *
 * ## The off hand chooses the term
 *
 * The plugin asked in a chest which deal to make. Here the deal is whatever the Notary holds out:
 * an empty off hand offers peace; goods in the off hand demand an **Oath** (`DEBT`) — that item,
 * that many, within three real days. The bow seals both alike. Everything after the signature —
 * the scroll, the payment, the breach — lives in `Oaths`, because a debt outlasts any session.
 *
 * ## The scroll hangs between them
 *
 * No line in the chat says what is offered: a scroll of light unrolls in the air between the two,
 * and its terms hang over it as words in the world, readable by whoever stands there — and fading
 * when the offer lapses. Signed, the scroll parts in two and each half flies to one of them.
 */
class Contract : ActiveAbility(AbilityIdentity.of("sun_contract")) {

    override val displayName: Component = Component.literal("Магічне засвідчення")

    override val spiritualityCost: Int = 100

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Пропонує гравцеві під прицілом Золотий Сувій: мир або Клятву.\n" +
            "▪ Він приймає, присівши й подивившись на вас протягом $OFFER_SECONDS с\n" +
            "▪ Порожня друга рука — мир: ${peaceSeconds(sequence)} с ви двоє не можете зашкодити одне одному\n" +
            "▪ Предмет у другій руці — Клятва: за 3 дні він віддасть стільки ж власникові сувою\n" +
            "▪ Сплата — присісти й ПКМ по власнику сувою; за прострочення карає Сонце",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        // A second offer on top of the first — a player has one session.
        if (context.sessionRunning) return AbilityOutcome.failed("Сувій уже розгорнуто")
        val other = context.targeting.playerLookingAt(RANGE)
            ?: return AbilityOutcome.failed("Немає кому пропонувати: наведіть погляд на гравця")
        if (other.uuid == context.caster.uuid) {
            return AbilityOutcome.failed("Із собою не домовляються")
        }

        val goods = context.caster.offhandItem.takeUnless { it.isEmpty }?.copy()
        val peaceSeconds = peaceSeconds(context.sequence)
        val party = other.uuid
        val notary = context.caster.uuid
        val pending = context.cast
        val messaging = context.messaging
        val scheduling = context.scheduling
        val events = context.events
        val color = context.effects.pathwayColor
        val light = ModParticles.light(color)

        // The terms are written on the scroll itself, between the two, and read by whoever stands there.
        val offer = if (goods == null) "Мир · $peaceSeconds с" else "Клятва · ${goods.count} × ${goods.hoverName.string} · 3 дні"
        val terms = Component.literal("$offer\nприйняти — присісти й подивитися на нотаріуса").withStyle(ChatFormatting.GOLD)
        val text = worldText(scheduling, context.level, textPoint(context.caster, other), terms, OFFER_SECONDS * TICKS_PER_SECOND)

        context.session {
            try {
                repeat(OFFER_SECONDS * TICKS_PER_SECOND / POLL_TICKS) { poll ->
                    val self = owner ?: return@session
                    val guest = self.level().server.playerList.getPlayer(party) ?: return@session
                    scroll(self, guest, light, poll)

                    if (bowsTo(guest, self)) {
                        val sealed = if (goods == null) {
                            // The domain already knows contracts — it is needed here exactly so that the
                            // deal has a name, a state and a term rather than being an anonymous subscription.
                            val contract = DomainContract.propose(notary, party, ContractTerm.PEACE, emptyMap())
                            contract.sign()
                            events.on<IncomingDamage>(durationTicks = peaceSeconds * TICKS_PER_SECOND) { damage ->
                                val victim = damage.victim.uuid
                                val attacker = damage.attacker?.uuid ?: return@on
                                if (!contract.involves(victim) || !contract.involves(attacker)) return@on
                                if (victim == attacker) return@on
                                damage.cancel()
                                // The scroll stands between them: the blow breaks on its light.
                                ward(damage.victim, color)
                                messaging.hint(attacker, Component.literal("Сувій тримає: ви не можете його вдарити"))
                            }
                            "Мир укладено · $peaceSeconds с"
                        } else {
                            val scroll = Oaths.swear(self, guest, goods)
                            if (!self.inventory.add(scroll)) self.drop(scroll, false)
                            "Клятву засвідчено\nсплата — присісти й ПКМ по власнику сувою"
                        }

                        pending.complete()
                        text?.discard()
                        split(self, guest, light)
                        worldText(
                            scheduling, self.level(), textPoint(self, guest),
                            Component.literal(sealed).withStyle(ChatFormatting.GOLD), SEALED_TEXT_TICKS,
                        )
                        return@session
                    }
                    waitTicks(POLL_TICKS)
                }
                // The ticket stayed unsettled — a refusal really does cost nothing.
                messaging.hint(notary, Component.literal("Сувій згорнувся: відповіді не було"))
            } finally {
                // Whatever ended the offer, its words go with it.
                text?.discard()
            }
        }

        return AbilityOutcome.Awaiting()
    }

    /**
     * The bow: sneaking and looking at the Notary.
     *
     * The gaze is checked with a dot product rather than "is it in the crosshair" — a bow is a
     * gesture, not a shot, and demanding aiming precision from the player here would be cruel.
     */
    private fun bowsTo(guest: ServerPlayer, notary: ServerPlayer): Boolean {
        if (!guest.isShiftKeyDown) return false
        val toNotary = notary.position().subtract(guest.position())
        if (toNotary.lengthSqr() > RANGE * RANGE) return false
        return guest.getViewVector(1.0f).dot(toNotary.normalize()) >= FACING_DOT
    }

    /** The middle of the scroll: halfway between the two, at eye height. */
    private fun middle(notary: ServerPlayer, guest: ServerPlayer): Vec3 =
        notary.eyePosition.add(guest.eyePosition).scale(0.5)

    private fun textPoint(notary: ServerPlayer, guest: ServerPlayer): Vec3 =
        middle(notary, guest).add(0.0, TEXT_LIFT, 0.0)

    /** Across the line between the two, level with the ground: the way the scroll's rods lie. */
    private fun across(notary: ServerPlayer, guest: ServerPlayer): Vec3 {
        val way = guest.position().subtract(notary.position())
        return Vec3(-way.z, 0.0, way.x).normalize().takeIf { it.lengthSqr() > 0 } ?: Vec3(1.0, 0.0, 0.0)
    }

    /**
     * The scroll between the two: two rods of light, one above the other, that part over the first
     * few polls — the scroll unrolling — and then hold open while the offer lives.
     */
    private fun scroll(notary: ServerPlayer, guest: ServerPlayer, light: ParticleOptions, poll: Int) {
        val level = notary.level()
        val centre = middle(notary, guest)
        val across = across(notary, guest)
        val open = SCROLL_HALF_HEIGHT * minOf(1.0, (poll + 1).toDouble() / UNROLL_POLLS)
        for (rod in listOf(open, -open)) {
            for (i in -ROD_POINTS..ROD_POINTS) {
                val at = centre.add(across.scale(SCROLL_HALF_WIDTH * i / ROD_POINTS)).add(0.0, rod, 0.0)
                level.sendParticles(light, at.x, at.y, at.z, 0, 0.0, 0.0, 0.0, 0.0)
            }
        }
    }

    /**
     * Signed: the scroll parts in two, and each half flies to one of the two — the seal reaches
     * both at one and the same moment, with a thud they both hear.
     */
    private fun split(notary: ServerPlayer, guest: ServerPlayer, light: ParticleOptions) {
        val level = notary.level()
        val centre = middle(notary, guest)
        val across = across(notary, guest)
        // The rods lie across the line between the two, so neither half is nearer to either party:
        // one simply goes to each.
        for ((side, party) in listOf(1 to notary, -1 to guest)) {
            val to = party.position().add(0.0, party.bbHeight * 0.6, 0.0)
            for (i in 0..ROD_POINTS) {
                for (rod in listOf(SCROLL_HALF_HEIGHT, -SCROLL_HALF_HEIGHT)) {
                    val at = centre.add(across.scale(side * SCROLL_HALF_WIDTH * i / ROD_POINTS)).add(0.0, rod, 0.0)
                    val flight = to.subtract(at).scale(1.0 / SPLIT_CARRY)
                    level.sendParticles(light, at.x, at.y, at.z, 0, flight.x, flight.y, flight.z, 1.0)
                }
            }
        }
        level.playSound(null, notary.x, notary.y, notary.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7f, 1.6f)
    }

    private fun peaceSeconds(sequence: Sequence): Int =
        scale(BASE_PEACE_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    private companion object {
        const val RANGE = 15.0
        const val COOLDOWN_SECONDS = 40
        const val OFFER_SECONDS = 15
        const val BASE_PEACE_SECONDS = 60
        const val POLL_TICKS = 4
        /** A ~66° cone: a bow, not an aim. */
        const val FACING_DOT = 0.4

        const val SCROLL_HALF_WIDTH = 0.45
        const val SCROLL_HALF_HEIGHT = 0.3
        const val ROD_POINTS = 5
        /** The scroll unrolls over the first three polls — about half a second. */
        const val UNROLL_POLLS = 3
        /** The light brakes as it flies and carries about seven times its speed: a half is sent at 1/7 of its way. */
        const val SPLIT_CARRY = 7.0
        const val TEXT_LIFT = 0.55
        const val SEALED_TEXT_TICKS = 4 * TICKS_PER_SECOND
    }
}
