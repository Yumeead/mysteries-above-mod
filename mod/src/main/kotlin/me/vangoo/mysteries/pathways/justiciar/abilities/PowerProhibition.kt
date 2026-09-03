package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.event.ProhibitionViolated
import me.vangoo.mysteries.pathways.justiciar.raiseZone
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.Items

/**
 * Justiciar, Seq. 6 (Judge): **Prohibition of Power**.
 *
 * Plants a territory where one action is simply forbidden. The plugin offered six bans, three of
 * which had no real hook to stand on in Fabric (a general teleport ban, a domain-entry ban, a
 * full block-change lock — each would need its own new mixin for a single Seq. 6 utility); those
 * three are cut rather than faked with polling, the same call the plugin's own
 * `LightningBoltArrow` and `ThunderVoice` made in Tyrant. The three that remain each ride a real
 * mechanism: [ProhibitionMode.KILL_BAN] and [ProhibitionMode.WEAPON_BAN] cancel through
 * [IncomingDamage] — a genuine cancel, not the plugin's hand-simulated Totem of Undying — and
 * [ProhibitionMode.FLIGHT_BAN] polls, because vanilla flight has no toggle event to catch at all.
 *
 * Mode is the item in hand, exactly as [Verdict] and Door's `DivinationArts` already do.
 */
class PowerProhibition : ActiveAbility(AbilityIdentity.of("justiciar_power_prohibition")) {

    private enum class ProhibitionMode(val label: String) {
        KILL_BAN("Заборона вбивств"),
        FLIGHT_BAN("Заборона польоту"),
        WEAPON_BAN("Заборона зброї"),
    }

    override val displayName: Component = Component.literal("Заборона")

    override val spiritualityCost: Int = 250

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Накладає заборону на дію в зоні навколо вас на ${DURATION_TICKS / 20} с.\n" +
            "▪ Тотем Безсмертя в руці — заборона вбивств\n" +
            "▪ Елітри в руці — заборона польоту\n" +
            "▪ Меч у руці — заборона використання зброї\n" +
            "▪ Порушення карає власне Покарання Судді (Посл. 5+)",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val mode = when (context.caster.mainHandItem.item) {
            Items.ELYTRA -> ProhibitionMode.FLIGHT_BAN
            Items.IRON_SWORD -> ProhibitionMode.WEAPON_BAN
            else -> ProhibitionMode.KILL_BAN
        }
        val casterId = context.caster.uuid
        val radius = jurisdictionRadius(context.sequence.level)

        val zone = context.raiseZone(
            radius = radius,
            durationTicks = DURATION_TICKS,
            boundaryParticle = ParticleTypes.ENCHANT,
            onExpire = { context.messaging.actionBar(context.caster, Component.literal("⚖ Заборона знята.")) },
        )

        when (mode) {
            ProhibitionMode.KILL_BAN -> context.events.on<IncomingDamage>(DURATION_TICKS + 20) { damage ->
                val victim = damage.victim as? ServerPlayer ?: return@on
                val attacker = damage.attacker as? ServerPlayer ?: return@on
                if (!zone.contains(victim.position())) return@on
                if (damage.amount < victim.health) return@on

                damage.cancel()
                victim.heal(1.0f - victim.health)
                context.level.sendParticles(
                    ParticleTypes.TOTEM_OF_UNDYING, victim.x, victim.y + 1.0, victim.z, 30, 0.5, 0.5, 0.5, 0.1,
                )
                context.messaging.actionBar(attacker, Component.literal("⚖ Вбивство заборонено!"))
                context.messaging.actionBar(victim, Component.literal("⚖ Заборона врятувала вас від смерті!"))
                reportViolation(context, casterId, attacker.uuid, "спробував вбити ${victim.name.string}")
            }

            ProhibitionMode.WEAPON_BAN -> context.events.on<IncomingDamage>(DURATION_TICKS + 20) { damage ->
                val attacker = damage.attacker as? ServerPlayer ?: return@on
                if (!zone.contains(attacker.position())) return@on
                if (!isBannedWeapon(attacker)) return@on

                damage.cancel()
                context.messaging.actionBar(attacker, Component.literal("⚖ Використання зброї заборонено!"))
                reportViolation(context, casterId, attacker.uuid, "спробував атакувати забороненою зброєю")
            }

            ProhibitionMode.FLIGHT_BAN -> Unit // enforced by the poll below
        }

        if (mode == ProhibitionMode.FLIGHT_BAN) {
            val poll = context.scheduling.every(5, 0) {
                for (player in zone.playersInside()) {
                    if (player.abilities.flying || player.abilities.mayfly) {
                        player.abilities.flying = false
                        player.abilities.mayfly = false
                        player.onUpdateAbilities()
                        context.messaging.actionBar(player, Component.literal("⚖ Політ заборонено!"))
                        reportViolation(context, casterId, player.uuid, "спробував летіти")
                    }
                }
            }
            context.scheduling.after(DURATION_TICKS) { poll.cancel() }
        }

        showActivation(context, mode, radius)
        return AbilityOutcome.done("Заборону накладено: ${mode.label}")
    }

    private fun reportViolation(context: CastContext, enforcerId: java.util.UUID, violatorId: java.util.UUID, action: String) {
        if (violatorId == enforcerId) return
        if (context.sequence.level <= PUNISHMENT_UNLOCK_SEQUENCE) {
            context.events.publish(ProhibitionViolated(enforcerId, violatorId, action))
        }
    }

    private fun isBannedWeapon(player: ServerPlayer): Boolean =
        player.mainHandItem.typeHolder().`is`(ItemTags.WEAPON_ENCHANTABLE)

    private fun showActivation(context: CastContext, mode: ProhibitionMode, radius: Double) {
        val center = context.origin
        context.level.sendParticles(ParticleTypes.ENCHANT, center.x, center.y + 1.0, center.z, 40, radius * 0.3, 1.0, radius * 0.3, 0.2)
        context.level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.5f)
        context.messaging.actionBar(context.caster, Component.literal("⚖ Активовано заборону: ${mode.label}"))
    }

    private fun jurisdictionRadius(sequence: Int): Double = when {
        sequence == 6 -> 60.0
        sequence == 5 -> 100.0
        sequence < 5 -> 150.0
        else -> 15.0
    }

    private companion object {
        const val DURATION_TICKS = 45 * 20
        const val COOLDOWN_SECONDS = 75
        const val PUNISHMENT_UNLOCK_SEQUENCE = 5
    }
}
