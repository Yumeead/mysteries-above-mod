package me.vangoo.mysteries.ability.context

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Text to a player. **The only way** — a direct `sendSystemMessage` is never called outside
 * this file and [me.vangoo.mysteries.ability.runtime.ServerMessaging].
 *
 * The first thing the service exists for is addressing **by UUID**: an ability's target is
 * often already offline or in another dimension, and every ability should not be searching all
 * the worlds for it separately. The rule started out softer ("calling directly is fine too"),
 * and that is exactly what ate it: phase 08 wrote half a dozen places shaped like
 * `level.server.playerList.getPlayer(id)?.sendSystemMessage(…)` — that is, this very service's
 * `find` rewritten by hand.
 *
 * The second is that this line will one day have to be walked: translation, muting during a
 * cast, logging in a test, a packet instead of chat. The phase 03 test ("a method gets into the
 * context only if it adds something beyond a single vanilla call") passes here not so much on
 * arithmetic as on this being a **point** rather than a scatter.
 *
 * Into a session and into an event handler goes **the service itself**, not the context:
 * `Messaging` holds nobody's identity and lives as long as the server. The same has already
 * been done with `scheduling` and `beyonders`.
 */
interface Messaging {

    fun tell(player: ServerPlayer, message: Component)

    fun actionBar(player: ServerPlayer, message: Component)

    /** Silently does nothing if the player is not online. */
    fun tell(playerId: UUID, message: Component)

    fun actionBar(playerId: UUID, message: Component)

    /**
     * Why a power did not happen — and **not** in the chat or the action bar.
     *
     * The world already shows the refusal: [fizzle] is drawn at the caster's hand by their own
     * client. The words are for the player who turned hints on in the client settings (they are
     * off by default), and for a vanilla client, which can draw no fizzle and so gets the line in
     * the action bar as before.
     */
    fun hint(player: ServerPlayer, message: Component, fizzle: Fizzle = Fizzle.NONE)

    fun hint(playerId: UUID, message: Component, fizzle: Fizzle = Fizzle.NONE)
}

/** What the caster's hand does when a power did not come. Only the caster sees it. */
enum class Fizzle {
    /** Nothing: the refusal is not about the power (not a Beyonder, nothing selected, a power that ran out). */
    NONE,

    /** Sparks gather at the hand and gutter out: the power was called and did not come. */
    SPARK,

    /** The same, and the spirituality flask shakes: there was nothing to draw it from. */
    EMPTY,

    /** A dull click and nothing more: the power is there, it is just not ready yet. */
    CLICK,
}
