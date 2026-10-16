package me.vangoo.mysteries.beyonder

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.entities.Pathway
import me.vangoo.mysteries.ability.Ability
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import me.vangoo.mysteries.net.payload.AbilityStatePayload
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.DyedItemColor

/**
 * The Beyonder's state as the client sees it.
 *
 * The source is the live registry. An empty registry means the player is still ordinary, and
 * that is the same answer the screen already knows how to show. Storage (phase 09) fills the
 * registry when the player joins; until then, the potion does.
 *
 * **A pure snapshot builder.** [AbilityStatePump] decides which snapshot flies when; here there
 * is only "assemble and hand over". Two snapshots rather than one, because their frequency
 * differs by three orders of magnitude: [roster] a handful of times per session, [state] up to
 * once a second. Merging them would mean sending ability descriptions every second.
 */
object BeyonderStatus {

    // ── Snapshots ───────────────────────────────────────────────────────────────

    /**
     * The body a player is wearing, if any — set by the Marionettist's plumbing at install.
     *
     * A lambda rather than a call into `pathways/fool`: the roster belongs to every Beyonder, and
     * possession is one pathway's business. The same seam `PassiveTicker` uses for toggle state.
     */
    var wornBody: (java.util.UUID) -> AbilityRosterPayload.Worn? = { null }

    /**
     * The ability list with everything the menu needs. The numbers are already computed for the
     * owner's Sequence — the client has no `SequenceScaler` and no reason to have one.
     *
     * An `AbilitySpec` that is not an [Ability] never gets here: it has neither a name nor an
     * icon, and skipping it silently is more honest than an empty card. There are none such in
     * the mod anyway — everything the pathways register inherits [Ability].
     *
     * It takes a [Beyonder] rather than a player deliberately: nothing in the ability list needs
     * a live world, so the snapshot is checked by an ordinary unit test.
     * `null` — the player is not a Beyonder.
     */
    fun roster(beyonder: Beyonder?): AbilityRosterPayload {
        if (beyonder == null) return AbilityRosterPayload.NONE
        val pathway = beyonder.pathway

        return AbilityRosterPayload(
            AbilityRosterPayload.Roster(
                pathwayId = pathway.name,
                sequence = beyonder.sequence,
                sequenceName = Component.literal(pathway.sequenceName(beyonder.sequenceLevel)),
                entries = beyonder.abilities
                    .filterIsInstance<Ability>()
                    .map { entryOf(it, beyonder) },
                worn = wornBody(beyonder.playerId),
            ),
        )
    }

    /** `null` — the player is not a Beyonder; the empty [roster] has already said so. */
    fun state(player: ServerPlayer): AbilityStatePayload? {
        val beyonder = AbilityServices.beyonders.of(player.uuid) ?: return null
        val owner = player.uuid

        return AbilityStatePayload(
            serverTick = player.level().server.tickCount,
            spirituality = beyonder.spirituality,
            sanityLoss = beyonder.sanityLoss,
            selected = AbilityServices.selection.of(owner)?.id,
            live = beyonder.abilities.mapNotNull { spec ->
                val readyAtTick = AbilityServices.cooldowns.readyAtTick(owner, spec.identity)
                val toggled = AbilityServices.passives.isEnabled(owner, spec.identity)
                val sustaining = AbilityServices.scheduling.isRunning(owner, spec.identity)
                val mode = (spec as? Ability)?.let { AbilityServices.modes.of(owner, it, beyonder.sequence) }?.id
                // Ready, switched off, not running and one-way — there is nothing to say about it.
                if (readyAtTick == 0 && !toggled && !sustaining && mode == null) return@mapNotNull null
                AbilityStatePayload.Live(spec.identity.id, readyAtTick, toggled, sustaining, mode)
            },
            mastery = beyonder.mastery,
        )
    }

    // ── Sending ─────────────────────────────────────────────────────────────────

    /** The ability list changed: a potion, an ascension, a demotion, joining a world, `forget`. */
    fun pushRoster(player: ServerPlayer) {
        ModChannels.ABILITY_ROSTER.sendTo(listOf(player), roster(AbilityServices.beyonders.of(player.uuid)))
    }

    /** Nothing flies to an ordinary player: state only makes sense for a Beyonder. */
    fun pushState(player: ServerPlayer) {
        val state = state(player) ?: return
        ModChannels.ABILITY_STATE.sendTo(listOf(player), state)
    }

    // ── Assembling a card ───────────────────────────────────────────────────────

    private fun entryOf(ability: Ability, beyonder: Beyonder): AbilityRosterPayload.Entry {
        val sequence = beyonder.sequence
        return AbilityRosterPayload.Entry(
            identity = ability.identity.id,
            name = ability.displayName,
            description = ability.description(sequence),
            type = ability.type,
            cost = ability.spiritualityCost,
            upkeep = ability.periodicCost(sequence),
            cooldownSeconds = ability.cooldownSeconds(sequence),
            icon = tinted(ability.icon, beyonder.pathway.name),
            modes = ability.modes(sequence).map { AbilityRosterPayload.Mode(it.id, it.name) },
            origin = originOf(ability, beyonder),
        )
    }

    /**
     * The Sequence a power first appears at on the owner's pathway — Seq. 9 for one carried up
     * from the start, even if a later Sequence replaced it with a stronger version. A stolen or
     * lent power is not on the pathway at all and has no origin.
     */
    private fun originOf(ability: Ability, beyonder: Beyonder): AbilityRosterPayload.Origin? {
        val pathway = beyonder.pathway
        val level = (Pathway.SEQUENCE_COUNT - 1 downTo 0).firstOrNull { level ->
            pathway.abilitiesFor(level).any { it.identity == ability.identity }
        } ?: return null
        return AbilityRosterPayload.Origin(level, pathway.sequenceName(level))
    }

    /**
     * Dyes the icon in the pathway colour — for free, with a vanilla component.
     *
     * An ability that declared its own `DYED_COLOR` keeps it: an override must be stronger than
     * a default, otherwise it is worth nothing.
     *
     * The copy is deliberate. A default icon is built anew each time, but an overridden one may
     * be a field — and then the dye would settle on a shared instance forever.
     */
    private fun tinted(icon: ItemStack, pathwayName: String): ItemStack {
        if (icon.has(DataComponents.DYED_COLOR)) return icon
        val tinted = icon.copy()
        tinted.set(DataComponents.DYED_COLOR, DyedItemColor(PathwayBranding.liquidOf(pathwayName).packed))
        return tinted
    }
}
