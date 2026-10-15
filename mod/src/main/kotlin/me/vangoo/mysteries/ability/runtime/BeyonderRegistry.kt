package me.vangoo.mysteries.ability.runtime

import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.mysteries.ability.context.Beyonders
import java.util.UUID

/**
 * The Beyonders currently in the game.
 *
 * Only those in the game: `BeyonderStore` fills the registry on join from the player's save and
 * takes them out again on leave.
 */
class BeyonderRegistry(
    /**
     * The toggles' state. As a lambda rather than a reference to `PassiveTicker`: that one is
     * built **after** the registry (it needs the contexts, which need the registry), so a direct
     * field would close an initialisation cycle.
     */
    private val toggles: (UUID, AbilityIdentity) -> Boolean = { _, _ -> false },
) : Beyonders {

    private val byId = LinkedHashMap<UUID, Beyonder>()

    override fun of(id: UUID): Beyonder? = byId[id]

    override fun isToggled(id: UUID, ability: AbilityIdentity): Boolean = toggles(id, ability)

    override val online: Collection<Beyonder> get() = byId.values

    fun register(beyonder: Beyonder) {
        byId[beyonder.playerId] = beyonder
    }

    fun forget(id: UUID): Beyonder? = byId.remove(id)
}
