package me.vangoo.mysteries.pathways.whitetower.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import me.vangoo.mysteries.schedule.TaskHandle
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3

/**
 * WhiteTower, Seq. 7 (Detective): **Reconstruction**.
 *
 * The Detective freezes the scene and looks at what just happened here. A diagram appears in the
 * air: from everyone who struck to everyone who was struck a thread is drawn, and the blow itself
 * runs along it. Below is the record: who, whom, how many seconds ago.
 *
 * ## Why not a block-change journal, as in the plugin
 *
 * The plugin's `MysticalReenactment` showed the history of blocks placed and broken over the last
 * five minutes, and the whole server paid for it: for there to be anything to show, the mod had
 * to **keep writing a journal** of every change in the world, including other people's and those
 * nobody would ever ask about. The Detective there was the consumer of a subsystem that existed
 * for them alone.
 *
 * Here there is no journal at all. The game **already** stores who last struck whom and how many
 * ticks ago — that is the vanilla `getLastHurtByMob` / `getLastHurtMob` with their timestamps,
 * which mobs need for aggro. The Detective simply knows how to read it. Zero records, zero
 * memory, zero effect on anyone who is not a Detective — and the scene comes out more accurate
 * than a list of broken blocks: a fight is more interesting than a fence.
 *
 * ## The positions are taken at cast time
 *
 * The diagram does not follow the participants, and that is not a simplification: what is
 * reconstructed is the **past**. A thread trailing after someone who has already run would show
 * something other than what happened.
 */
class CrimeReconstruction : ActiveAbility(AbilityIdentity.of("whitetower_crime_reconstruction")) {

    override val displayName: Component = Component.literal("Реконструкція")

    override val spiritualityCost: Int = 120

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Місце події проступає в повітрі: нитки показують, хто кого бив, а протокол — коли.\n" +
            "▪ Радіус: ${radius(sequence).toInt()} бл.\n" +
            "▪ Пам'ять сцени: ${WINDOW_SECONDS} с\n" +
            "▪ Схема тримається ${REPLAY_TICKS / TICKS_PER_SECOND} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val scene = context.targeting.livingNear(radius(context.sequence))
            .mapNotNull { blow(it) }
            .distinctBy { setOf(it.attacker.uuid, it.victim.uuid) }
            .sortedBy { it.ticksAgo }
            .take(MAX_LINKS)

        if (scene.isEmpty()) {
            return AbilityOutcome.failed("Сцена чиста: тут нікого не били останні $WINDOW_SECONDS с")
        }

        val mote = ModParticles.mote(context.effects.pathwayColor)
        scene.forEach { link ->
            thread(context, link, mote)
            context.messaging.tell(
                context.caster,
                Component.literal(
                    "  ${link.attacker.name.string} → ${link.victim.name.string} · " +
                        "${link.ticksAgo / TICKS_PER_SECOND} с тому",
                ),
            )
        }

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.6f,
        )
        return AbilityOutcome.done("Реконструкція: слідів на сцені — ${scene.size}")
    }

    /**
     * The last blow the game itself remembers.
     *
     * The timestamp is the **victim's** `tickCount` at the moment of the blow, so it is compared
     * with that same counter rather than with the server's clock: an entity that loaded later has
     * an age of its own.
     */
    private fun blow(victim: LivingEntity): Link? {
        val attacker = victim.lastHurtByMob ?: return null
        if (attacker === victim || attacker.isRemoved) return null
        val ticksAgo = victim.tickCount - victim.lastHurtByMobTimestamp
        if (ticksAgo !in 0..(WINDOW_SECONDS * TICKS_PER_SECOND)) return null
        return Link(attacker, victim, ticksAgo)
    }

    /**
     * A thread from the attacker to the victim, with the blow itself running along it.
     *
     * The head runs in one direction only — otherwise the thread would read as a connection
     * rather than a direction, and the direction here is the conclusion.
     */
    private fun thread(context: CastContext, link: Link, mote: net.minecraft.core.particles.ParticleOptions) {
        val level = context.level
        val from = link.attacker.eyePosition
        val to = link.victim.position().add(0.0, link.victim.bbHeight * 0.5, 0.0)
        var step = 0

        lateinit var handle: TaskHandle
        handle = context.scheduling.every(PERIOD_TICKS) {
            if (step * PERIOD_TICKS >= REPLAY_TICKS) return@every handle.cancel()
            val head = (step * HEAD_SPEED) % 1.0
            repeat(DASHES) { dash ->
                val t = (head + dash.toDouble() / DASHES) % 1.0
                val point = from.lerp(to, t)
                level.sendParticles(mote, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
            }
            step++
        }

        // The ends of the thread are marked more densely: that is where the participants stood,
        // and that is exactly where the player looks.
        mark(level, from, mote)
        mark(level, to, mote)
    }

    private fun mark(level: ServerLevel, at: Vec3, mote: net.minecraft.core.particles.ParticleOptions) {
        level.sendParticles(mote, at.x, at.y, at.z, 12, 0.25, 0.25, 0.25, 0.0)
    }

    private fun radius(sequence: Sequence): Double =
        scale(BASE_RADIUS, sequence, SequenceScaler.ScalingStrategy.MODERATE).toDouble()

    private data class Link(val attacker: LivingEntity, val victim: LivingEntity, val ticksAgo: Int)

    private companion object {
        const val BASE_RADIUS = 16
        const val COOLDOWN_SECONDS = 45
        /** How many seconds back still counts as "just now". */
        const val WINDOW_SECONDS = 60
        const val MAX_LINKS = 8

        const val REPLAY_TICKS = 6 * TICKS_PER_SECOND
        const val PERIOD_TICKS = 2
        /** How much of the thread the head covers per tick: a full run ≈ 1.7 s. */
        const val HEAD_SPEED = 0.06
        const val DASHES = 5
    }
}
