package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.effect.privateGlow
import me.vangoo.mysteries.effect.privateSound
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Scrutiny
import me.vangoo.mysteries.pathways.justiciar.Dossiers
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import java.util.UUID

/**
 * Justiciar, Seq. 8 (Sheriff): **Recognition**.
 *
 * "As long as they see a person in the flesh, they firmly remember the target's appearance and obtain an additional
 * sense on an extraordinary level." While it is on, holding someone in the eye for [GAZE_TICKS] remembers them
 * ([Dossiers]): four corners close round them as the Sheriff looks, seen by the Sheriff alone, and snap shut with a
 * click. From then on, wherever the remembered one walks within reach, the Sheriff sees their footprints, and their
 * file stands in the book (B, «Справи»). The memory holds [slots] people; a new one pushes out the oldest.
 *
 * The plugin outlined the target with vanilla Glowing for everyone and wrote its health into the action bar; both are
 * gone. What the Sheriff learns is who and where, not a number.
 */
class Recognition : ToggleablePassiveAbility(AbilityIdentity.of("justiciar_recognition")), Ticking {

    override val tickPeriodTicks: Int = PERIOD_TICKS

    override val displayName: Component = Component.literal("Ідентифікація")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Поки увімкнено, подивіться на когось 1,5 с, і ви запам'ятаєте його назавжди.\n" +
            "▪ Поки запам'ятований у межах ${Dossiers.SENSE_RANGE.toInt()} бл., ви бачите його сліди, навіть крізь стіни\n" +
            "▪ Упізнаєте його й під чужим обличчям\n" +
            "▪ Справа на кожного в книзі (B), вкладка «Справи»\n" +
            "▪ Пам'ятаєте ${slots(sequence)} осіб; новий витісняє найдавнішого",
    )

    private class Gaze(val target: UUID, val entity: Int, var ticks: Int = 0, var known: Boolean = false)

    private val gazes = HashMap<UUID, Gaze>()

    override fun onEnable(context: AbilityContext) {
        gazes.remove(context.caster.uuid)
    }

    override fun onDisable(context: AbilityContext) {
        gazes.remove(context.caster.uuid)?.let { lose(context.caster, it) }
    }

    override fun tick(context: AbilityContext) {
        val caster = context.caster
        val target = context.targeting.lookingAt(RANGE)
        val gaze = gazes[caster.uuid]
        if (target == null || target.uuid != gaze?.target) {
            gaze?.let { lose(caster, it) }
            gazes.remove(caster.uuid)
            if (target == null) return
            gazes[caster.uuid] = Gaze(target.uuid, target.id)
            send(context, Scrutiny(target.id, Scrutiny.Stage.GAZE, GAZE_TICKS, color(context)))
            return
        }
        if (gaze.known) return
        gaze.ticks += PERIOD_TICKS
        if (gaze.ticks < GAZE_TICKS) return
        gaze.known = true
        Dossiers.remember(caster, target, slots(context.sequence))
        send(context, Scrutiny(target.id, Scrutiny.Stage.KNOWN, 0, color(context)))
        privateGlow(caster, listOf(target), KNOWN_GLOW_TICKS)
        privateSound(caster, target.position(), SoundEvents.BOOK_PAGE_TURN, 0.8f, 1.6f)
        privateSound(caster, caster.position(), SoundEvents.UI_LOOM_TAKE_RESULT, 0.6f, 1.4f)
    }

    private fun lose(caster: ServerPlayer, gaze: Gaze) {
        if (gaze.known) return
        ModChannels.WORLD_EFFECT.sendTo(caster, Scrutiny(gaze.entity, Scrutiny.Stage.LOST, 0, 0))
    }

    private fun send(context: AbilityContext, payload: Scrutiny) = ModChannels.WORLD_EFFECT.sendTo(context.caster, payload)

    private fun color(context: AbilityContext): Int = context.effects.pathwayColor.packed

    /** Three at Seq. 8, one more for every Sequence up. */
    private fun slots(sequence: Sequence): Int = 11 - sequence.level

    private companion object {
        const val PERIOD_TICKS = 2
        const val GAZE_TICKS = 30
        const val RANGE = 30.0
        const val KNOWN_GLOW_TICKS = 30
    }
}
