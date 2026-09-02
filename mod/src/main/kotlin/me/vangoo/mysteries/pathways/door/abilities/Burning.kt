package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.BaseFireBlock
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult

/**
 * Door, Посл. 8 (Трюкач): **Підпал**.
 *
 * Найдешевша сила шляху й єдина, що завдає шкоди без цілі: під прицілом істота — горить
 * вона, під прицілом стіна — горить стіна.
 *
 * ## Вогонь ставиться ванільним станом, а не блоком
 *
 * [BaseFireBlock.getState] сама вибирає, який саме вогонь тут доречний — звичайний чи
 * душевний над душевним піском. Плагінне `fireBlock.setType(Material.FIRE)` цього не
 * знало й ставило звичайне полум'я всюди.
 */
class Burning : ActiveAbility(AbilityIdentity.of("door_burning")) {

    override val displayName: Component = Component.literal("Підпал")

    override val spiritualityCost: Int = 20

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Я люблю як воно палає, як горить...\n" +
            "▪ Ціль під прицілом горить ${FIRE_SECONDS} с; стіна займається\n" +
            "▪ Дальність ${RANGE.toInt()} бл., перепочинок ${COOLDOWN_SECONDS} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val ember = ModParticles.mote(context.effects.pathwayColor)

        context.targeting.lookingAt(RANGE)?.let { target ->
            target.igniteForSeconds(FIRE_SECONDS.toFloat())
            context.level.sendParticles(
                ember,
                target.x, target.y + target.bbHeight * 0.5, target.z,
                20, 0.3, 0.4, 0.3, 0.0,
            )
            context.level.playSound(
                null, target.x, target.y, target.z,
                SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0f, 1.2f,
            )
            return AbilityOutcome.done("${target.name.string} горить")
        }

        val hit = context.caster.pick(RANGE, 1.0f, false) as? BlockHitResult
            ?: return AbilityOutcome.failed("Немає що підпалювати")
        if (hit.type != HitResult.Type.BLOCK) return AbilityOutcome.failed("Немає що підпалювати")

        val spot: BlockPos = hit.blockPos.relative(hit.direction)
        if (!context.level.getBlockState(spot).isAir) {
            return AbilityOutcome.failed("Тут нема куди поставити вогонь")
        }

        context.level.setBlockAndUpdate(spot, BaseFireBlock.getState(context.level, spot))
        context.level.sendParticles(
            ember,
            spot.x + 0.5, spot.y + 0.5, spot.z + 0.5,
            15, 0.2, 0.2, 0.2, 0.0,
        )
        context.level.playSound(
            null, spot.x + 0.5, spot.y + 0.5, spot.z + 0.5,
            SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0f, 1.0f,
        )
        return AbilityOutcome.done("Займається")
    }

    private companion object {
        const val RANGE = 5.0
        const val COOLDOWN_SECONDS = 3
        const val FIRE_SECONDS = 3
    }
}
