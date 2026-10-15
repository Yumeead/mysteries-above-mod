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
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
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

    /** A second offer on top of the first — a player has one session. */
    override fun canCast(context: CastContext): Boolean = !context.sessionRunning

    override fun cast(context: CastContext): AbilityOutcome {
        val other = context.targeting.playerLookingAt(RANGE)
            ?: return AbilityOutcome.failed("Немає кому пропонувати: наведіть погляд на гравця")
        if (other.uuid == context.caster.uuid) {
            return AbilityOutcome.failed("Із собою не домовляються")
        }

        val goods = context.caster.offhandItem.takeUnless { it.isEmpty }?.copy()
        if (goods != null) return offerOath(context, other, goods)

        val notary = context.caster.uuid
        val party = other.uuid
        val pending = context.cast
        val messaging = context.messaging
        val events = context.events
        val mote = ModParticles.mote(context.effects.pathwayColor)
        val peaceTicks = peaceSeconds(context.sequence) * TICKS_PER_SECOND

        messaging.tell(other, Component.literal("${context.caster.name.string} пропонує вам мир."))
        messaging.actionBar(party, Component.literal("Присядьте й подивіться на нього, щоб прийняти"))

        context.session {
            repeat(OFFER_SECONDS * TICKS_PER_SECOND / POLL_TICKS) {
                val self = owner ?: return@session
                val guest = self.level().server.playerList.getPlayer(party) ?: return@session
                scroll(self, guest, mote)

                if (bowsTo(guest, self)) {
                    // The domain already knows contracts — it is needed here exactly so that the
                    // deal has a name, a state and a term rather than being an anonymous subscription.
                    val contract = DomainContract.propose(notary, party, ContractTerm.PEACE, emptyMap())
                    contract.sign()

                    events.on<IncomingDamage>(durationTicks = peaceTicks) { damage ->
                        val victim = damage.victim.uuid
                        val attacker = damage.attacker?.uuid ?: return@on
                        if (!contract.involves(victim) || !contract.involves(attacker)) return@on
                        if (victim == attacker) return@on
                        damage.cancel()
                        messaging.actionBar(attacker, Component.literal("Сувій тримає: ви не можете його вдарити"))
                    }

                    pending.complete()
                    seal(self, guest)
                    val seconds = peaceTicks / TICKS_PER_SECOND
                    messaging.tell(notary, Component.literal("Сувій підписано: мир на $seconds с"))
                    messaging.tell(party, Component.literal("Сувій підписано: мир на $seconds с"))
                    return@session
                }
                waitTicks(POLL_TICKS)
            }
            // The ticket stayed unsettled — a refusal really does cost nothing.
            messaging.actionBar(notary, Component.literal("Сувій згорнувся: відповіді не було"))
        }

        return AbilityOutcome.Awaiting(Component.literal("Сувій розгорнуто перед ${other.name.string}"))
    }

    /** The same bow, a different scroll: the debtor swears to deliver [goods]. */
    private fun offerOath(context: CastContext, other: ServerPlayer, goods: ItemStack): AbilityOutcome {
        val notary = context.caster.uuid
        val party = other.uuid
        val pending = context.cast
        val messaging = context.messaging
        val mote = ModParticles.mote(context.effects.pathwayColor)
        val what = "${goods.count} × ${goods.hoverName.string}"

        messaging.tell(other, Component.literal("${context.caster.name.string} вимагає Клятви: $what за 3 дні."))
        messaging.actionBar(party, Component.literal("Присядьте й подивіться на нього, щоб поклястися"))

        context.session {
            repeat(OFFER_SECONDS * TICKS_PER_SECOND / POLL_TICKS) {
                val self = owner ?: return@session
                val guest = self.level().server.playerList.getPlayer(party) ?: return@session
                scroll(self, guest, mote)

                if (bowsTo(guest, self)) {
                    val sealed = Oaths.swear(self, guest, goods)
                    if (!self.inventory.add(sealed)) self.drop(sealed, false)
                    pending.complete()
                    seal(self, guest)
                    messaging.tell(notary, Component.literal("Клятву засвідчено: Золотий Сувій у вас"))
                    messaging.tell(
                        party,
                        Component.literal("Ви поклялися віддати $what. Щоб сплатити — присядьте й ПКМ по власнику сувою"),
                    )
                    return@session
                }
                waitTicks(POLL_TICKS)
            }
            messaging.actionBar(notary, Component.literal("Сувій згорнувся: відповіді не було"))
        }

        return AbilityOutcome.Awaiting(Component.literal("Клятва розгорнута перед ${other.name.string}"))
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

    /** The scroll between the two: a thread of motes while the offer is alive. */
    private fun scroll(notary: ServerPlayer, guest: ServerPlayer, mote: ParticleOptions) {
        val level = notary.level() as? ServerLevel ?: return
        val from = notary.eyePosition
        val to = guest.eyePosition
        val steps = (from.distanceTo(to) / STEP).toInt().coerceIn(1, MAX_STEPS)
        for (i in 0..steps) {
            val point: Vec3 = from.lerp(to, i.toDouble() / steps)
            level.sendParticles(mote, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.0)
        }
    }

    /** The seal — to both, and at one and the same moment. */
    private fun seal(notary: ServerPlayer, guest: ServerPlayer) {
        val level = notary.level() as? ServerLevel ?: return
        listOf(notary, guest).forEach { party ->
            level.sendParticles(
                net.minecraft.core.particles.ParticleTypes.END_ROD,
                party.x, party.y + party.bbHeight, party.z,
                18, 0.4, 0.3, 0.4, 0.04,
            )
        }
        level.playSound(
            null, notary.x, notary.y, notary.z,
            SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7f, 1.6f,
        )
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
        const val STEP = 0.4
        const val MAX_STEPS = 60
    }
}
