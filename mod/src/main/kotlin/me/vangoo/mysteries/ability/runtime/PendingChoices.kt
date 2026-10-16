package me.vangoo.mysteries.ability.runtime

import me.vangoo.mysteries.net.payload.ChoiceAnswerPayload
import me.vangoo.mysteries.net.payload.ChoiceOfferPayload
import net.minecraft.network.chat.Component
import java.util.UUID

/**
 * A choice an ability is waiting on: the context's `ui` seam.
 *
 * `Alteration` and `ThunderVoice` pause mid-cast for a pick among options that only exist at
 * that moment (the online players, a trap's two halves) — the plugin drew a chest menu for each.
 * Here [offer] sends the options to the caster's client, which opens a real screen and answers
 * with an index. The ability keeps its ticket unsettled meanwhile, so a closed screen costs the
 * caster nothing.
 *
 * A choice known before the cast does not belong here: it is a mode, picked on the wheel ahead
 * of time (`PsychicCue`). This seam is for what can only be asked once the cast has begun.
 *
 * One offer per player at a time: a fresh [offer] replaces whatever was pending, and the
 * replaced screen's late answer is told apart by its id.
 */
class PendingChoices(private val send: (UUID, ChoiceOfferPayload) -> Unit) {

    private class Offer(val id: Int, val size: Int, val onChosen: (Int, String) -> Unit)

    private val byPlayer = HashMap<UUID, Offer>()
    private var nextId = 0

    /**
     * Asks [player] to pick one of [options]. With [withText] the screen also carries a line of
     * free text, handed to [onChosen] together with the pick (otherwise it is empty).
     */
    fun offer(
        player: UUID,
        title: Component,
        options: List<Component>,
        withText: Boolean = false,
        onChosen: (index: Int, text: String) -> Unit,
    ) {
        val id = ++nextId
        byPlayer[player] = Offer(id, options.size, onChosen)
        send(player, ChoiceOfferPayload(id, title, options, withText))
    }

    /**
     * One answer from [player]'s client. Foreign input: a stale id is ignored outright, an
     * index out of range or [ChoiceAnswerPayload.CANCEL] drops the offer.
     */
    fun answer(player: UUID, answer: ChoiceAnswerPayload) {
        val offer = byPlayer[player]?.takeIf { it.id == answer.id } ?: return
        byPlayer.remove(player)
        if (answer.index in 0 until offer.size) offer.onChosen(answer.index, answer.text.trim())
    }

    fun forget(player: UUID) {
        byPlayer.remove(player)
    }
}
