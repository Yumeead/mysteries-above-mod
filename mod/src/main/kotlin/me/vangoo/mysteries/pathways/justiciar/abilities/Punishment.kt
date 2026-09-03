package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.ProhibitionViolated
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Items
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Justiciar, Seq. 5 (Paladin): **Punishment**.
 *
 * The Judge's own `PowerProhibition` used to reach across to this class through a private static
 * map and a direct call. Here it merely subscribes to [ProhibitionViolated] like any other
 * ability — `PowerProhibition` could disappear from the roster and this class would not notice.
 *
 * The plugin picked a punishment by cycling a hidden mode with repeated casts of this very
 * ability. Since a violation can land at any moment — including while the Paladin's hands are
 * full of `PowerProhibition`'s own mode item — the choice moves to the **offhand**: empty or a
 * sword strikes the offender down, a chain roots them, a book locks their powers.
 */
class Punishment : PermanentPassiveAbility(AbilityIdentity.of("justiciar_punishment")) {

    override val displayName: Component = Component.literal("Покарання")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Спрацьовує сама, коли хтось порушує вашу Заборону. Що в другій руці — те й покарання:\n" +
            "▪ Порожньо або меч — удар із телепорту\n" +
            "▪ Ланцюг — знерухомлення на 30 с\n" +
            "▪ Книга — блокування здібностей на 10 хв\n" +
            "▪ Одна ціль карається не частіше разу на хвилину",
    )

    override fun onActivate(context: AbilityContext) {
        val enforcerId = context.caster.uuid
        val server = context.level.server
        val messaging = context.messaging
        val cooldowns = context.cooldowns

        context.events.on<ProhibitionViolated> { violation ->
            if (violation.enforcerId != enforcerId) return@on
            val enforcer = server.playerList.getPlayer(enforcerId) ?: return@on
            val violator = server.playerList.getPlayer(violation.violatorId) ?: return@on

            val key = enforcerId to violator.uuid
            val now = context.scheduling.tick
            val last = lastPunishedAt[key]
            if (last != null && now - last < PUNISHMENT_COOLDOWN_TICKS) return@on
            lastPunishedAt[key] = now

            messaging.actionBar(violator, Component.literal("⚖ Ви порушили: ${violation.action}"))
            messaging.actionBar(enforcer, Component.literal("⚖ ${violator.name.string} порушив: ${violation.action}"))
            punish(server, messaging, cooldowns, enforcer, violator)
        }
    }

    private fun punish(server: MinecraftServer, messaging: me.vangoo.mysteries.ability.context.Messaging, cooldowns: me.vangoo.mysteries.ability.context.Cooldowns, enforcer: ServerPlayer, violator: ServerPlayer) {
        val level = violator.level()

        when (enforcer.offhandItem.item) {
            Items.IRON_CHAIN -> {
                violator.addEffect(MobEffectInstance(MobEffects.SLOWNESS, IMMOBILIZE_TICKS, 255))
                violator.addEffect(MobEffectInstance(MobEffects.JUMP_BOOST, IMMOBILIZE_TICKS, 250))
                level.sendParticles(ParticleTypes.SOUL, violator.x, violator.y + 1.0, violator.z, 30, 0.4, 0.4, 0.4, 0.05)
                level.playSound(null, violator.x, violator.y, violator.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 1.0f, 0.5f)
                messaging.actionBar(violator, Component.literal("Вас скуто кайданами! Рух неможливий."))
            }

            Items.BOOK -> {
                cooldowns.lockAll(violator.uuid, DISABLE_ABILITIES_SECONDS)
                level.sendParticles(ParticleTypes.WITCH, violator.x, violator.y + 1.0, violator.z, 30, 0.3, 0.3, 0.3, 0.05)
                level.playSound(null, violator.x, violator.y, violator.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 1.0f, 0.6f)
                messaging.actionBar(violator, Component.literal("⚖ Ваші здібності заблоковані на ${DISABLE_ABILITIES_SECONDS / 60} хв!"))
            }

            else -> {
                val spot = violator.position()
                enforcer.teleportTo(spot.x, spot.y + 1.0, spot.z)
                violator.hurtServer(level, level.damageSources().indirectMagic(enforcer, enforcer), TELEPORT_STRIKE_DAMAGE)
                level.sendParticles(ParticleTypes.EXPLOSION, spot.x, spot.y + 1.0, spot.z, 1, 0.0, 0.0, 0.0, 0.0)
                level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0f, 0.8f)
                messaging.actionBar(violator, Component.literal("Вас вдарило покарання!"))
            }
        }
    }

    private companion object {
        const val PUNISHMENT_COOLDOWN_TICKS = 60 * 20L
        const val IMMOBILIZE_TICKS = 30 * 20
        const val DISABLE_ABILITIES_SECONDS = 10 * 60
        const val TELEPORT_STRIKE_DAMAGE = 18.0f

        val lastPunishedAt = ConcurrentHashMap<Pair<UUID, UUID>, Long>()
    }
}
