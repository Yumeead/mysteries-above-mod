package me.vangoo.mysteries.pathways.justiciar

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.effect.worldText
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import java.util.UUID

/**
 * The Paladin's marks: who is chosen for Punishment, by whom, until when. The ability marks whoever is in its sight;
 * a Prohibition marks whoever breaks it ([Prohibitions.breach]) if its Judge has Punishment, saying so aloud. Either
 * way the mark is the same, and it does three things for [MARK_TICKS]:
 *
 * - the Paladin shines with a dawn-like light (everyone sees it), and in the target's own eyes grows taller;
 * - unseen shackles close on the target's feet: it moves stiffly;
 * - the Paladin's blows land on it [BLOW_FACTOR] as hard.
 */
object Punishments {

    val IDENTITY: AbilityIdentity = AbilityIdentity.of("justiciar_punishment")

    /** target → (paladin, until server tick). One Paladin's mark per target at a time. */
    private val marks = mutableMapOf<UUID, Pair<UUID, Int>>()

    /** When each Judge last said that violators will be punished. */
    private val said = mutableMapOf<UUID, Int>()

    /** Whether [target] stands marked by [paladin] now. */
    fun marked(paladin: UUID, target: UUID, now: Int): Boolean = marks[target]?.let { it.first == paladin && it.second > now } ?: false

    /** [paladin] marks [target]. Marking someone already marked by them only lets the mark run on. */
    fun mark(paladin: ServerPlayer, target: LivingEntity, color: RgbColor) {
        val level = paladin.level()
        val now = level.server.tickCount
        val renewed = marked(paladin.uuid, target.uuid, now)
        marks[target.uuid] = paladin.uuid to now + MARK_TICKS
        if (renewed) return

        lash(level, paladin, target, WorldEffectPayload.Lash.Shape.MARK, color)
        level.playSound(null, paladin.x, paladin.y, paladin.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 1.4f)
        clang(level, target.position(), 0.8f)
        target.addEffect(MobEffectInstance(MobEffects.SLOWNESS, MARK_TICKS, 1, false, false))
        val paladinId = paladin.uuid
        val targetId = target.uuid
        AbilityServices.events.scopeOf(paladinId, IDENTITY).on<IncomingDamage>(MARK_TICKS) { damage ->
            if (damage.attacker?.uuid != paladinId || damage.victim.uuid != targetId) return@on
            if (!marked(paladinId, targetId, damage.victim.level().server?.tickCount ?: return@on)) return@on
            damage.scale(BLOW_FACTOR)
        }
    }

    /**
     * Someone broke [law]: if its Judge is a Paladin, the breaker is marked, and the Judge says so ("Violators will be
     * punished!"). Once per mark: a breach while the mark stands only lets it run on.
     */
    fun onBreach(law: Prohibitions.Law, offender: LivingEntity) {
        val judge = law.level.server.playerList.getPlayer(law.enforcer) ?: return
        val beyonder = AbilityServices.beyonders.of(judge.uuid) ?: return
        if (beyonder.abilityBy(IDENTITY) == null) return
        val fresh = !marked(judge.uuid, offender.uuid, law.level.server.tickCount)
        mark(judge, offender, law.color)
        // Said once for a crowd of breakers, not once for each of them.
        val now = law.level.server.tickCount
        if (fresh && now - (said[judge.uuid] ?: Int.MIN_VALUE / 2) >= SAID_EVERY_TICKS) {
            said[judge.uuid] = now
            worldText(
                AbilityServices.scheduling, law.level, judge.position().add(0.0, judge.bbHeight + 0.6, 0.0),
                Component.literal("Порушника буде покарано!").withColor(WORD_COLOR), WORD_TICKS, fadeInTicks = 6,
            )
        }
    }

    val MARK_TICKS: Int get() = WorldEffectPayload.Lash.Shape.MARK.life
    private const val BLOW_FACTOR = 1.5
    private const val WORD_COLOR = 0xF2C14E
    private const val WORD_TICKS = 40
    private const val SAID_EVERY_TICKS = 60
}
