package me.vangoo.mysteries.pathways.fool.abilities

import me.vangoo.mysteries.ability.AbilityMode
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.IllusionKind
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
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
 * A cycle is reported as [AbilityOutcome.Failed]: nothing happened that costs spirituality or a
 * cooldown, which is exactly what that outcome already means.
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
            val next = context.modes.cycle(context.caster.uuid, this, context.sequence)
            context.level.playSound(
                null, context.origin.x, context.origin.y, context.origin.z,
                SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.7f, 1.5f,
            )
            context.messaging.actionBar(context.caster, Component.literal("🎭 Режим: ${next?.name?.string}"))
            return AbilityOutcome.failed("Режим перемкнуто")
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
            IllusionKind.FOOTSTEPS -> {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GRAVEL_STEP, SoundSource.PLAYERS, 1.2f, 1.0f)
                level.playSound(null, at.x, at.y, at.z, SoundEvents.GRAVEL_STEP, SoundSource.PLAYERS, 1.2f, 0.9f)
                level.sendParticles(ParticleTypes.ASH, at.x, at.y, at.z, 10, 0.3, 0.1, 0.3, 0.0)
            }
            IllusionKind.FIRE -> {
                level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 2.0f, 1.0f)
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 40, 0.6, 0.6, 0.6, 0.02)
                level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 15, 0.5, 0.6, 0.5, 0.0)
            }
        }

        context.messaging.actionBar(context.caster, Component.literal("🎭 ${kind.displayName}"))
        return AbilityOutcome.done(kind.displayName)
    }

    private fun soundProjection(context: CastContext): AbilityOutcome {
        val target = context.targeting.playerLookingAt(TARGET_RANGE)
            ?: return AbilityOutcome.failed("Немає гравця-цілі в радіусі ${TARGET_RANGE.toInt()} бл.")

        // The "from nowhere" trick is the pitch and placement, not a real point in space — the
        // sound is played straight to the target's own ears, same as any other actionBar-scoped cue.
        context.level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.ZOMBIE_AMBIENT, SoundSource.PLAYERS, 1.5f, 1.0f,
        )
        context.level.playSound(
            null, target.x, target.y, target.z,
            SoundEvents.GRAVEL_STEP, SoundSource.PLAYERS, 1.2f, 0.9f,
        )

        context.messaging.actionBar(context.caster, Component.literal("🎭 Ви послали фальшивий звук ${target.name.string}"))
        return AbilityOutcome.done("Фальшивий звук надіслано")
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
    }
}
