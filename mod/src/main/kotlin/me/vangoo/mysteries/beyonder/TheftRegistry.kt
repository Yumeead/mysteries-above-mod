package me.vangoo.mysteries.beyonder

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.vangoo.domain.valueobjects.AbilityIdentity
import net.minecraft.core.UUIDUtil
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Who took whose power, and until when.
 *
 * ## The clock here is the wall, not the tick — and that is the one rule of this file
 *
 * Everywhere else in the mod the clock is the server tick: sessions, subscriptions, the
 * evidence log all freeze when a single-player game is paused, and rightly so. Theft is the
 * exception, and deliberately: ten minutes of stolen power means **ten minutes of real time**.
 * A countdown that pauses while the thief is offline would turn a loan into a possession —
 * log out, come back tomorrow, still holding someone else's power.
 *
 * Hence absolute stamps ([Theft.expiresAt], [Theft.suppressionEndsAt]) and never a remaining
 * count. A remaining count is exactly the thing that stops when the server does.
 *
 * ## One slot, and the record knows its own terms
 *
 * A thief holds one theft at a time. The terms travel **inside the record** rather than as
 * constants here, because the tiers differ — Prometheus takes for ten minutes, the Dream
 * Stealer for half an hour — and the registry has no business knowing Error's balance numbers.
 *
 * ## Four points of enforcement, and no others
 *
 * | | where |
 * |---|---|
 * | the theft | `PowerTheft` → [steal] |
 * | sweeping the expired | `AbilityServices`, once every ten seconds → [sweep] |
 * | the victim cannot cast it | one `if` in `AbilityExecutor` → [isSuppressed] |
 * | the first call of what was stolen | the same method, unconditionally → [markUsed] |
 *
 * A theft spent early frees the slot through [release], but that is the holder's own business
 * rather than a point of enforcement.
 *
 * Sessions and toggles the victim had already started keep running: the `if` gates **new**
 * casts only. That is the correct behaviour by construction, not by extra code.
 *
 * ## Saved with the world
 *
 * [CODEC] writes the whole registry; `BeyonderStore` keeps it on the overworld. The stolen power
 * itself is never saved with the thief — it is handed back on join while the record still holds
 * it, so a theft can only ever end by its own clock, never by being written down twice.
 */
object TheftRegistry {

    /**
     * @property expiresAt when the thief loses the power
     * @property suppressionEndsAt when the victim gets it back — always later
     */
    class Theft(
        val thiefId: UUID,
        val victimId: UUID,
        val identity: AbilityIdentity,
        val stolenAt: Long,
        expiresAt: Long,
        val suppressionEndsAt: Long,
        /** How long the power lives once it is first used; `0` — there is no reserve tier. */
        val activeHold: Long = 0,
    ) {
        /**
         * When the thief loses the power.
         *
         * Not a `val`: at the Dream Stealer's tier an unused power waits on a long reserve term
         * and the **first cast** re-reckons the ownership. Which is why the record carries its own
         * terms — nobody outside Error should need to know its balance numbers.
         */
        var expiresAt: Long = expiresAt
            internal set

        var firstUsedAt: Long? = null
            internal set

        internal fun use(now: Long) {
            if (activeHold <= 0 || firstUsedAt != null) return
            firstUsedAt = now
            expiresAt = minOf(expiresAt, now + activeHold)
        }

        /** The power is gone from the thief, but the victim is still without it. */
        var revoked: Boolean = false
            internal set
    }

    private val byThief = ConcurrentHashMap<UUID, Theft>()

    val CODEC: Codec<List<Theft>> = RecordCodecBuilder.create<Theft> { row ->
        row.group(
            UUIDUtil.CODEC.fieldOf("thief").forGetter(Theft::thiefId),
            UUIDUtil.CODEC.fieldOf("victim").forGetter(Theft::victimId),
            Codec.STRING.fieldOf("identity").forGetter { it.identity.id },
            Codec.LONG.fieldOf("stolen_at").forGetter(Theft::stolenAt),
            Codec.LONG.fieldOf("expires_at").forGetter(Theft::expiresAt),
            Codec.LONG.fieldOf("suppression_ends_at").forGetter(Theft::suppressionEndsAt),
            Codec.LONG.fieldOf("active_hold").forGetter(Theft::activeHold),
            Codec.LONG.optionalFieldOf("first_used_at").forGetter { Optional.ofNullable(it.firstUsedAt) },
            Codec.BOOL.fieldOf("revoked").forGetter(Theft::revoked),
        ).apply(row) { thief, victim, identity, stolenAt, expiresAt, suppressionEndsAt, activeHold, firstUsedAt, revoked ->
            Theft(thief, victim, AbilityIdentity.of(identity), stolenAt, expiresAt, suppressionEndsAt, activeHold).also {
                it.firstUsedAt = firstUsedAt.orElse(null)
                it.revoked = revoked
            }
        }
    }.listOf()

    fun snapshot(): List<Theft> = byThief.values.toList()

    /** A fresh server: the saved records replace whatever the last one left. */
    fun restore(thefts: List<Theft>) {
        byThief.clear()
        thefts.forEach { byThief[it.thiefId] = it }
    }

    /** Whether this thief's single slot is taken. */
    fun heldBy(thiefId: UUID): Theft? = byThief[thiefId]

    /**
     * Records a theft, replacing whatever the thief held before — the slot is one.
     *
     * @param hold how long the thief keeps the power
     * @param suppression how long the victim goes without it; must outlast [hold]
     */
    fun steal(
        thiefId: UUID,
        victimId: UUID,
        identity: AbilityIdentity,
        hold: Long,
        suppression: Long,
        now: Long = System.currentTimeMillis(),
        activeHold: Long = 0,
    ): Theft {
        require(suppression >= hold) { "Пригнічення жертви не може бути коротшим за володіння: $suppression < $hold" }
        val theft = Theft(thiefId, victimId, identity, now, now + hold, now + suppression, activeHold)
        byThief[thiefId] = theft
        return theft
    }

    /** The victim cannot call this power of theirs — someone is holding it. */
    fun isSuppressed(
        victimId: UUID,
        identity: AbilityIdentity,
        now: Long = System.currentTimeMillis(),
    ): Boolean = byThief.values.any {
        it.victimId == victimId && it.identity == identity && now < it.suppressionEndsAt
    }

    /** How long the victim still waits, in seconds; zero when nothing holds them. */
    fun suppressionSecondsLeft(
        victimId: UUID,
        identity: AbilityIdentity,
        now: Long = System.currentTimeMillis(),
    ): Long = byThief.values
        .filter { it.victimId == victimId && it.identity == identity && now < it.suppressionEndsAt }
        .maxOfOrNull { (it.suppressionEndsAt - now) / 1000 }
        ?: 0

    /**
     * The stolen power was called for the first time.
     *
     * The gate is entirely inside the record — the wrong thief, the wrong power, a second call or
     * a tier with no reserve all leave without doing anything — so `AbilityExecutor` can make this
     * call unconditionally and know nothing about Error's terms.
     */
    fun markUsed(thiefId: UUID, identity: AbilityIdentity, now: Long = System.currentTimeMillis()) {
        val theft = byThief[thiefId] ?: return
        if (theft.identity == identity) theft.use(now)
    }

    /** The theft was spent early: the slot frees, and so does the victim. */
    fun release(thiefId: UUID): Theft? = byThief.remove(thiefId)

    /**
     * Takes back what has run out.
     *
     * Two stages in one pass, and they are not the same moment: the power leaves the thief at
     * [Theft.expiresAt], while the record lives on until the victim's window closes — otherwise
     * the victim would get their power back the instant the thief lost it, and the theft would
     * cost its target nothing.
     */
    fun sweep(now: Long = System.currentTimeMillis(), revoke: (Theft) -> Unit) {
        byThief.values.toList().forEach { theft ->
            if (!theft.revoked && now >= theft.expiresAt) {
                theft.revoked = true
                revoke(theft)
            }
            if (now >= theft.suppressionEndsAt) byThief.remove(theft.thiefId, theft)
        }
    }

    /** Server shutdown. */
    fun reset() {
        byThief.clear()
    }
}
