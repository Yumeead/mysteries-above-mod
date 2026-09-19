package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.ThreadVisionRange
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.pathways.fool.Marionettes
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity

/**
 * Fool, Seq. 5 (Marionettist): **Thread Sight**.
 *
 * The Marionettist sees the Threads of the Spirit Body — the strands that run up out of every
 * living thing — and what hides behind an invisibility has strands all the same. Nobody else sees
 * any of it.
 *
 * ## Owner-only, and that is why the packets are sent by hand
 *
 * `ServerLevel.sendParticles` tells everyone in range, which would turn a private sense into a
 * public light show and hand every hidden player a warning that they have been spotted. There is
 * no vanilla lever for "this particle, this viewer" — the particle packet is simply sent down one
 * connection, which is one call and needs no service of its own.
 *
 * The plugin lit invisible entities with an owner-only glow (`IGlowingContext`, a subsystem of
 * its own). Here they get a **denser, brighter thread** instead: the same information, drawn with
 * the vocabulary this ability already has, and no second mechanism to keep alive.
 *
 * ## It ticks, and there is nothing to wait for instead
 *
 * Since phase 06 a passive ticks only if it must. This one must: it answers "what is around me
 * right now", and there is no event for a creature merely existing.
 */
class ThreadSight : PermanentPassiveAbility(AbilityIdentity.of("fool_thread_sight")), Ticking {

    override val displayName: Component = Component.literal("Бачення ниток")

    /** Twice a second: often enough to look continuous, rare enough not to flood the wire. */
    override val tickPeriodTicks: Int = 10

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви бачите Нитки Духовного Тіла над усім живим — і невидимих теж.\n" +
            "▪ Радіус ${ThreadVisionRange.rangeFor(sequence)} бл.; нитки бачите лише ви\n" +
            "▪ Невидимих видає густіша нитка; ваші маріонетки світяться фіолетовим",
    )

    override fun tick(context: AbilityContext) {
        val caster = context.caster
        val range = ThreadVisionRange.rangeFor(context.sequence).toDouble()

        context.targeting.livingNear(range).forEach { seen ->
            val hidden = seen.isInvisible || seen.hasEffect(MobEffects.INVISIBILITY)
            val mine = Marionettes.ownerOf(seen) == caster.uuid
            val color = when {
                mine -> OWNED
                hidden -> HIDDEN
                else -> THREAD
            }
            thread(caster, seen, color, if (hidden || mine) DENSE else SPARSE)
        }
    }

    /**
     * One strand, drawn straight up out of the crown of the head and into nothing.
     *
     * All of it in a **single** packet: the count and the vertical spread make the strand, rather
     * than one packet per mote. With a couple of dozen creatures in range the difference is one
     * packet each against a hundred and twenty every half second, for a picture the eye cannot
     * tell apart.
     */
    private fun thread(
        viewer: net.minecraft.server.level.ServerPlayer,
        over: LivingEntity,
        color: RgbColor,
        knots: Int,
    ) {
        viewer.connection.send(
            ClientboundLevelParticlesPacket(
                ModParticles.mote(color),
                true,
                false,
                over.x,
                over.y + over.bbHeight + THREAD_RISE / 2.0,
                over.z,
                0.02f,
                (THREAD_RISE / 2.0).toFloat(),
                0.02f,
                0f,
                knots,
            ),
        )
    }

    private companion object {
        /** Soul-pale: an ordinary living thing's strand. */
        val THREAD = RgbColor.of(0xB8, 0xD8, 0xE8)

        /** Something is standing there that does not want to be seen. */
        val HIDDEN = RgbColor.of(0x6F, 0xE8, 0xC8)

        /** Already on your threads. */
        val OWNED = RgbColor.of(0xC0, 0x6C, 0xE8)

        /** How far the strand climbs above the crown of the head. */
        const val THREAD_RISE = 1.2
        const val SPARSE = 3
        const val DENSE = 6
    }
}
