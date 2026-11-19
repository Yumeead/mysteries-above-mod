package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.effect.privateSound
import me.vangoo.mysteries.pathways.common.DangerSense
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import java.util.UUID

/**
 * Justiciar, Seq. 8 (Sheriff): **Danger Intuition**.
 *
 * The Seer's sense of harm coming ([DangerSense]): a projectile foreseen at launch, a bow drawn, an armed enemy close,
 * a mob closing in, a fuse burning, each flaring for the Sheriff alone with a bell from its side and an arrow by the
 * crosshair.
 *
 * The Sheriff's own, by the novel ("a sharp intuition for monitoring others and for being monitored"): someone who
 * keeps looking at them for [WATCHED_TICKS], armed or not, is felt too, more quietly: a pale arrow and a soft chime
 * from where the watcher stands, no outline. A trait, not a switch: always on, like the Seer's.
 */
class Intuition : PermanentPassiveAbility(AbilityIdentity.of("justiciar_intuition")), Ticking {

    override val tickPeriodTicks: Int = DangerSense.SCAN_PERIOD_TICKS

    override val displayName: Component = Component.literal("Інтуїція Небезпеки")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Передчуваєте загрозу до удару: снаряд, що летить у вас, натягнутий лук, озброєного ворога поруч, моба, що біжить до вас, крипера, підпалений TNT.\n" +
            "▪ Стрілка біля прицілу показує, звідки загроза; червона, коли удар менш ніж за секунду\n" +
            "▪ Загроза спалахує обрисом, і дзвін лунає з її боку; бачите й чуєте лише ви\n" +
            "▪ Відчуваєте, коли на вас довго дивляться: бліда стрілка й тихий звук з того боку",
    )

    private val sense = DangerSense()

    /** Per Sheriff: since which tick each watcher has kept looking at them. */
    private val watchedSince = HashMap<UUID, HashMap<UUID, Long>>()

    override fun onActivate(context: AbilityContext) {
        sense.activate(context)
        watchedSince[context.caster.uuid] = HashMap()
    }

    override fun onDeactivate(context: AbilityContext) {
        sense.deactivate(context.caster.uuid)
        watchedSince.remove(context.caster.uuid)
    }

    override fun tick(context: AbilityContext) {
        sense.scan(context)
        feelWatched(context)
    }

    /** Another player who keeps the Sheriff in their eyes, from as far as one can make out a face. */
    private fun feelWatched(context: AbilityContext) {
        val caster = context.caster
        val since = watchedSince.getOrPut(caster.uuid) { HashMap() }
        val now = context.scheduling.tick
        val watching = HashSet<UUID>()
        for (watcher in caster.level().players()) {
            if (watcher === caster || watcher.isSpectator) continue
            if (watcher.distanceTo(caster) > WATCH_RANGE) continue
            if (!DangerSense.isLookingAt(watcher, caster, WATCH_CONE) || !watcher.hasLineOfSight(caster)) continue
            watching += watcher.uuid
            val start = since.getOrPut(watcher.uuid) { now }
            if (now - start < WATCHED_TICKS || !sense.fresh(context, watcher)) continue
            sense.point(context, watcher, DangerSense.PRESENCE_TICKS, urgent = false)
            privateSound(caster, watcher.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 0.6f)
        }
        since.keys.retainAll(watching)
    }

    private companion object {
        const val WATCH_RANGE = 32.0

        /** "Looking at me": about ten degrees off the Sheriff's body. */
        const val WATCH_CONE = 0.985
        const val WATCHED_TICKS = 40L
    }
}
