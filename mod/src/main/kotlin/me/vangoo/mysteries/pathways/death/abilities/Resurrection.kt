package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.SpiritGuideLore
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.pathways.death.DEATH_COLOR
import me.vangoo.mysteries.pathways.death.DeathTraces
import me.vangoo.mysteries.pathways.death.Retinue
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.EntityTypeTags
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile

/**
 * Death, Seq. 6 (Spirit Usher): **Resurrection**.
 *
 * "They can turn corpses into living skeletons and Zombies; however, this kind of Resurrection does
 * not allow the reanimated corpse to have any will or vitality." The body is not taken from thin
 * air: a death leaves one ([DeathTraces]) for five minutes, and raising it spends it — one body,
 * one servant.
 *
 * ## A body rises as what vanilla would make of it
 *
 * The plugin had two outcomes: skeleton for skeletons and players, zombie for everything else. The
 * game already knows a whole family of the walking dead, so a body rises as its own: a villager
 * as a zombie villager, a piglin as a zombified piglin, a horse as a zombie horse, a camel as a
 * camel husk, a nautilus as a zombie nautilus, the drowned from the water, the husk from the
 * desert, a skeleton as the skeleton it was. A player rises a zombie **wearing their own face**.
 *
 * Sneak + cast lets the whole retinue crumble — the same gesture as letting the Gravedigger's
 * spirits go.
 */
class Resurrection : ActiveAbility(AbilityIdentity.of("death_resurrection")) {

    override val displayName: Component = Component.literal("Воскресіння")

    override val spiritualityCost: Int = SpiritGuideLore.RESURRECTION_COST

    override fun cooldownSeconds(sequence: Sequence): Int = SpiritGuideLore.resurrectionCooldownSeconds(sequence)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Тіло, що лежить поруч, устає без волі й без життя: ходить за вами й б'є лише за наказом.\n" +
            "▪ Тіло в межах ${RAISE_RADIUS.toInt()} бл., лежить ${DeathTraces.CORPSE_TICKS / 1200} хв після смерті\n" +
            "▪ Устає тим, чим його зробила б сама смерть: утопленим, кадавром, зомбі-жителем, конем-зомбі…\n" +
            "▪ Слуг загалом: до ${Retinue.cap(sequence)}\n" +
            "▪ Присід + застосування розпускає всіх",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        if (caster.isShiftKeyDown) {
            return if (Retinue.release(caster)) AbilityOutcome.Awaiting(Component.literal("☠ Почет розсипався на порох"))
            else AbilityOutcome.failed("Слуг немає")
        }
        val sequence = context.sequence
        if (Retinue.room(caster, sequence) == 0) {
            val cap = Retinue.cap(sequence)
            return AbilityOutcome.failed("Більше нежиті вам не підкоряється ($cap/$cap)")
        }
        val corpse = DeathTraces.corpses(context.level.dimension(), context.origin, RAISE_RADIUS, caster.uuid)
            .minByOrNull { it.pos.distanceToSqr(context.origin) }
            ?: return AbilityOutcome.failed("Поблизу немає тіл")

        val servant = raise(context.level, corpse) ?: return AbilityOutcome.failed("Тіло не піддалось")
        corpse.raised = true
        Retinue.enroll(caster, servant)

        val level = context.level
        val at = corpse.pos
        level.sendParticles(ModParticles.glyph(DEATH_COLOR), at.x, at.y + 1.0, at.z, 24, 0.3, 1.0, 0.3, 0.0)
        level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.5, at.z, 14, 0.3, 0.5, 0.3, 0.02)
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 0.9f, 0.5f)
        return AbilityOutcome.done("☠ ${corpse.name} встає з мертвих (${Retinue.size(caster)}/${Retinue.cap(sequence)})")
    }

    private fun raise(level: ServerLevel, corpse: DeathTraces.Trace): Mob? {
        val mob = risesAs(corpse).create(level, EntitySpawnReason.MOB_SUMMONED) as? Mob ?: return null
        mob.snapTo(corpse.pos.x, corpse.pos.y + 0.1, corpse.pos.z, level.random.nextFloat() * 360f, 0f)
        mob.customName = Component.literal("Слуга: ${corpse.name}")
        mob.isCustomNameVisible = true
        corpse.profile?.let { face ->
            mob.setItemSlot(EquipmentSlot.HEAD, ItemStack(Items.PLAYER_HEAD).apply { set(DataComponents.PROFILE, ResolvableProfile.createResolved(face)) })
            mob.setDropChance(EquipmentSlot.HEAD, 0f)
        }
        level.addFreshEntity(mob)
        return mob
    }

    /** What death itself makes of this body — vanilla's own family of the walking dead. */
    private fun risesAs(corpse: DeathTraces.Trace): EntityType<*> {
        val type = corpse.type
        return when {
            type == EntityTypes.WITHER || type == EntityTypes.ENDER_DRAGON -> EntityTypes.ZOMBIE
            type.builtInRegistryHolder().`is`(EntityTypeTags.UNDEAD) -> type
            type == EntityTypes.VILLAGER -> EntityTypes.ZOMBIE_VILLAGER
            type == EntityTypes.PIGLIN || type == EntityTypes.PIGLIN_BRUTE -> EntityTypes.ZOMBIFIED_PIGLIN
            type == EntityTypes.HORSE || type == EntityTypes.DONKEY || type == EntityTypes.MULE -> EntityTypes.ZOMBIE_HORSE
            type == EntityTypes.CAMEL -> EntityTypes.CAMEL_HUSK
            type == EntityTypes.NAUTILUS -> EntityTypes.ZOMBIE_NAUTILUS
            corpse.drowned -> EntityTypes.DROWNED
            corpse.scorched -> EntityTypes.HUSK
            else -> EntityTypes.ZOMBIE
        }
    }

    private companion object {
        /** The rite is done over the body: searching the whole neighbourhood is a necropolis, not a resurrection. */
        const val RAISE_RADIUS = 6.0
    }
}
