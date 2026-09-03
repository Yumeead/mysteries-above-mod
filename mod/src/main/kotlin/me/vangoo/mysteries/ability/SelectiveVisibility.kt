package me.vangoo.mysteries.ability

import com.mojang.datafixers.util.Pair
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity

/**
 * Hides one entity from exactly one viewer — everyone else keeps seeing it normally.
 *
 * ## Why not `MobEffects.INVISIBILITY`
 *
 * The vanilla effect is the mod's default answer to "make someone unseen" (see Door's `Flash`),
 * and it stays that where the plugin's own mechanic wanted the *caster* imperceptible to
 * *everyone*. `BattleHypnotism` wants the opposite shape: one specific enemy loses the caster
 * from their senses while the rest of the world keeps seeing them fight. Vanilla has no lever
 * for that — invisibility is global, not per-viewer — so this exists instead of stretching the
 * effect to a case it was never for.
 *
 * ## Why packets and not a mixin
 *
 * Both packets this needs are public vanilla constructors reachable from server code as-is:
 * [ClientboundRemoveEntitiesPacket] to desync the target's client from the tracked entity, and
 * [ClientboundAddEntityPacket] plus its data and equipment to resync it. No hook is missing and
 * nothing needs redirecting — the search that ends in a mixin elsewhere in the mod does not even
 * start here.
 *
 * ## The cost of going around the tracker
 *
 * The server-side entity tracker (`ChunkMap`) never learns that [viewer] stopped seeing
 * [entity]: as far as it knows, tracking never changed, so it will not resend a spawn packet on
 * its own. That is exactly why [show] rebuilds the spawn sequence by hand instead of waiting for
 * the tracker to notice — nothing else ever will. Ordinary per-tick updates (position, motion)
 * that arrive for an entity ID the viewer's client has forgotten are simply dropped client-side:
 * silent, not an error.
 */
object SelectiveVisibility {

    /** [viewer] stops seeing [entity] until [show] runs. */
    fun hide(entity: LivingEntity, viewer: ServerPlayer) {
        viewer.connection.send(ClientboundRemoveEntitiesPacket(entity.id))
    }

    /** Resyncs [entity] onto [viewer]'s client: spawn, synced data, and equipment, in that order. */
    fun show(entity: LivingEntity, viewer: ServerPlayer) {
        if (!viewer.connection.isAcceptingMessages) return

        viewer.connection.send(
            ClientboundAddEntityPacket(
                entity.id,
                entity.uuid,
                entity.x,
                entity.y,
                entity.z,
                entity.xRot,
                entity.yRot,
                entity.type,
                0,
                entity.deltaMovement,
                entity.yHeadRot.toDouble(),
            ),
        )
        viewer.connection.send(
            ClientboundSetEntityDataPacket(entity.id, entity.entityData.nonDefaultValues ?: emptyList()),
        )
        val equipment = EquipmentSlot.entries.map { slot -> Pair.of(slot, entity.getItemBySlot(slot)) }
        viewer.connection.send(ClientboundSetEquipmentPacket(entity.id, equipment))
    }
}
