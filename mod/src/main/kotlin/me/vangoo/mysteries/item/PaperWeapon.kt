package me.vangoo.mysteries.item

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.vangoo.domain.valueobjects.PaperWeaponType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/**
 * What a paper weapon (Fool, Seq. 7) actually is, riding on an otherwise ordinary vanilla item
 * (a blaze rod, a stick, a brick — the same materials the plugin used as menu icons, now the
 * item itself instead of a GUI selection). Two stacks with different [usesLeft] carry different
 * component data and so never merge on their own — no UUID stamp needed the way the plugin's NBT
 * approach required just to keep two bats from becoming one stack.
 */
data class PaperWeapon(val type: String, val usesLeft: Int) {

    init {
        require(usesLeft >= 0) { "Заряд паперової зброї не може бути від'ємним: $usesLeft" }
    }

    fun asType(): PaperWeaponType? = runCatching { PaperWeaponType.valueOf(type) }.getOrNull()

    fun degraded(): PaperWeapon = copy(usesLeft = usesLeft - 1)

    companion object {
        val CODEC: Codec<PaperWeapon> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("type").forGetter { it.type },
                Codec.intRange(0, 64).fieldOf("uses_left").forGetter { it.usesLeft },
            ).apply(instance) { type, usesLeft -> PaperWeapon(type, usesLeft) }
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, PaperWeapon> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, { it.type },
                ByteBufCodecs.VAR_INT, { it.usesLeft },
                { type, usesLeft -> PaperWeapon(type, usesLeft) },
            )
    }
}
