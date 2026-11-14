package me.vangoo.mysteries.pathways.fool.abilities

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.Disguise
import me.vangoo.mysteries.ability.SessionEnd
import me.vangoo.mysteries.ability.SessionScope
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.FaceGalleryPayload
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Face.Kind
import me.vangoo.mysteries.pathways.fool.Faces
import me.vangoo.mysteries.pathways.fool.face
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import kotlin.time.Duration.Companion.seconds

/**
 * Fool, Seq. 6 (Faceless): **Shapeshifting**.
 *
 * The Faceless wears someone else's face: to every other player they *are* that person, name and
 * skin both — in the tab row, over the head, in chat and in the game's own lines (a death, an
 * advancement) — for as long as they keep paying for it. Damage does not shake it loose and there is
 * no timer — only the upkeep and a second cast end it.
 *
 * ## Whose face: the one in front of you, or any you have seen
 *
 * The plugin listed every player who had ever joined the server in a paginated menu of 53 heads.
 * Here a face is one the Faceless has **seen** ([Faces]). Looking at a player takes theirs at once,
 * off their head. Casting at nobody opens the gallery of faces seen — heads with names, newest
 * first, searchable — and the face picked folds itself out of paper on the Faceless's head, since
 * there is nobody to take it off. Leaving the gallery costs nothing.
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
 * near sees it happen, and the Faceless sees the new face on themselves in F5 and on their arm.
 */
class Shapeshifting : ActiveAbility(AbilityIdentity.of("fool_shapeshifting")) {

    override val displayName: Component = Component.literal("Перевтілення")

    /** Nothing up front: the whole price is the upkeep, so taking the mask off is always free. */
    override val spiritualityCost: Int = 0

    override fun periodicCost(sequence: Sequence): Int = COST_PER_SECOND

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Носите обличчя будь-кого, кого бачили: інші бачать чуже ім'я та скін.\n" +
            "▪ Погляд на гравця (до ${RANGE.toInt()} бл.) знімає його обличчя одразу; без цілі — галерея побачених облич\n" +
            "▪ Без обмеження часу; шкода личину не знімає, у повідомленнях гри теж чуже ім'я\n" +
            "▪ Витрата $COST_PER_SECOND духовності/с; повторний каст знімає личину",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.Done()
        }

        val target = context.targeting.playerLookingAt(RANGE)
        if (target != null) {
            val face = Disguise.faceOf(target)
            face(context.level, Kind.TAKE, target, context.caster)
            put(context, face)
            hold(context) { face }
            return AbilityOutcome.Done()
        }

        val seen = Faces.seenBy(context.caster)
        if (seen.isEmpty()) return AbilityOutcome.failed("Ви ще нікого не бачили — погляньте на гравця")

        // The gallery: the cast stays open until a face is picked, and costs nothing if none is.
        val chosen = CompletableDeferred<Disguise.Face>()
        val caster = context.caster
        val now = System.currentTimeMillis()
        context.pendingChoices.present(
            caster.uuid,
            seen.size,
            show = { id ->
                ModChannels.FACE_GALLERY.sendTo(
                    caster,
                    FaceGalleryPayload(id, seen.map { (face, at) -> FaceGalleryPayload.Entry(Disguise.profileOf(face), (now - at) / 1000L) }),
                )
            },
            onDropped = { chosen.cancel() },
        ) { index, _ -> chosen.complete(seen[index].first) }

        val ticket = context.cast
        val messaging = context.messaging
        hold(context) {
            val face = withTimeoutOrNull(GALLERY_WAIT) { chosen.await() } ?: return@hold null
            val wearer = owner ?: return@hold null
            // Nobody to take it off: it folds itself out of paper on the Faceless's own head.
            face(wearer.level(), Kind.MASK, wearer)
            Disguise.wear(wearer, face)
            wearer.level().playSound(null, wearer.x, wearer.y, wearer.z, SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 1.0f, 1.2f)
            if (face.textures == null) messaging.hint(wearer, Component.literal("Скін цієї личини не збережено — узято лише ім'я"), Fizzle.NONE)
            ticket.complete()
            face
        }
        return AbilityOutcome.Awaiting()
    }

    /** The mask goes on: everyone else is told, and the Faceless hears the mirror. */
    private fun put(context: CastContext, face: Disguise.Face) {
        Disguise.wear(context.caster, face)
        val at = context.origin
        context.level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 1.0f, 1.0f)
        if (face.textures == null) {
            context.messaging.hint(context.caster, Component.literal("Скін цієї личини не збережено — узято лише ім'я"), Fizzle.NONE)
        }
    }

    /**
     * Keeps the mask on for its upkeep. [pick] says which face it is — at once, or once the gallery
     * answers; `null` means there was none, and nothing was ever worn.
     */
    private fun hold(context: CastContext, pick: suspend SessionScope.() -> Disguise.Face?) {
        val messaging = context.messaging
        context.session {
            pick() ?: return@session
            try {
                val end = sustain(
                    periodTicks = TICKS_PER_SECOND,
                    upkeep = periodicCost,
                    upkeepPeriodTicks = TICKS_PER_SECOND,
                ) { }
                if (end == SessionEnd.EXHAUSTED) {
                    messaging.hint(ownerId, Component.literal("Духовність вичерпана — личина спала"), Fizzle.EMPTY)
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
    }

    private companion object {
        const val COST_PER_SECOND = 20
        const val COOLDOWN_SECONDS = 10
        const val RANGE = 16.0

        /** How long the gallery may stay open before the cast lapses, free. */
        val GALLERY_WAIT = 60.seconds
    }
}
