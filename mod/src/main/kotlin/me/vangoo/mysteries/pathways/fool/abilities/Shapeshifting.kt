package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.Disguise
import me.vangoo.mysteries.ability.SessionEnd
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Face.Kind
import me.vangoo.mysteries.pathways.fool.face
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource

/**
 * Fool, Seq. 6 (Faceless): **Shapeshifting**.
 *
 * The Faceless takes a face off someone and wears it: to every other player they *are* that
 * person, name and skin both, for as long as they keep paying for it. Damage does not shake it
 * loose and there is no timer — only the upkeep and a second cast end it.
 *
 * ## The face is taken from someone in front of you
 *
 * The plugin listed every player who had ever joined the server in a paginated menu of 53 heads.
 * Here the face is taken off whoever the Faceless is **looking at** — the fourth time this port
 * turns one of the plugin's menus into a thing that exists in the world, after Door's three.
 * It costs the ability nothing in reach (a face, once stolen, is worn wherever you like) and buys
 * a scene the menu could not: to take someone's face you must first stand in front of them.
 *
 * ## A session, not a toggle — the same call `PsychologicalInvisibility` made
 *
 * The mask must be able to fall on its own when the spirituality runs out, and a toggle's switch
 * lives outside the ability where the ability cannot reach it. A session ends itself, and its
 * `finally` is what guarantees the borrowed face is handed back on every exit there is: a second
 * cast, exhaustion, death, logging out, the server stopping.
 *
 * ## The face is seen changing hands
 *
 * It peels off the one it was taken from and flies onto the Faceless's head; when the mask falls,
 * the borrowed face peels off and tears like paper on the way down. Nothing is written: whoever is
 * near sees it happen, and the Faceless sees their own new face in any mirror the game has.
 */
class Shapeshifting : ActiveAbility(AbilityIdentity.of("fool_shapeshifting")) {

    override val displayName: Component = Component.literal("Перевтілення")

    /** Nothing up front: the whole price is the upkeep, so taking the mask off is always free. */
    override val spiritualityCost: Int = 0

    override fun periodicCost(sequence: Sequence): Int = COST_PER_SECOND

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Знімає обличчя з гравця під прицілом і носить його: інші бачать чуже ім'я та скін.\n" +
            "▪ Дальність $RANGE бл., без обмеження часу; шкода личину не знімає\n" +
            "▪ Витрата $COST_PER_SECOND духовності/с; повторний каст знімає личину",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.Done()
        }

        val target = context.targeting.playerLookingAt(RANGE)
            ?: return AbilityOutcome.failed("Погляньте на того, чиє обличчя берете")

        val face = Disguise.faceOf(target)
        val messaging = context.messaging
        val casterId = context.caster.uuid

        face(context.level, Kind.TAKE, target, context.caster)
        Disguise.wear(context.caster, face)
        val at = context.origin
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 1.0f, 1.0f)
        if (face.textures == null) {
            messaging.hint(context.caster, Component.literal("Скін цієї личини не збережено — узято лише ім'я"), Fizzle.NONE)
        }

        context.session {
            try {
                val end = sustain(
                    periodTicks = TICKS_PER_SECOND,
                    upkeep = periodicCost,
                    upkeepPeriodTicks = TICKS_PER_SECOND,
                ) { }
                if (end == SessionEnd.EXHAUSTED) {
                    messaging.hint(casterId, Component.literal("Духовність вичерпана — личина спала"), Fizzle.EMPTY)
                }
            } finally {
                val masked = owner
                if (masked != null) {
                    // Before the skin goes back: the client peels off the face the head wears when this lands.
                    face(masked.level(), Kind.DROP, masked)
                    Disguise.remove(masked)
                    masked.level().playSound(
                        null, masked.x, masked.y, masked.z,
                        SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 0.8f,
                    )
                } else {
                    // Gone from the world: their player-info entry left with them, so there is
                    // nobody left to tell — only the bookkeeping to drop.
                    Disguise.forget(ownerId)
                }
            }
        }

        return AbilityOutcome.Done()
    }

    private companion object {
        const val COST_PER_SECOND = 20
        const val COOLDOWN_SECONDS = 10
        const val RANGE = 16.0
    }
}
