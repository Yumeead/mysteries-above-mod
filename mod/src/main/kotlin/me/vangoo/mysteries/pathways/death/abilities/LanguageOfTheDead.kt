package me.vangoo.mysteries.pathways.death.abilities

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.SpiritGuideLore
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.entity.MarionetteEntity
import me.vangoo.mysteries.entity.ModEntities
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.pathways.common.SoulWard
import me.vangoo.mysteries.pathways.death.DEATH_COLOR
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.level.GameType
import java.util.UUID

/**
 * Death, Seq. 6 (Spirit Usher): **Language of the Dead**.
 *
 * "By speaking a mystical language, they can urge a target's Spirit to leave a body, bypassing the
 * physical protection provided by flesh and blood to target the Spirit Body."
 *
 * Armour cannot be "bypassed" with a number, so the soul is taken out of the body whole: the victim
 * becomes a watching spirit tethered beside their own standing body, their powers sealed. The body
 * is a lever, not a target for finishing off — the first blow on it drives the soul back in, and
 * that blow lands on the soul body itself, **through** the armour.
 *
 * ## What the plugin paid Citizens for, the mod already has
 *
 * The plugin spawned a Citizens NPC with the victim's skin. The mod's own marionette entity is a
 * player-shaped `Avatar` that wears any profile — exactly the body Fool's possession leaves behind
 * — so the body here is that, in the victim's face, armour and health.
 *
 * ## The soul always comes back
 *
 * The victim's previous game mode and the body's place are written onto them as a persistent
 * attachment before anything else happens. If the victim, the Usher or the server leaves in the
 * middle, the next join puts the soul back — a player can never be left a spectator.
 */
class LanguageOfTheDead : ActiveAbility(AbilityIdentity.of("death_language_of_the_dead")) {

    override val displayName: Component = Component.literal("Мова мертвих")

    override val spiritualityCost: Int = SpiritGuideLore.LANGUAGE_COST

    override fun cooldownSeconds(sequence: Sequence): Int = SpiritGuideLore.languageCooldownSeconds(sequence)

    /** Souls out of their bodies right now — one cannot be torn out twice. */
    private val outOfBody = HashSet<UUID>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Кілька слів мовою, якої не знають живі, — і душа цілі виходить із тіла.\n" +
            "▪ Дальність ${SpiritGuideLore.languageRange(sequence).toInt()} бл., душа поза тілом ${SpiritGuideLore.languageDurationSeconds(sequence)} с: " +
            "жертва лише дивиться, сили запечатані\n" +
            "▪ Перший удар по тілу повертає душу — і б'є просто по ній, крізь броню\n" +
            "▪ Істота просто ціпеніє",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(SpiritGuideLore.languageRange(context.sequence))
            ?: return AbilityOutcome.failed("Ніхто не в прицілі")
        if (target.uuid in outOfBody || target.hasAttached(RIPPED)) return AbilityOutcome.failed("Ця душа вже поза тілом")
        if (SoulWard.isWarded(target.uuid, context.scheduling.tick)) return AbilityOutcome.failed("Душа цілі підмінена мерцем — кликати нікого")
        if (target !is ServerPlayer && target !is Mob) return AbilityOutcome.failed("У цього тіла немає душі")
        if (target is Mob && target.isNoAi) return AbilityOutcome.failed("У цього тіла немає душі")

        val ticks = SpiritGuideLore.languageDurationSeconds(context.sequence) * TICKS_PER_SECOND
        when (target) {
            is ServerPlayer -> ripPlayer(context, target, ticks)
            is Mob -> ripMob(context, target, ticks)
        }
        val level = context.level
        level.sendParticles(ParticleTypes.SCULK_SOUL, target.x, target.eyeY, target.z, 20, 0.3, 0.4, 0.3, 0.04)
        level.sendParticles(ModParticles.glyph(DEATH_COLOR), target.x, target.y + 1.0, target.z, 12, 0.4, 0.6, 0.4, 0.0)
        level.playSound(null, target.x, target.y, target.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0f, 0.6f)
        return AbilityOutcome.done("☠ Душа ${target.name.string} вирвана з тіла (${ticks / TICKS_PER_SECOND} с)")
    }

    private fun ripPlayer(context: CastContext, victim: ServerPlayer, ticks: Int) {
        val level = victim.level()
        val victimId = victim.uuid
        victim.setAttached(RIPPED, Ripped(victim.gameMode().serializedName, level.dimension().identifier(), victim.x, victim.y, victim.z))

        val body = MarionetteEntity(ModEntities.MARIONETTE, level)
        body.snapTo(victim.x, victim.y, victim.z, victim.yRot, victim.xRot)
        body.setProfile(ResolvableProfile.createResolved(victim.gameProfile))
        body.inheritHealth(victim.maxHealth.toDouble(), victim.health.toDouble())
        body.customName = victim.name
        body.isCustomNameVisible = true
        body.vesselOf = victimId
        EquipmentSlot.entries.forEach { body.setItemSlot(it, victim.getItemBySlot(it).copy()) }
        level.addFreshEntity(body)

        victim.setGameMode(GameType.SPECTATOR)
        context.cooldowns.lockAll(victimId, ticks / TICKS_PER_SECOND)
        context.messaging.tell(victim, Component.literal("☠ Вашу душу вирвано з тіла — ви лише дивитесь"))
        outOfBody += victimId

        var blow: Pair<DamageSource, Float>? = null
        val struck = context.events.on<IncomingDamage> { damage ->
            if (damage.victim !== body) return@on
            damage.cancel()
            if (blow == null) blow = damage.source to damage.amount
        }
        val messaging = context.messaging
        context.session {
            try {
                var elapsed = 0
                while (elapsed < ticks && blow == null && body.isAlive) {
                    val soul = level.server.playerList.getPlayer(victimId) ?: break
                    // The soul stays beside its body: a spectator's free flight would take it anywhere.
                    if (soul.level() !== level || soul.distanceToSqr(body) > TETHER * TETHER) {
                        soul.teleportTo(level, body.x, body.eyeY, body.z, emptySet(), soul.yRot, soul.xRot, true)
                    }
                    waitTicks(1)
                    elapsed++
                }
            } finally {
                struck.cancel()
                outOfBody -= victimId
                val soul = level.server.playerList.getPlayer(victimId)
                if (soul != null) {
                    restore(soul, body)
                    blow?.let { (source, amount) ->
                        // The blow went through flesh to the spirit body: magic, which armour does not stop.
                        val culprit = source.entity
                        soul.hurtServer(soul.level(), soul.damageSources().indirectMagic(source.directEntity ?: culprit ?: soul, culprit), amount)
                    }
                    messaging.actionBar(soul, Component.literal("☠ Душа повернулась у тіло"))
                }
                body.discard()
            }
        }
    }

    /** A creature has nowhere to put its soul: its body simply stands still until struck or released. */
    private fun ripMob(context: CastContext, mob: Mob, ticks: Int) {
        mob.isNoAi = true
        outOfBody += mob.uuid
        var struckOnce = false
        val struck = context.events.on<IncomingDamage> { damage -> if (damage.victim === mob) struckOnce = true }
        context.session {
            try {
                var elapsed = 0
                while (elapsed < ticks && !struckOnce && mob.isAlive) {
                    waitTicks(1)
                    elapsed++
                }
            } finally {
                struck.cancel()
                outOfBody -= mob.uuid
                if (mob.isAlive) mob.isNoAi = false
            }
        }
    }

    companion object {

        private data class Ripped(val mode: String, val dimension: Identifier, val x: Double, val y: Double, val z: Double)

        private val CODEC: Codec<Ripped> = RecordCodecBuilder.create { row ->
            row.group(
                Codec.STRING.fieldOf("mode").forGetter(Ripped::mode),
                Identifier.CODEC.fieldOf("dimension").forGetter(Ripped::dimension),
                Codec.DOUBLE.fieldOf("x").forGetter(Ripped::x),
                Codec.DOUBLE.fieldOf("y").forGetter(Ripped::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Ripped::z),
            ).apply(row, ::Ripped)
        }

        private val RIPPED: AttachmentType<Ripped> by lazy {
            AttachmentRegistry.create(modId("soul_ripped")) { it.persistent(CODEC) }
        }

        private const val TETHER = 4.0

        /** The soul goes back into its body: its own mode, the body's place. */
        private fun restore(soul: ServerPlayer, body: LivingEntity?) {
            val ripped = soul.removeAttached(RIPPED) ?: return
            val server = soul.level().server
            val level: ServerLevel = body?.level() as? ServerLevel
                ?: server.getLevel(ResourceKey.create(Registries.DIMENSION, ripped.dimension))
                ?: server.overworld()
            val x = body?.x ?: ripped.x
            val y = body?.y ?: ripped.y
            val z = body?.z ?: ripped.z
            soul.setGameMode(GameType.byName(ripped.mode, GameType.SURVIVAL) ?: GameType.SURVIVAL)
            soul.teleportTo(level, x, y, z, emptySet(), body?.yRot ?: soul.yRot, soul.xRot, true)
            level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 1.0, z, 16, 0.3, 0.5, 0.3, 0.02)
            level.playSound(null, x, y, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8f, 0.6f)
        }

        /** A soul left out by a logout or a crash goes home on the next join. */
        fun install() {
            // Attachment types register lazily — at mod init, here, never in a unit test that has no Fabric mixins.
            RIPPED
            ServerPlayConnectionEvents.JOIN.register { handler, _, _ -> restore(handler.player, null) }
        }
    }
}
