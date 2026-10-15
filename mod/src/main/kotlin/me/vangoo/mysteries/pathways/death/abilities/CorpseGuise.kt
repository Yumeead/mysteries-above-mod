package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.CorpseCollectorLore
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.SpiritMediumLore
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.SelectiveVisibility
import me.vangoo.mysteries.ability.SessionEnd
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.event.IncomingDamage
import me.vangoo.mysteries.event.MobTargeting
import me.vangoo.mysteries.pathways.death.Spirits
import me.vangoo.mysteries.pathways.death.isUndead
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.ItemStack

/**
 * Death, Seq. 7 (Spirit Medium): **Corpse Guise**.
 *
 * "They can also Disguise themselves as a Zombie in order to better endure the erosion of Decay,
 * Cold, Death and other auras."
 *
 * ## A real zombie walks in your place
 *
 * The plugin swapped packets through a disguise library. Here the guise is a vanilla zombie — a
 * shell with no will of its own — that stands exactly where the medium stands every tick, while
 * the medium themselves are taken off every other client: the trick Fool's possession already
 * proved. Everyone else sees, hears and strikes a zombie; a blow on the shell lands on the medium.
 *
 * And because it is a real zombie, the rest is vanilla's: it **catches fire at noon**, and the
 * medium burns with it — unless the medium wears a helmet, which is exactly what saves a zombie
 * too. Iron golems go for it and villagers flee from it. None of that is a line of code here.
 *
 * What the guise grants: the undead lose the medium even after a blow (unlike Gloomy Presence,
 * there is no grudge — they see a corpse, not an offender), and decay, poison, hunger and frost
 * do not take hold.
 */
class CorpseGuise : ActiveAbility(AbilityIdentity.of("death_corpse_guise")) {

    override val displayName: Component = Component.literal("Трупна личина")

    override val spiritualityCost: Int = SpiritMediumLore.GUISE_ACTIVATION_COST

    override fun periodicCost(sequence: Sequence): Int = SpiritMediumLore.GUISE_PERIODIC_COST

    override fun cooldownSeconds(sequence: Sequence): Int = SpiritMediumLore.GUISE_COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви прикидаєтесь мерцем: усі бачать на вашому місці зомбі, а гниль і холод вас обходять.\n" +
            "▪ Тління, отрута, голод і мороз не діють\n" +
            "▪ Нежить на ${CorpseCollectorLore.gloomRadius(sequence).toInt()} бл. губить вас навіть після удару\n" +
            "▪ Під сонцем ви горите, як справжній зомбі, якщо без шолома\n" +
            "▪ Витрата ${SpiritMediumLore.GUISE_PERIODIC_COST} духовності/с, повторне застосування знімає",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.done("☠ Трупна личина спала")
        }

        val caster = context.caster
        val casterId = caster.uuid
        val level = context.level
        // Vanilla discards every zombie on Peaceful by its type alone, persistence or not — the
        // shell would vanish the instant it appeared. A guise is a power, not a monster, so it
        // opts out of despawning altogether.
        val shell = object : Zombie(EntityTypes.ZOMBIE, level) {
            override fun checkDespawn() {}
        }
        shell.snapTo(caster.x, caster.y, caster.z, caster.yRot, caster.xRot)
        shell.isNoAi = true
        shell.setPersistenceRequired()
        shell.setCanPickUpLoot(false)
        level.addFreshEntity(shell)
        shell.addTag(Spirits.EPHEMERAL)

        val guise = context.events.on<IncomingDamage> { damage ->
            when {
                // A blow on the shell is a blow on the one inside it; the sun's fire is not — that
                // reaches the medium through their own burning below.
                damage.victim === shell -> {
                    damage.cancel()
                    val wearer = shell.level().getPlayerByUUID(casterId) as? ServerPlayer ?: return@on
                    if (damage.attacker != null) wearer.hurtServer(wearer.level(), damage.source, damage.amount)
                }
                damage.victim.uuid == casterId && immune(damage) -> damage.cancel()
            }
        }
        val kinship = context.events.on<MobTargeting> { event ->
            if (event.target.uuid == casterId && event.mob.isUndead()) event.deny()
        }
        releaseUndead(caster, context.sequence)

        val messaging = context.messaging
        val scheduling = context.scheduling
        level.sendParticles(ParticleTypes.SCULK_SOUL, caster.x, caster.y + 1.0, caster.z, 20, 0.4, 0.8, 0.4, 0.03)
        level.playSound(null, caster.x, caster.y, caster.z, SoundEvents.ZOMBIE_AMBIENT, SoundSource.PLAYERS, 0.8f, 0.5f)

        context.session {
            try {
                val end = sustain(periodTicks = 1, upkeep = periodicCost) { wearer ->
                    // Someone removed the shell (a command, a void): the guise falls rather than
                    // going on charging for nothing.
                    if (!shell.isAlive) {
                        messaging.actionBar(wearer, Component.literal("☠ Оболонку знищено — личина спала"))
                        scheduling.stop(ownerId, ability.identity)
                        return@sustain
                    }
                    wear(wearer, shell)
                    if (wearer.ticksFrozen > 0) wearer.ticksFrozen = 0
                    if (wearer.hasEffect(MobEffects.HUNGER)) wearer.removeEffect(MobEffects.HUNGER)
                    if (shell.isOnFire) wearer.igniteForSeconds(SUN_FIRE_SECONDS)
                }
                if (end == SessionEnd.EXHAUSTED) owner?.let { messaging.actionBar(it, Component.literal("✗ Духовність вичерпана — личина спадає")) }
            } finally {
                guise.cancel()
                kinship.cancel()
                shell.discard()
                owner?.let { wearer ->
                    wearer.level().players().forEach { if (it !== wearer) SelectiveVisibility.show(wearer, it) }
                    wearer.level().playSound(null, wearer.x, wearer.y, wearer.z, SoundEvents.ZOMBIE_AMBIENT, SoundSource.PLAYERS, 0.6f, 1.4f)
                }
            }
        }
        return AbilityOutcome.done("☠ Ви прикинулись мерцем — інші бачать зомбі")
    }

    /**
     * Every tick: the shell stands where the medium stands and holds what they hold, the medium is
     * hidden from everyone else, and the shell from the medium — their own client would otherwise
     * shove them against it.
     */
    private fun wear(wearer: ServerPlayer, shell: Zombie) {
        shell.snapTo(wearer.x, wearer.y, wearer.z, wearer.yRot, wearer.xRot)
        shell.yHeadRot = wearer.yHeadRot
        shell.yBodyRot = wearer.yBodyRot
        if (wearer.swinging && !shell.swinging) shell.swing(wearer.swingingArm ?: net.minecraft.world.InteractionHand.MAIN_HAND)
        EquipmentSlot.entries.forEach { slot ->
            val worn = wearer.getItemBySlot(slot)
            if (!ItemStack.matches(shell.getItemBySlot(slot), worn)) {
                shell.setItemSlot(slot, worn.copy())
                shell.setDropChance(slot, 0f)
            }
        }
        wearer.level().players().forEach { if (it !== wearer) SelectiveVisibility.hide(wearer, it) }
        SelectiveVisibility.hide(shell, wearer)
    }

    private fun immune(damage: IncomingDamage): Boolean {
        val source = damage.source
        return source.`is`(DamageTypes.WITHER) || source.`is`(DamageTypes.FREEZE) ||
            (source.`is`(DamageTypes.MAGIC) && damage.attacker == null && damage.victim.hasEffect(MobEffects.POISON))
    }

    private fun releaseUndead(caster: ServerPlayer, sequence: Sequence) {
        val radius = CorpseCollectorLore.gloomRadius(sequence)
        caster.level().getEntitiesOfClass(Mob::class.java, caster.boundingBox.inflate(radius)) { it.target === caster && it.isUndead() }
            .forEach { it.target = null }
    }

    private companion object {
        const val SUN_FIRE_SECONDS = 3f
    }
}
