package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.mysteries.ability.AbilityMode
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.IllusionKind
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.privateSound
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

/**
 * Fool, Seq. 7 (Magician): **Illusion Creation**.
 *
 * A right-click plays whichever illusion is selected; Shift + right-click only cycles the
 * selection, free of charge — the same "sneak switches, plain click acts" split `IllusionCreation`
 * already used in the plugin, ported as-is because nothing about it was a Spigot workaround.
 * A cycle is reported as [AbilityOutcome.Awaiting]: nothing happened that costs spirituality or a
 * cooldown, and the illusion now chosen is on the HUD beside the power's name.
 *
 * ## An illusion is made of the real thing — almost
 *
 * The explosion, the fire and the steps are vanilla's own particles and sounds, and that is the
 * point: an illusion that looked like a power would fool nobody. What gives it away is what it
 * lacks. The fire lights nothing and burns nothing; the steps walk past and leave no one behind;
 * the voice "from nowhere" is played to its one listener alone, from just behind their back, and
 * nobody standing beside them hears a thing.
 */
class IllusionCreation : ActiveAbility(AbilityIdentity.of("fool_illusion_creation")) {

    override val displayName: Component = Component.literal("Створення ілюзій")

    override val spiritualityCost: Int = BASE_COST

    override fun cooldownSeconds(sequence: Sequence): Int = BASE_COOLDOWN_SECONDS

    override fun modes(sequence: Sequence): List<AbilityMode> =
        IllusionKind.entries.map { AbilityMode(it.name.lowercase(), Component.literal(it.displayName)) } +
            AbilityMode(SOUND_MODE, Component.literal("Фальшивий звук (на ціль)"))

    override fun description(sequence: Sequence): Component = Component.literal(
        "Виконує обраний режим ілюзії. Присід + застосування — безкоштовно перемикає режим.\n" +
            "▪ Локальна: вибух, кроки чи пожежа в точці погляду\n" +
            "▪ Фальшивий звук «нізвідки» на гравця під прицілом",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.caster.isShiftKeyDown) {
            context.modes.cycle(context.caster.uuid, this, context.sequence)
            privateSound(context.caster, context.origin, SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, 1.5f)
            return AbilityOutcome.Awaiting()
        }

        val mode = mode(context)
        val kind = IllusionKind.entries.firstOrNull { it.name.lowercase() == mode }
        return if (kind == null) soundProjection(context) else localIllusion(context, kind)
    }

    private fun localIllusion(context: CastContext, kind: IllusionKind): AbilityOutcome {
        val at = illusionLocation(context)
        val level = context.level

        when (kind) {
            IllusionKind.EXPLOSION -> {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.5f, 1.0f)
                level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.4, 0.4, 0.4, 0.0)
                level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 30, 1.0, 1.0, 1.0, 0.0)
            }
            IllusionKind.FOOTSTEPS -> footsteps(context, at)
            IllusionKind.FIRE -> {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 2.0f, 1.0f)
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 40, 0.6, 0.6, 0.6, 0.02)
                level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 15, 0.5, 0.6, 0.5, 0.0)
            }
        }

        return AbilityOutcome.Done()
    }

    /**
     * Someone walks past the spot: [STEPS] steps across the Magician's line of sight, each sounding of
     * the ground it falls on and kicking up a crumb of it — and nobody there.
     */
    private fun footsteps(context: CastContext, at: Vec3) {
        val level = context.level
        val scheduling = context.scheduling
        // Taken out now: the steps fall later, and a context lives one call.
        val look = context.look
        val across = Vec3(-look.z, 0.0, look.x).let { if (it.lengthSqr() < 1e-4) Vec3(1.0, 0.0, 0.0) else it.normalize() }
        val start = at.subtract(across.scale(STEP_LENGTH * STEPS / 2)).subtract(0.0, 0.5, 0.0)
        for (step in 0 until STEPS) {
            scheduling.after(step * STEP_TICKS) {
                // Left foot, right foot: a little to either side of the line walked.
                val side = if (step % 2 == 0) 0.15 else -0.15
                val foot = start.add(across.scale(step * STEP_LENGTH)).add(look.x * side, 0.0, look.z * side)
                val ground = BlockPos.containing(foot.x, foot.y - 0.2, foot.z)
                val state = level.getBlockState(ground)
                level.playSound(null, foot.x, foot.y, foot.z, state.soundType.stepSound, SoundSource.PLAYERS, 0.9f, 1.0f)
                if (!state.isAir) level.sendParticles(BlockParticleOption(ParticleTypes.BLOCK, state), foot.x, foot.y, foot.z, 2, 0.05, 0.0, 0.05, 0.0)
            }
        }
    }

    private fun soundProjection(context: CastContext): AbilityOutcome {
        val target = context.targeting.playerLookingAt(TARGET_RANGE)
            ?: return AbilityOutcome.failed("Немає гравця-цілі в радіусі ${TARGET_RANGE.toInt()} бл.")

        // To the target's ears alone, from a step behind their back: they turn, and there is nobody.
        val behind = target.position().subtract(Vec3(target.lookAngle.x, 0.0, target.lookAngle.z).normalize().scale(BEHIND)).add(0.0, 1.2, 0.0)
        privateSound(target, behind, SoundEvents.ZOMBIE_AMBIENT, 1.5f, 1.0f)
        privateSound(target, behind, SoundEvents.GRAVEL_STEP, 1.2f, 0.9f)
        return AbilityOutcome.Done()
    }

    private fun illusionLocation(context: CastContext): Vec3 {
        val hit = context.caster.pick(LOOK_RANGE, 1.0f, false) as? BlockHitResult
        return if (hit != null) Vec3.atCenterOf(hit.blockPos.above()) else context.eye.add(context.look.scale(8.0))
    }

    private companion object {
        const val BASE_COST = 30
        const val BASE_COOLDOWN_SECONDS = 5
        const val LOOK_RANGE = 20.0
        const val TARGET_RANGE = 20.0
        const val SOUND_MODE = "sound"
        const val STEPS = 6
        const val STEP_TICKS = 6
        const val STEP_LENGTH = 0.7
        const val BEHIND = 2.5
    }
}
