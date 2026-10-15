package me.vangoo.mysteries.item

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.vangoo.domain.entities.Pathway
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.pathways.ModPathways
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec

/**
 * What exactly is poured into the bottle: the pathway and the Sequence.
 *
 * The format is chosen for the mod, not for the plugin: the plugin's saves do not exist and
 * there is nothing to migrate (decision 2). The pathway travels **by name**, not by ordinal —
 * names are stable and readable in `/data get`, while the order of pathways in the registry
 * changes every phase.
 *
 * It lives as an item component, so it goes both through world saving ([CODEC]) and through the
 * network ([STREAM_CODEC]).
 */
data class PotionBrew(val pathway: String, val sequence: Int) {

    init {
        require(pathway.isNotBlank()) { "Зілля без шляху" }
        require(sequence in 0 until Pathway.SEQUENCE_COUNT) {
            "Послідовність зілля поза межами: $sequence"
        }
    }

    /** `null` — no such pathway in the mod (a typo in a command, an item from an old build). */
    fun resolvePathway(): Pathway? = ModPathways.byName(pathway)

    fun asSequence(): Sequence = Sequence(sequence)

    companion object {

        val CODEC: Codec<PotionBrew> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("pathway").forGetter { it.pathway },
                Codec.intRange(0, Pathway.SEQUENCE_COUNT - 1).fieldOf("sequence").forGetter { it.sequence },
            ).apply(instance) { pathway, sequence -> PotionBrew(pathway, sequence) }
        }

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, PotionBrew> =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, { it.pathway },
                ByteBufCodecs.VAR_INT, { it.sequence },
                { pathway, sequence -> PotionBrew(pathway, sequence) },
            )
    }
}
