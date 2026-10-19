package me.vangoo.mysteries.pathways.death.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.domain.valueobjects.SpiritGuideLore
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.creature.Creatures
import me.vangoo.mysteries.pathways.death.Retinue
import me.vangoo.mysteries.pathways.death.Spirits
import me.vangoo.mysteries.pathways.death.payBranch
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.monster.Vex
import net.minecraft.world.phys.HitResult

/**
 * Death, Seq. 6 (Spirit Usher): **Pact with Spirits** — and the voice the whole retinue obeys.
 *
 * ## Orders by gaze, not by a chest
 *
 * The plugin opened a chest of five icons for orders. An order here is where the Usher looks when
 * they speak it:
 *
 * | looking at | order |
 * |---|---|
 * | a living creature | the retinue goes for it |
 * | one of their own servants | that one crumbles — dismissed alone, not the whole retinue |
 * | the ground | the retinue guards that spot |
 * | the open sky | the retinue returns to heel |
 * | *(crouching)* | the retinue sinks into the shadows, or rises back out of them |
 *
 * Orders are free and leave no cooldown: a command is not a summoning.
 *
 * ## The pact
 *
 * "Obtain help from certain Spirit World creatures." The Living Shadow, the Death Knight, the Lake
 * Goddess and the rest roam the Nether as creatures of the engine, with no will of their own: they
 * wait for someone to give them orders. Look at a free one up close and the Usher strikes a pact —
 * at the being's own price — and it joins the retinue for good. A plain vex is a spirit too, and
 * the cheapest one to bargain with.
 *
 * The plugin sold these beings from a menu; here the Usher has to **find** one first, which is
 * what the plugin's own final design already asked for (its beings wandered the Nether too).
 */
class SpiritPact : ActiveAbility(AbilityIdentity.of("death_spirit_pact")) {

    override val displayName: Component = Component.literal("Домовленість з духами")

    /** Orders are free; the pact pays its own price through [payBranch]. */
    override val spiritualityCost: Int = 0

    override fun cooldownSeconds(sequence: Sequence): Int = SpiritGuideLore.pactCooldownSeconds(sequence)

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ваші слуги слухають, куди ви дивитесь, коли наказуєте.\n" +
            "▪ На істоту — убити; на свого слугу — відпустити його; на землю — стерегти місце; у небо — за мною\n" +
            "▪ Присід — сховати слуг у тінях або повернути\n" +
            "▪ Вільний дух чи істота Світу Духів упритул (${CLAIM_RANGE.toInt()} бл.) — домовитись: від ${SpiritGuideLore.LIVING_SHADOW_COST} духовності, стає слугою",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val caster = context.caster
        val owner = caster.uuid
        if (caster.isShiftKeyDown) {
            if (Retinue.size(caster) == 0) return AbilityOutcome.failed("Слуг немає")
            val hidden = Retinue.toggleHidden(caster)
            return AbilityOutcome.Awaiting(Component.literal(if (hidden) "☠ Слуги розчинились у тінях" else "☠ Слуги вийшли з тіней"))
        }

        val looked = context.targeting.lookingAt(COMMAND_RANGE)
        // A spirit someone already holds (a Gravedigger's companion) is not free to bargain with.
        if (looked is Mob && (looked is Vex || Spirits.isSpiritWorldBeing(looked)) && !Retinue.isServant(looked) &&
            Spirits.EPHEMERAL !in looked.entityTags() && looked.distanceTo(caster) <= CLAIM_RANGE
        ) {
            return pact(context, looked)
        }

        if (Retinue.of(owner).isEmpty()) return AbilityOutcome.failed("Слуг немає — нікому наказувати")
        return when {
            looked != null && Retinue.isOwnedBy(owner, looked) -> dismiss(looked)
            looked != null -> attack(context, looked)
            else -> {
                val hit = caster.pick(COMMAND_RANGE, 1.0f, false)
                if (hit.type == HitResult.Type.BLOCK) {
                    Retinue.order(owner, Retinue.Order.Guard(hit.location))
                    context.level.sendParticles(ParticleTypes.SOUL, hit.location.x, hit.location.y + 0.2, hit.location.z, 12, 0.6, 0.05, 0.6, 0.01)
                    AbilityOutcome.Awaiting(Component.literal("☠ Почет стереже це місце"))
                } else {
                    Retinue.order(owner, Retinue.Order.Follow)
                    AbilityOutcome.Awaiting(Component.literal("☠ Слуги йдуть за вами"))
                }
            }
        }
    }

    private fun attack(context: CastContext, victim: LivingEntity): AbilityOutcome {
        Retinue.order(context.caster.uuid, Retinue.Order.Attack(victim.uuid))
        context.level.playSound(null, victim.x, victim.y, victim.z, SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.0f, 0.5f)
        return AbilityOutcome.Awaiting(Component.literal("☠ Слуги кинулись на ${victim.name.string}"))
    }

    private fun dismiss(servant: LivingEntity): AbilityOutcome {
        val name = servant.name.string
        val level = servant.level() as net.minecraft.server.level.ServerLevel
        level.sendParticles(ParticleTypes.SCULK_SOUL, servant.x, servant.y + 0.5, servant.z, 16, 0.3, 0.5, 0.3, 0.02)
        level.playSound(null, servant.x, servant.y, servant.z, SoundEvents.SOUL_SAND_BREAK, SoundSource.HOSTILE, 0.8f, 0.6f)
        Retinue.absorb(Retinue.ownerOf(servant) ?: return AbilityOutcome.failed("Це не ваш слуга"), servant as net.minecraft.world.entity.Mob)
        return AbilityOutcome.Awaiting(Component.literal("☠ $name повернувся в землю"))
    }

    private fun pact(context: CastContext, spirit: Mob): AbilityOutcome {
        val caster = context.caster
        if (Retinue.room(caster, context.sequence) == 0) return AbilityOutcome.failed("Слуг уже стільки, скільки ви втримаєте")
        val price = priceOf(spirit)
        if (!context.payBranch(price, SpiritGuideLore.pactCooldownSeconds(context.sequence))) {
            return AbilityOutcome.failed("Недостатньо духовності, щоб домовитись: потрібно $price")
        }
        // A spirit under a pact keeps its own flight and its own techniques — it only loses the right to choose.
        Retinue.enroll(caster, spirit)
        (spirit as? Vex)?.let(Spirits::hold)
        context.level.playSound(null, spirit.x, spirit.y, spirit.z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0f, 0.5f)
        return AbilityOutcome.done("☠ Дух погодився вам служити (${Retinue.size(caster)}/${Retinue.cap(context.sequence)})")
    }

    /**
     * What a being asks for its service: the plugin's four prices for the four it named, and for
     * the rest of the Spirit World a price that grows with how much of it there is to bargain with.
     */
    private fun priceOf(spirit: Mob): Int = when (Creatures.keyOf(spirit)?.identifier()?.path) {
        null, "living_shadow" -> SpiritGuideLore.LIVING_SHADOW_COST
        "shadow_swallowing_python" -> SpiritGuideLore.SHADOW_PYTHON_COST
        "death_knight" -> SpiritGuideLore.DEATH_KNIGHT_COST
        "lake_goddess" -> SpiritGuideLore.LAKE_GODDESS_COST
        else -> (spirit.maxHealth * PRICE_PER_HEALTH).toInt().coerceIn(SpiritGuideLore.LIVING_SHADOW_COST, SpiritGuideLore.LAKE_GODDESS_COST)
    }

    private companion object {
        const val PRICE_PER_HEALTH = 0.6f

        /** A pact is a conversation at arm's length, not a spell across a field. */
        const val CLAIM_RANGE = 8.0
        const val COMMAND_RANGE = 32.0
    }
}
