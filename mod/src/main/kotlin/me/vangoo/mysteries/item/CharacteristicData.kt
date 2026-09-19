package me.vangoo.mysteries.item

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.vangoo.domain.brewing.Characteristic
import me.vangoo.domain.entities.Pathway
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/**
 * Which Characteristic this core is: a pathway and a Sequence, the same pair the potion carries.
 *
 * The name mirrors `core`'s [Characteristic] rather than repeating it, because that one is the
 * **brewing ingredient** — a key in a recipe, with no idea it can also be an item lying on the
 * ground. [key] is the one bridge between them, and it is the same string `BrewRecipe` already
 * matches against.
 */
data class CharacteristicData(val pathway: String, val sequence: Int) {

    init {
        require(pathway.isNotBlank()) { "Характеристика без шляху" }
        require(sequence in 0 until Pathway.SEQUENCE_COUNT) {
            "Послідовність Характеристики поза межами: $sequence"
        }
    }

    /** The ingredient key — what brewing will look this core up by when phase 09 lands. */
    fun key(): String = Characteristic(pathway, sequence).itemKey()

    companion object {

        val CODEC: Codec<CharacteristicData> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("pathway").forGetter { it.pathway },
                Codec.intRange(0, Pathway.SEQUENCE_COUNT - 1).fieldOf("sequence").forGetter { it.sequence },
            ).apply(instance) { pathway, sequence -> CharacteristicData(pathway, sequence) }
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CharacteristicData> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, { it.pathway },
                ByteBufCodecs.VAR_INT, { it.sequence },
                { pathway, sequence -> CharacteristicData(pathway, sequence) },
            )
    }
}
