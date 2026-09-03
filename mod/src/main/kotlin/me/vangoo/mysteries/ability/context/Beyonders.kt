package me.vangoo.mysteries.ability.context

import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.AbilityIdentity
import net.minecraft.world.entity.Entity
import java.util.UUID

/**
 * Who among those present is a Beyonder, and which one.
 *
 * The registry is live: the domain [Beyonder] exists only while the player is in the game, and
 * outside it storage holds them (phase 09). Abilities come here for someone else's state —
 * their own already lies in the context.
 */
interface Beyonders {

    fun of(id: UUID): Beyonder?

    fun of(entity: Entity): Beyonder? = of(entity.uuid)

    fun isBeyonder(id: UUID): Boolean = of(id) != null

    /**
     * Whether the player [id] is holding the toggle [ability] on **right now**.
     *
     * Needed because a toggle can be a **shield**: the Door's `AntiDivination` does nothing on
     * its own — it merely makes someone else's divination fail. Without this query the
     * resistance would have to be dressed up as an event published by everyone who divines and
     * subscribed to by the shield — that is, two points instead of one, and neither of them
     * simpler.
     *
     * The toggle registry lives in `PassiveTicker`; it arrives here as a lambda, because that
     * one is built later — it needs the context itself.
     */
    fun isToggled(id: UUID, ability: AbilityIdentity): Boolean

    val online: Collection<Beyonder>
}
