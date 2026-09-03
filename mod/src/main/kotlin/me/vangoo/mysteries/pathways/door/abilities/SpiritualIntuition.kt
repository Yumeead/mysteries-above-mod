package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Door, Seq. 7 (Astrologer): **Spiritual Intuition**.
 *
 * The Astrologer feels **someone else's intent** — not the blow but the moment before it: a mob
 * that has chosen them, and a person who will not take their aim off them.
 *
 * ## Why not "someone took an ability in hand"
 *
 * In the plugin the intuition listened to `PlayerItemHeldEvent`: abilities there were items in the
 * hotbar, so switching slots was the "wind-up". The mod has no ability items at all — a cast comes
 * from a key — so that signal is not merely hard to reproduce, it does not exist. And reproducing
 * it with a **later** signal (the cast has already happened) would mean writing `DecryptPatterns`
 * twice: the same event, the same radius, the same line.
 *
 * So the intuition took a different signal — one the game really does have before a blow:
 *
 * | source | the vanilla question |
 * |---|---|
 * | a beast | [Mob.getTarget] — whom it has already chosen |
 * | a person | where they are looking and how precisely ([AIM] — the cosine of the angle) |
 *
 * Both answer "I am being hunted right now", and neither needs an event or a mixin: this is
 * polling, and it belongs here — there is no hook for "someone took aim at me" and there cannot be.
 *
 * ## One state record per owner, not two dimensions
 *
 * The plugin kept `casterId → attackerId → (name, time)` and cleaned expired entries with a
 * separate sweeper on the tick. A warning, however, is read one at a time: what matters to the
 * player is "someone is looking at me", not a list. So the state is a single tick of the last
 * warning per owner, and there is no way for it to go stale.
 */
class SpiritualIntuition :
    ToggleablePassiveAbility(AbilityIdentity.of("door_spiritual_intuition")),
    Ticking {

    /** Twice a second: an intent ripens more slowly than that. */
    override val tickPeriodTicks: Int = 10

    override val displayName: Component = Component.literal("Духовна інтуїція")

    /** When the owner was last warned — so as not to buzz every tick. */
    private val lastWarned = HashMap<UUID, Long>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви відчуваєте, коли на вас полюють.\n" +
            "▪ Звір, що вас обрав, або людина, що не зводить прицілу, у ${RANGE.toInt()} бл.\n" +
            "▪ Попередження не частіше разу на ${QUIET_TICKS / TICKS_PER_SECOND} с",
    )

    override fun onEnable(context: AbilityContext) {
        lastWarned.remove(context.caster.uuid)
        val at = context.origin
        context.level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f,
        )
    }

    override fun onDisable(context: AbilityContext) {
        lastWarned.remove(context.caster.uuid)
        val at = context.origin
        context.level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.0f,
        )
    }

    override fun tick(context: AbilityContext) {
        val owner = context.caster.uuid
        val now = context.scheduling.tick
        if (now - (lastWarned[owner] ?: Long.MIN_VALUE) < QUIET_TICKS) return

        val threat = context.targeting.livingNear(RANGE).firstOrNull { aimsAt(it, context) } ?: return
        lastWarned[owner] = now

        val distance = Math.sqrt(threat.distanceToSqr(context.caster)).roundToInt()
        context.messaging.actionBar(
            context.caster,
            Component.literal("⚠ ${threat.name.string} тримає вас на прицілі — $distance бл."),
        )
        // Quieter for those far away: intuition is a sense of distance.
        val volume = (1.0 - distance / RANGE).coerceAtLeast(0.1).toFloat()
        val at = context.origin
        context.level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS, volume, 1.8f,
        )
    }

    /**
     * A beast answers with its target, a person with their look direction.
     *
     * The [AIM] threshold is taken as a cosine rather than in degrees: the `dot` of two unit
     * vectors **is** the cosine, so an `acos` here would be an arccosine done only to compare it
     * straight back.
     */
    private fun aimsAt(threat: LivingEntity, context: AbilityContext): Boolean = when (threat) {
        is Mob -> threat.target === context.caster
        is Player -> {
            val toMe = context.origin.subtract(threat.position())
            toMe.lengthSqr() > 1.0e-6 && threat.getViewVector(1.0f).dot(toMe.normalize()) >= AIM
        }
        else -> false
    }

    private companion object {
        const val RANGE = 30.0
        const val QUIET_TICKS = 3 * TICKS_PER_SECOND
        /** ≈18°: a wider cone would fire on everyone who simply walks past. */
        const val AIM = 0.95
    }
}
