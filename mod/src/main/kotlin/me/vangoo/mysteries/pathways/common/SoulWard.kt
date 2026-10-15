package me.vangoo.mysteries.pathways.common

import java.util.UUID

/**
 * A soul that is not where it seems to be: the Spirit Usher's **Spirit Swap** (Death, Seq. 6)
 * trades places with a servant, and for a while anything aimed at their soul body finds a corpse.
 *
 * It lives in `common` because its readers are on two pathways — Death's own `LanguageOfTheDead`
 * has no soul to tear out, and Fool's `MarionetteControl` has no soul to tie a thread to. The
 * plugin used an entity tag for the same reason, and removed it with a scheduled task.
 *
 * Here it is a deadline in memory on the mod's own clock, never a tag: a tag is written into the
 * player's file, and a server stopped between the swap and the removal would have left a
 * permanent soul ward — not a trick any more, but immortality against half of two pathways.
 */
object SoulWard {

    private val until = HashMap<UUID, Long>()

    fun protect(id: UUID, untilTick: Long) {
        until[id] = untilTick
    }

    fun isWarded(id: UUID, now: Long): Boolean {
        val deadline = until[id] ?: return false
        if (now < deadline) return true
        until.remove(id)
        return false
    }

    fun reset() {
        until.clear()
    }
}
