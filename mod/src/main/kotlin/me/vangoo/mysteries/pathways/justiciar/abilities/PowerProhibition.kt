package me.vangoo.mysteries.pathways.justiciar.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityMode
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.worldText
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.ProhibitionSign
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.pathways.justiciar.Prohibitions
import me.vangoo.mysteries.pathways.justiciar.gavel
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.EntityTypeTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation
import net.minecraft.world.entity.monster.Ghast
import net.minecraft.world.entity.monster.Phantom
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Justiciar, Seq. 6 (Judge): **Prohibition**.
 *
 * "'X' is prohibited here." The Judge pushes an open palm forward and speaks the law in the mystical tongue; it
 * spreads over the ground round them as a ring of runes, and while it stands, what it names is not done within it.
 * The four laws are the novel's own examples (the user's call, 17.11.2026: one Prohibition with modes picked on the
 * wheel; the plugin's bans on killing and on weapons, which the novel never names, and the separate Spawn
 * Prohibition are gone):
 *
 * - **Flight**: whoever flies or glides is brought down, as Ulyssan's wings were weighed down; flying creatures sink.
 * - **Teleportation**: powers that carry a body elsewhere fail ([Prohibitions.forbids]), an ender pearl or a chorus
 *   fruit will not be used, a pearl thrown in from outside falls apart.
 * - **Invisibility**: whoever is hidden by it is shown, and stays outlined a while.
 * - **Undead**: the dead and the unclean cannot abide the ground: they are driven out and weakened while they stay.
 *
 * Every breach of a player is published as [me.vangoo.mysteries.event.ProhibitionViolated]: a Paladin's Punishment
 * takes it from there. The Judge is not bound by their own law.
 */
class PowerProhibition : ActiveAbility(AbilityIdentity.of("justiciar_power_prohibition")) {

    private enum class Law(val sign: ProhibitionSign, val title: String, val word: String) {
        FLIGHT(ProhibitionSign.FLIGHT, "Політ", "Політ тут заборонено!"),
        TELEPORTATION(ProhibitionSign.TELEPORTATION, "Телепортація", "Телепортацію тут заборонено!"),
        INVISIBILITY(ProhibitionSign.INVISIBILITY, "Невидимість", "Невидимість тут заборонено!"),
        UNDEAD(ProhibitionSign.UNDEAD, "Нечисть", "Нечисті тут не бути!"),
    }

    override val displayName: Component = Component.literal("Заборона")

    override val spiritualityCost: Int = 200

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun modes(sequence: Sequence): List<AbilityMode> =
        Law.entries.map { AbilityMode(it.name.lowercase(), Component.literal(it.title)) }

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви вимовляєте закон, і в колі радіусом ${radius(sequence).toInt()} бл. названого більше не робиться ${DURATION_TICKS / 20} с. " +
            "Закон обирається на колесі.\n" +
            "▪ Політ: хто летить чи ширяє, падає; літаючі істоти осідають\n" +
            "▪ Телепортація: сили переміщення, перлини Енду й плоди хорусу не діють\n" +
            "▪ Невидимість: невидимих видно, і вони підсвічені\n" +
            "▪ Нечисть: нечисть виганяє з кола й послаблює\n" +
            "▪ Порушників карає ваше Покарання",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val law = Law.entries.firstOrNull { it.name.lowercase() == mode(context) } ?: Law.FLIGHT
        val caster = context.caster
        val level = context.level
        val centre = caster.position()
        val radius = radius(context.sequence)
        val color = context.effects.pathwayColor

        caster.swing(InteractionHand.MAIN_HAND, true)
        worldText(context.scheduling, level, centre.add(0.0, caster.bbHeight + WORD_HEIGHT, 0.0), Component.literal(law.word).withColor(WORD_COLOR), WORD_SHOWN_TICKS, fadeInTicks = WORD_FADE_TICKS)
        gavel(level, centre, 1.4f, 0.6f)
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 0.7f)
        ModChannels.WORLD_EFFECT.sendTo(
            PlayerLookup.around(level, centre, radius + AUDIENCE),
            WorldEffectPayload.Prohibition(centre, radius.toFloat(), law.sign, DURATION_TICKS, color.packed),
        )

        val standing = Prohibitions.Law(level, centre, radius, law.sign, caster.uuid, color, level.server.tickCount.toLong() + DURATION_TICKS)
        // The law holds once it has spread to its edge.
        val since = mutableMapOf<Int, Int>()
        val start = level.server.tickCount
        context.scheduling.after(WorldEffectPayload.Prohibition.SPREAD_TICKS) { Prohibitions.proclaim(standing) }
        val loop = context.scheduling.every(1, WorldEffectPayload.Prohibition.SPREAD_TICKS) {
            val now = level.server.tickCount
            // One breach a second for each offender: a law that is struck every tick would only be noise.
            fun breach(offender: Entity, deed: String) {
                if (now - (since[offender.id] ?: Int.MIN_VALUE / 2) < BREACH_EVERY_TICKS) return
                since[offender.id] = now
                Prohibitions.breach(standing, offender, deed)
            }
            val box = AABB.ofSize(centre, radius * 2, radius * 2, radius * 2)
            when (law) {
                Law.FLIGHT -> weighDown(standing, box, caster.uuid, ::breach)
                Law.TELEPORTATION -> for (pearl in level.getEntitiesOfClass(ThrownEnderpearl::class.java, box) { standing.covers(it) }) {
                    (pearl.owner as? ServerPlayer)?.takeIf { it.uuid != caster.uuid }?.let { breach(it, "спробував телепортуватися") }
                    pearl.discard()
                }
                Law.INVISIBILITY -> for (hidden in level.getEntitiesOfClass(LivingEntity::class.java, box) { it.hasEffect(MobEffects.INVISIBILITY) && standing.covers(it) }) {
                    if (hidden.uuid == caster.uuid) continue
                    hidden.removeEffect(MobEffects.INVISIBILITY)
                    hidden.addEffect(MobEffectInstance(MobEffects.GLOWING, REVEALED_TICKS, 0, false, false))
                    breach(hidden, "сховався від закону")
                }
                Law.UNDEAD -> for (dead in level.getEntitiesOfClass(LivingEntity::class.java, box) { it.type.builtInRegistryHolder().`is`(EntityTypeTags.SENSITIVE_TO_SMITE) && standing.covers(it) }) {
                    val out = dead.position().subtract(centre).multiply(1.0, 0.0, 1.0).let { if (it.lengthSqr() < 1e-4) Vec3(1.0, 0.0, 0.0) else it.normalize() }
                    dead.deltaMovement = dead.deltaMovement.add(out.scale(UNDEAD_PUSH)).add(0.0, 0.04, 0.0)
                    dead.hurtMarked = true
                    dead.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, false))
                    dead.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, false))
                    breach(dead, "посмів лишитися")
                }
            }
            if (now - start >= DURATION_TICKS) return@every
        }
        context.scheduling.after(DURATION_TICKS) {
            loop.cancel()
            Prohibitions.repeal(standing)
        }
        return AbilityOutcome.Done()
    }

    /** Flight under the law: a flier is set down, a glider's wings fold, a flying creature sinks. */
    private fun weighDown(law: Prohibitions.Law, box: AABB, judge: java.util.UUID, breach: (Entity, String) -> Unit) {
        val level = law.level
        for (player in level.getEntitiesOfClass(ServerPlayer::class.java, box) { law.covers(it) && it.uuid != judge && !it.isSpectator }) {
            var broke = false
            if (player.abilities.flying) {
                player.abilities.flying = false
                player.onUpdateAbilities()
                broke = true
            }
            if (player.isFallFlying) {
                player.stopFallFlying()
                broke = true
            }
            if (broke) {
                player.deltaMovement = Vec3(player.deltaMovement.x * 0.3, (player.deltaMovement.y).coerceAtMost(-WEIGHT), player.deltaMovement.z * 0.3)
                player.hurtMarked = true
                breach(player, "спробував летіти")
            }
        }
        for (flier in level.getEntitiesOfClass(LivingEntity::class.java, box) { flies(it) && law.covers(it) }) {
            flier.deltaMovement = flier.deltaMovement.add(0.0, -SINK, 0.0)
            flier.hurtMarked = true
            if (!flier.onGround()) breach(flier, "летів")
        }
    }

    /** A creature that keeps itself in the air: one that paths through it, or the few that fly without a path. */
    private fun flies(entity: LivingEntity): Boolean =
        entity is Ghast || entity is Phantom || entity is Bat || (entity is Mob && entity.navigation is FlyingPathNavigation)

    private fun radius(sequence: Sequence): Double = if (sequence.level <= 5) 28.0 else 20.0

    private companion object {
        const val DURATION_TICKS = 45 * 20
        const val COOLDOWN_SECONDS = 60
        const val AUDIENCE = 48.0

        const val WORD_HEIGHT = 0.6
        const val WORD_COLOR = 0xF2C14E
        const val WORD_SHOWN_TICKS = 50
        const val WORD_FADE_TICKS = 8

        const val BREACH_EVERY_TICKS = 20
        const val WEIGHT = 0.6
        const val SINK = 0.12
        const val REVEALED_TICKS = 100
        const val UNDEAD_PUSH = 0.35
    }
}
