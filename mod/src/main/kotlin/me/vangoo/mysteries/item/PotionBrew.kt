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
 * Що саме налито в пляшку: шлях і Послідовність.
 *
 * Формат вибрано під мод, а не під плагін: збережень плагіна не існує, мігрувати нема
 * чого (рішення 2). Шлях їде **іменем**, а не порядковим номером — імена стабільні й
 * читаються в `/data get`, а порядок шляхів у реєстрі змінюється щофази.
 *
 * Живе як компонент предмета, тож проходить і через збереження світу
 * ([CODEC]), і через мережу ([STREAM_CODEC]).
 */
data class PotionBrew(val pathway: String, val sequence: Int) {

    init {
        require(pathway.isNotBlank()) { "Зілля без шляху" }
        require(sequence in 0 until Pathway.SEQUENCE_COUNT) {
            "Послідовність зілля поза межами: $sequence"
        }
    }

    /** `null` — шлях іще не перенесено (13 заглушок і решта фази 08). */
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
