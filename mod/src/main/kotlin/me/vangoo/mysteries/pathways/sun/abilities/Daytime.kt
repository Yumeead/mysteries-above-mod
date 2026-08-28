package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.SessionEnd
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sessionRunning
import me.vangoo.mysteries.ability.stopSession
import me.vangoo.mysteries.ability.sustain
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LightBlock

/**
 * Sun, Посл. 8 (Прохач Світла): **Денне світло**.
 *
 * Довкола Прохача світло — **справжнє**, а не намальоване: ванільний блок
 * `minecraft:light` рівня 15 їде за ним і освітлює печеру так само, як смолоскип.
 * Тіні відступають, моби поруч не спавняться, і бачать це всі, а не лише він.
 *
 * ## Чому не Нічне бачення
 *
 * Плагін описував цю силу як «світло природно поширюється далі (≈10+ бл)» — і мусив
 * підробляти її ефектом зілля, бо на Spigot ставити блоки під ногами гравця двадцять
 * разів на секунду означало б двадцять оновлень чанка на секунду по всій окрузі.
 *
 * Ванільний `LightBlock` для цього й існує: він **невидимий**, без колізії, без моделі,
 * і рухається одним `setBlock`. Різниця з нічним баченням механічна, а не косметична —
 * нічне бачення показує **вам**, світло змінює **світ**: інші гравці бачать, спавн
 * мобів припиняється, датчики денного світла реагують.
 *
 * ## Один блок, і він прибирається
 *
 * Світло їде за гравцем: новий блок ставиться лише тоді, коли Прохач вийшов зі старого,
 * і попередній того ж такту знімається. Перезаписується **тільки повітря** — чужий
 * блок світло не з'їдає.
 *
 * ponytail: аварійна зупинка сервера між тактами лишить у світі рівно один невидимий
 * блок світла. Прибрати його — звичайне ламання рукою; вартувати це окремим журналом
 * дорожче за саму проблему.
 */
class Daytime : ActiveAbility(AbilityIdentity.of("sun_daytime")) {

    override val displayName: Component = Component.literal("Денне світло")

    /** Каст безкоштовний: ціна — утримання, і гасити світло має бути дарма. */
    override val spiritualityCost: Int = 0

    /**
     * Ціна утримання росте з Послідовністю — і мусить, бо разом із нею росте
     * **регенерація**: 2 очки/с на Посл. 8 проти 6 на Посл. 5 (і 9 при повному
     * засвоєнні). Пласка ціна, взята з плагіна, на Посл. 5 виявилась нижчою за
     * відновлення, тобто сила ставала безкоштовною, а смуга духовності повзла назад
     * до стелі просто під час утримання.
     */
    override fun periodicCost(sequence: Sequence): Int =
        scale(BASE_PERIODIC_COST, sequence, SequenceScaler.ScalingStrategy.MODERATE)

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Справжнє світло їде за вами: ванільний блок світла рівня $LIGHT_LEVEL.\n" +
            "▪ Печера освітлюється по-справжньому — моби поруч не спавняться\n" +
            "▪ Бачать усі, не лише ви\n" +
            "▪ Утримання: ${periodicCost(sequence)} духовності/с; повторний каст гасить",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (context.sessionRunning) {
            context.stopSession()
            return AbilityOutcome.done("Світло згасло")
        }

        val messaging = context.messaging
        // Блок світла — стан у світі, а не в здібності: екземпляр здібності спільний для
        // всіх гравців, тож позиція мусить жити в сесії конкретного Прохача.
        var lit: BlockPos? = null

        context.session {
            try {
                val end = sustain(periodTicks = MOVE_PERIOD_TICKS, upkeep = periodicCost) { priest ->
                    val here = priest.blockPosition().above()
                    if (here != lit) {
                        lit?.let { douse(priest.level() as ServerLevel, it) }
                        lit = kindle(priest.level() as ServerLevel, here)
                    }
                }
                if (end == SessionEnd.EXHAUSTED) {
                    messaging.actionBar(ownerId, Component.literal("Світло згасло: духовність скінчилась"))
                }
            } finally {
                val level = owner?.level() as? ServerLevel
                if (level != null) lit?.let { douse(level, it) }
            }
        }

        context.level.playSound(
            null, context.origin.x, context.origin.y, context.origin.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f,
        )
        return AbilityOutcome.done("Довкола вас настав день")
    }

    /** Ставить світло, якщо там повітря. Повертає позицію або `null`, якщо місце зайняте. */
    private fun kindle(level: ServerLevel, pos: BlockPos): BlockPos? {
        if (!level.getBlockState(pos).isAir) return null
        val state = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL)
        return if (level.setBlock(pos, state, UPDATE_FLAGS)) pos else null
    }

    /** Знімає **своє** світло. Чужий блок на тому місці не чіпається. */
    private fun douse(level: ServerLevel, pos: BlockPos) {
        if (level.getBlockState(pos).block === Blocks.LIGHT) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS)
        }
    }

    private companion object {
        const val BASE_PERIODIC_COST = 10
        const val COOLDOWN_SECONDS = 3
        const val LIGHT_LEVEL = 15
        /** Раз на п'ять тіків: пішки блок міняється рідше, а оновлень чанка вчетверо менше. */
        const val MOVE_PERIOD_TICKS = 5
        /** Клієнту сказати, сусідів не будити: блок світла ні на що не впливає. */
        const val UPDATE_FLAGS = 2
    }
}
