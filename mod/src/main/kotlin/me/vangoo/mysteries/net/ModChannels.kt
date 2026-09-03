package me.vangoo.mysteries.net

import me.vangoo.mysteries.net.payload.AbilityActionPayload
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import me.vangoo.mysteries.net.payload.AbilityStatePayload
import me.vangoo.mysteries.net.payload.BeyonderStatusPayload
import me.vangoo.mysteries.net.payload.CastRequestPayload
import me.vangoo.mysteries.net.payload.StatusRequestPayload
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * The single place the mod's packets live in.
 *
 * The most common class of bug in mods — the server knows something the client does not —
 * grows out of ad-hoc messages scattered across features. Here declaring a channel is
 * registering it: [clientbound]/[serverbound] put the channel straight into [declared], and
 * [registerTypes] walks the list. Forgetting to register a channel declared here is impossible;
 * a channel assembled past this object will fail on its very first send — the vanilla registry
 * does not know its type.
 *
 * [registerTypes] is called from the main entrypoint, i.e. on both sides.
 */
object ModChannels {

    private val declared = mutableListOf<Channel<*>>()
    private var registered = false

    /** The server hands the Beyonder's state to the client. */
    val BEYONDER_STATUS: ClientboundChannel<BeyonderStatusPayload> =
        clientbound(BeyonderStatusPayload.TYPE, BeyonderStatusPayload.CODEC)

    /** The client asks the server to re-read the state. */
    val STATUS_REQUEST: ServerboundChannel<StatusRequestPayload> =
        serverbound(StatusRequestPayload.TYPE, StatusRequestPayload.CODEC)

    /** The cast key: the client pulled the trigger, the server picks the ability. */
    val CAST_REQUEST: ServerboundChannel<CastRequestPayload> =
        serverbound(CastRequestPayload.TYPE, CastRequestPayload.CODEC)

    /**
     * The rare snapshot: the ability list, their descriptions, costs and icons.
     * It flies only when the list itself changes — a handful of times per session.
     */
    val ABILITY_ROSTER: ClientboundChannel<AbilityRosterPayload> =
        clientbound(AbilityRosterPayload.TYPE, AbilityRosterPayload.CODEC)

    /** The frequent small snapshot: spirituality, cooldowns, toggles. No more than once a second. */
    val ABILITY_STATE: ClientboundChannel<AbilityStatePayload> =
        clientbound(AbilityStatePayload.TYPE, AbilityStatePayload.CODEC)

    /** One player intent: select, cast, select and cast, toggle. */
    val ABILITY_ACTION: ServerboundChannel<AbilityActionPayload> =
        serverbound(AbilityActionPayload.TYPE, AbilityActionPayload.CODEC)

    /** Every declared channel — for logs and tests. */
    val all: List<Channel<*>> get() = declared.toList()

    fun registerTypes() {
        check(!registered) { "Канали вже зареєстровані" }
        registered = true
        declared.forEach { it.registerType() }
    }

    private fun <T : CustomPacketPayload> clientbound(
        type: CustomPacketPayload.Type<T>,
        codec: StreamCodec<RegistryFriendlyByteBuf, T>,
    ): ClientboundChannel<T> = declare(ClientboundChannel(type, codec))

    private fun <T : CustomPacketPayload> serverbound(
        type: CustomPacketPayload.Type<T>,
        codec: StreamCodec<RegistryFriendlyByteBuf, T>,
    ): ServerboundChannel<T> = declare(ServerboundChannel(type, codec))

    /**
     * Identical ids are caught here, during class initialisation, rather than later in the
     * vanilla registry: a copy-pasted channel is exactly the mistake this object exists for.
     */
    private fun <C : Channel<*>> declare(channel: C): C {
        require(declared.none { it.id == channel.id }) { "Канал ${channel.id} уже оголошено" }
        declared += channel
        return channel
    }
}
