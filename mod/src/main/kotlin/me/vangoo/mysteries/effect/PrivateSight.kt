package me.vangoo.mysteries.effect

import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/*
 * What a power shows to its caster alone.
 *
 * [worldText] and vanilla `GLOWING` are shared by everyone who stands near: right for a contract
 * read aloud, wrong for an omen or for what a Seer's eyes pick out of a crowd. These two go down one
 * connection and nowhere else, and the client draws them itself.
 */

/**
 * Words only [viewer] sees, hanging at [at] for [durationTicks] and fading — or riding over the head
 * of [anchor], if given, wherever it goes. [color] tints whatever in [text] has no colour of its own.
 */
fun privateText(
    viewer: ServerPlayer,
    at: Vec3,
    text: Component,
    durationTicks: Int,
    color: RgbColor = WORDS_WHITE,
    anchor: Entity? = null,
) {
    require(durationTicks > 0) { "Тривалість тексту мусить бути додатною: $durationTicks" }
    // A vanilla client has no such channel: the list form asks before it sends.
    ModChannels.WORLD_EFFECT.sendTo(
        listOf(viewer),
        WorldEffectPayload.Words(at, anchor?.id ?: WorldEffectPayload.NO_ANCHOR, text, color.packed, durationTicks),
    )
}

/** [entities] outlined for [viewer] alone, for [durationTicks]. Nobody else sees the outline. */
fun privateGlow(viewer: ServerPlayer, entities: Collection<Entity>, durationTicks: Int) {
    if (entities.isEmpty()) return
    require(durationTicks > 0) { "Тривалість обрису мусить бути додатною: $durationTicks" }
    // A vanilla client has no such channel: the list form asks before it sends.
    ModChannels.WORLD_EFFECT.sendTo(
        listOf(viewer),
        WorldEffectPayload.Glow(entities.take(WorldEffectPayload.MAX_GLOW).map { it.id }, durationTicks),
    )
}

/**
 * [sound] played to [listener] alone, from [at]: it comes from where the thing is, so it says the
 * direction without a word — and nobody standing beside them hears it.
 */
fun privateSound(listener: ServerPlayer, at: Vec3, sound: SoundEvent, volume: Float = 1f, pitch: Float = 1f) {
    listener.connection.send(
        ClientboundSoundPacket(
            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS,
            at.x, at.y, at.z, volume, pitch, listener.random.nextLong(),
        ),
    )
}

private val WORDS_WHITE = RgbColor.of(236, 236, 244)
