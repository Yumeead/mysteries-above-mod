package me.vangoo.mysteries.net.payload

import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.modId
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamDecoder
import net.minecraft.network.codec.StreamMemberEncoder
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.world.item.ItemStack

/**
 * Server → client: the **rare** snapshot — what changes only together with the ability list.
 *
 * It flies on joining a world, a potion, an ascension, a demotion and `/mysteries forget` —
 * that is, a handful of times per session. That is exactly why the descriptions, costs and
 * icons travel here rather than in [AbilityStatePayload]: that one goes once a second, and
 * merging the two would mean sending the full description text of every ability every second.
 *
 * `roster == null` — the player is not a Beyonder. The pathway, the Sequence and the list are
 * either all there or all absent; the format cannot express "a list with no pathway" — the same
 * trick already used in [BeyonderStatusPayload].
 */
data class AbilityRosterPayload(val roster: Roster?) : CustomPacketPayload {

    data class Roster(
        /** The pathway name — the key to `PathwayBranding` and the menu heading. */
        val pathwayId: String,
        val sequence: Sequence,
        /** The canonical Sequence name, e.g. "Sailor". */
        val sequenceName: Component,
        val entries: List<Entry>,
    )

    /**
     * An ability as the menu draws it.
     *
     * The numbers are already computed **for the owner's Sequence**: the client has no
     * `SequenceScaler` and no reason to have one.
     *
     * Do not compare `Entry` by equality: [ItemStack] has no structural `equals`, so the
     * `equals` generated here would compare icons by reference. The tests check the fields
     * separately and the icon through `ItemStack.matches`.
     */
    data class Entry(
        val identity: String,
        val name: Component,
        val description: Component,
        val type: AbilityType,
        /** The one-off cast cost. */
        val cost: Int,
        /** The upkeep per second; 0 — nothing is held. */
        val upkeep: Int,
        /** The full cooldown length — the client computes the fill proportion from it. */
        val cooldownSeconds: Int,
        val icon: ItemStack,
    )

    override fun type(): CustomPacketPayload.Type<AbilityRosterPayload> = TYPE

    companion object {

        /** An empty roster: the player is no longer a Beyonder. */
        val NONE: AbilityRosterPayload = AbilityRosterPayload(null)

        val TYPE: CustomPacketPayload.Type<AbilityRosterPayload> =
            CustomPacketPayload.Type(modId("ability_roster"))

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, AbilityRosterPayload> =
            CustomPacketPayload.codec(
                StreamMemberEncoder { payload, buf -> write(payload, buf) },
                StreamDecoder { buf -> read(buf) },
            )

        private fun write(payload: AbilityRosterPayload, buf: RegistryFriendlyByteBuf) {
            val roster = payload.roster
            buf.writeBoolean(roster != null)
            if (roster == null) return

            buf.writeUtf(roster.pathwayId)
            buf.writeVarInt(roster.sequence.level)
            ComponentSerialization.STREAM_CODEC.encode(buf, roster.sequenceName)

            buf.writeVarInt(roster.entries.size)
            for (entry in roster.entries) {
                buf.writeUtf(entry.identity)
                ComponentSerialization.STREAM_CODEC.encode(buf, entry.name)
                ComponentSerialization.STREAM_CODEC.encode(buf, entry.description)
                buf.writeVarInt(entry.type.ordinal)
                buf.writeVarInt(entry.cost)
                buf.writeVarInt(entry.upkeep)
                buf.writeVarInt(entry.cooldownSeconds)
                ItemStack.STREAM_CODEC.encode(buf, entry.icon)
            }
        }

        private fun read(buf: RegistryFriendlyByteBuf): AbilityRosterPayload {
            if (!buf.readBoolean()) return NONE

            val pathwayId = buf.readUtf()
            val sequence = Sequence(buf.readVarInt())
            val sequenceName = ComponentSerialization.STREAM_CODEC.decode(buf)

            val entries = List(buf.readVarInt()) {
                Entry(
                    identity = buf.readUtf(),
                    name = ComponentSerialization.STREAM_CODEC.decode(buf),
                    description = ComponentSerialization.STREAM_CODEC.decode(buf),
                    type = abilityType(buf.readVarInt()),
                    cost = buf.readVarInt(),
                    upkeep = buf.readVarInt(),
                    cooldownSeconds = buf.readVarInt(),
                    icon = ItemStack.STREAM_CODEC.decode(buf),
                )
            }
            return AbilityRosterPayload(Roster(pathwayId, sequence, sequenceName, entries))
        }

        /** The type travels as an ordinal — both sides come from one jar; a foreign number is caught here. */
        private fun abilityType(ordinal: Int): AbilityType {
            val values = AbilityType.entries
            require(ordinal in values.indices) { "Невідомий тип здібності: $ordinal" }
            return values[ordinal]
        }
    }
}
