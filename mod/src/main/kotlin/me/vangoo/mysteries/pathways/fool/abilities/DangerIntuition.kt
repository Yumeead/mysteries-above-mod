package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.DangerPremonition
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.effect.privateGlow
import me.vangoo.mysteries.effect.privateSound
import me.vangoo.mysteries.effect.privateText
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.pathways.common.DangerSense
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.block.DoorBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.TrapDoorBlock
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Fool, Seq. 9 (Seer): **Danger Intuition**.
 *
 * The sense of harm coming itself — a projectile foreseen at launch, a bow drawn, an armed enemy close, a mob closing
 * in, a fuse burning — is [DangerSense], shared with the Justiciar's Sheriff. What is the Seer's own:
 *
 * - **Behind the door**: whoever stands behind a door the Seer looks at is outlined for them alone.
 * - **A glimpse** of what the target under the crosshair will do next, now and then, a single word over its head.
 * - **The lethal dodge**, cancelled through [IncomingDamage]: yanked away from the blow a beat before it lands.
 *
 * Balance numbers live in [DangerPremonition] (`core`); this file is effects only.
 */
class DangerIntuition :
    PermanentPassiveAbility(AbilityIdentity.of("fool_danger_intuition")),
    Ticking {

    override val tickPeriodTicks: Int = DangerSense.SCAN_PERIOD_TICKS

    override val displayName: Component = Component.literal("Інтуїція небезпеки")

    override fun description(sequence: Sequence): Component {
        val dodge = (DangerPremonition.lethalDodgeChance(sequence) * 100).roundToInt()
        return Component.literal(
            "Передчуваєте загрозу до удару: снаряд, що летить у вас, натягнутий лук, озброєного ворога поруч, крипера, підпалений TNT.\n" +
                "▪ Стрілка біля прицілу показує, звідки загроза; червона — удар менш ніж за секунду\n" +
                "▪ Загроза спалахує обрисом, і дзвін лунає з її боку — бачите й чуєте лише ви\n" +
                "▪ Бачите крізь двері, на які дивитесь\n" +
                "▪ Зрідка вгадуєте наступну дію цілі під прицілом\n" +
                "▪ $dodge% шанс автоматично ухилитись від смертельного удару",
        )
    }

    private val sense = DangerSense()
    private val lastDoorReveal = HashMap<UUID, Long>()
    private val lastPrediction = HashMap<UUID, Long>()
    private val lastDodge = HashMap<UUID, Long>()

    override fun onActivate(context: AbilityContext) {
        sense.activate(context)
        context.events.on<IncomingDamage> { damage -> tryLethalDodge(context, damage) }
    }

    override fun onDeactivate(context: AbilityContext) {
        val owner = context.caster.uuid
        sense.deactivate(owner)
        lastDoorReveal.remove(owner)
        lastPrediction.remove(owner)
        lastDodge.remove(owner)
    }

    override fun tick(context: AbilityContext) {
        sense.scan(context)
        revealBehindDoor(context)
        predictNextAction(context)
    }

    // ── Behind the door ──────────────────────────────────────────────────────────

    private fun revealBehindDoor(context: AbilityContext) {
        val owner = context.caster.uuid
        val now = context.scheduling.tick
        if ((lastDoorReveal[owner] ?: 0L) + DOOR_THROTTLE_TICKS > now) return

        val caster = context.caster
        val hit = caster.pick(DOOR_LOOK_RANGE, 1.0f, false) as? BlockHitResult ?: return
        val block = context.level.getBlockState(hit.blockPos).block
        if (block !is DoorBlock && block !is TrapDoorBlock && block !is FenceGateBlock) return

        val center = Vec3.atCenterOf(hit.blockPos)
        val revealed = context.targeting.livingNear(DOOR_REVEAL_RADIUS + DOOR_LOOK_RANGE)
            .filter { it.position().distanceTo(center) <= DOOR_REVEAL_RADIUS }
        if (revealed.isEmpty()) return

        // Seen through the door, for the Seer alone: whoever stands behind it is outlined.
        privateGlow(caster, revealed, DOOR_GLOW_TICKS)
        lastDoorReveal[owner] = now
        privateSound(caster, center, SoundEvents.SCULK_SENSOR_HIT, 0.7f, 1.4f)
    }

    // ── The occasional hint ──────────────────────────────────────────────────────

    private fun predictNextAction(context: AbilityContext) {
        val owner = context.caster.uuid
        val now = context.scheduling.tick
        if ((lastPrediction[owner] ?: 0L) + PREDICT_THROTTLE_TICKS > now) return

        val target = context.targeting.lookingAt(PREDICT_RANGE) ?: return
        if (Random.nextDouble() > DangerPremonition.actionPredictionChance(context.sequence)) return

        val glimpse = when {
            target is Mob && target.target === context.caster -> "атакує"
            target.deltaMovement.lengthSqr() > 0.05 -> "рушить"
            else -> "вичікує"
        }
        lastPrediction[owner] = now
        privateText(context.caster, target.position(), Component.literal(glimpse), GLIMPSE_TICKS, GLIMPSE, anchor = target)
    }

    // ── Lethal dodge ─────────────────────────────────────────────────────────────

    private fun tryLethalDodge(context: AbilityContext, damage: IncomingDamage) {
        val caster = context.caster
        if (damage.victim !== caster) return
        if (damage.amount < caster.health) return

        val owner = caster.uuid
        val now = context.scheduling.tick
        val cooldownTicks = DangerPremonition.lethalDodgeCooldownMillis(context.sequence) / MILLIS_PER_TICK
        if ((lastDodge[owner] ?: 0L) + cooldownTicks > now) return
        if (Random.nextDouble() > DangerPremonition.lethalDodgeChance(context.sequence)) return

        damage.cancel()
        lastDodge[owner] = now

        // Yanked away from the blow with no wind-up, a beat before it lands: where the body was, a grey
        // trace hangs. Away from whoever struck — or aside, if the blow came from nowhere in particular.
        val from = (damage.directCause ?: damage.attacker)?.position()
        val away = from?.let { caster.position().subtract(it) }?.let { Vec3(it.x, 0.0, it.z) }?.takeIf { it.lengthSqr() > 1e-4 }
        val push = away?.normalize()?.scale(DODGE_SPEED) ?: caster.getViewVector(1.0f).let { look ->
            Vec3(-look.z, 0.0, look.x).normalize().scale(if (Random.nextBoolean()) DODGE_SPEED else -DODGE_SPEED)
        }
        context.level.sendParticles(ModParticles.mote(TRACE), caster.x, caster.y + 1.0, caster.z, TRACE_MOTES, 0.25, 0.5, 0.25, 0.0)
        caster.setDeltaMovement(push.x, DODGE_LIFT, push.z)
        // The server's own push reaches the client only if it is marked: without this the dodge never moved anyone.
        caster.hurtMarked = true
        context.level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.8f, 1.6f)
    }

    private companion object {
        const val DOOR_LOOK_RANGE = 3.0
        const val DOOR_REVEAL_RADIUS = 4.5
        val DOOR_GLOW_TICKS = 2 * TICKS_PER_SECOND
        val DOOR_THROTTLE_TICKS = 2500L / 50

        const val PREDICT_RANGE = 20.0
        val PREDICT_THROTTLE_TICKS = 4000L / 50

        const val MILLIS_PER_TICK = 50L

        val GLIMPSE_TICKS = 2 * TICKS_PER_SECOND
        val GLIMPSE: RgbColor = RgbColor.of(214, 196, 240)
        val TRACE: RgbColor = RgbColor.of(170, 170, 182)
        const val TRACE_MOTES = 14
        const val DODGE_SPEED = 1.2
        const val DODGE_LIFT = 0.3
    }
}
