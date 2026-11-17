package me.vangoo.mysteries.effect

import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.world.entity.Entity

/** Which eyes burn in a player's face: each is a texture of four pixels, drawn the way a spider's are. */
enum class EyeGlow(val texture: String) {
    /** The two suns of the Sun's Night Vision. */
    SUN("sun_eyes"),

    /** The Arbiter's stare pressing down. */
    GOLD("law_eyes_gold"),

    /** Bone-white: the Interrogator's piercing, held behind the eyes. */
    WHITE("law_eyes_white"),
}

/**
 * Whose eyes burn and how, for everyone who sees them. Synced to every client that tracks the player, never
 * written to disk. One slot: a body has one pair of eyes, and the last power to light them wins.
 */
object Eyes {

    val GLOW: AttachmentType<EyeGlow> by lazy {
        AttachmentRegistry.create(modId("eyes")) {
            it.syncWith(
                ByteBufCodecs.VAR_INT.map({ id -> EyeGlow.entries[id.coerceIn(0, EyeGlow.entries.lastIndex)] }, { glow -> glow.ordinal }),
                AttachmentSyncPredicate.all(),
            )
        }
    }

    /** An attachment unknown when a player loads is dropped from them, so it is created during init. */
    fun install() {
        GLOW
    }

    /** Lights [entity]'s eyes — a no-op if they already burn so, so it may be asked every tick. */
    fun light(entity: Entity, glow: EyeGlow) {
        if (entity.getAttached(GLOW) != glow) entity.setAttached(GLOW, glow)
    }

    fun dim(entity: Entity) {
        entity.removeAttached(GLOW)
    }
}
