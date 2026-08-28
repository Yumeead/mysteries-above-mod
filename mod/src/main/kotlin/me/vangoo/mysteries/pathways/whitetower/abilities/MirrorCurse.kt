package me.vangoo.mysteries.pathways.whitetower.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.effect.ModParticles
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.roundToInt

/**
 * WhiteTower, Посл. 5 (Магістр містицизму): **Дзеркальне прокляття**.
 *
 * Між вами й ціллю стає латунне дзеркало — і показує їй те, що вона вже собі зробила.
 * Чим більше ціль устигла втратити, тим важче їй дивитись у власне відображення.
 *
 * ## Здібність, що карає за поранення, а не за живучість
 *
 * Шкода тут — [BASE_DAMAGE] плюс [MISSING_HEALTH_FACTOR] від **уже втраченого**
 * здоров'я. Проти повного здоров'я це майже нічого; проти того, кого щойно збили з ніг,
 * це добивання. Механіка навмисно не універсальна: Магістр не б'є першим, він завершує.
 *
 * ## Дзеркало — справжня площина, а не хмарка
 *
 * Плагін умів надіслати частинку в точку, тож «дзеркало» там було розсипом крапель
 * навколо цілі. Тут площина рахується від напрямку погляду: нормаль — лінія до цілі,
 * і сітка порошинок стоїть до кастера **лицем**. Обійти дзеркало збоку означає побачити
 * його з ребра — так, як і мусить виглядати дзеркало.
 */
class MirrorCurse : ActiveAbility(AbilityIdentity.of("whitetower_mirror_curse")) {

    override val displayName: Component = Component.literal("Дзеркальне прокляття")

    override val spiritualityCost: Int = 150

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Латунне дзеркало показує цілі її власні рани — і вони відкриваються знову.\n" +
            "▪ Шкода: ${damageOf(sequence, 0f)} + ${(MISSING_HEALTH_FACTOR * 100).roundToInt()}% " +
            "від утраченого ціллю здоров'я\n" +
            "▪ Умова: ціль мусить бути поранена, у межах ${RANGE.toInt()} бл.",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        val target = context.targeting.lookingAt(RANGE)
            ?: return AbilityOutcome.failed("Дзеркалу нема кого показувати")

        val missing = target.maxHealth - target.health
        if (missing < MIN_WOUND) {
            return AbilityOutcome.failed("${target.name.string} неушкоджений — дзеркало порожнє")
        }

        val damage = damageOf(context.sequence, missing)
        mirror(context, target)
        target.hurtServer(
            context.level,
            context.level.damageSources().indirectMagic(context.caster, context.caster),
            damage.toFloat(),
        )

        context.messaging.actionBar(target.uuid, Component.literal("Ви бачите себе"))
        return AbilityOutcome.done("Дзеркало показало ${target.name.string} його рани (${damage} шкоди)")
    }

    /** Сітка порошинок на півдорозі до цілі, розвернута до кастера лицем. Потім — тріскає. */
    private fun mirror(context: CastContext, target: LivingEntity) {
        val level = context.level
        val from = context.eye
        val to = target.position().add(0.0, target.bbHeight * 0.5, 0.0)
        val normal = to.subtract(from).normalize()
        // Вертикаль світу як опора: горизонтальне ребро дзеркала завжди горизонтальне,
        // навіть коли Магістр дивиться вгору. Погляд точно вздовж осі Y тут неможливий —
        // ціль стоїть на землі, а кастер дивиться на неї.
        val right = normal.cross(UP).normalize()
        val up = right.cross(normal).normalize()
        val centre = from.lerp(to, 0.5)
        val mote = ModParticles.mote(context.effects.pathwayColor)

        for (u in -HALF..HALF) {
            for (v in -HALF..HALF) {
                val point = centre
                    .add(right.scale(u * CELL))
                    .add(up.scale(v * CELL))
                level.sendParticles(mote, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
        shatter(level, centre, mote)
        level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.8f)
    }

    private fun shatter(level: ServerLevel, centre: Vec3, mote: ParticleOptions) {
        level.sendParticles(mote, centre.x, centre.y, centre.z, 24, 0.4, 0.4, 0.4, 0.18)
        level.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y, centre.z, 8, 0.2, 0.2, 0.2, 0.05)
    }

    private fun damageOf(sequence: Sequence, missing: Float): Int =
        scale(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG) +
            (missing * MISSING_HEALTH_FACTOR).roundToInt()

    private companion object {
        const val RANGE = 15.0
        const val COOLDOWN_SECONDS = 25
        /** 2 серця на Посл. 9 — але сама здібність з'являється лише на 5-й. */
        const val BASE_DAMAGE = 4
        const val MISSING_HEALTH_FACTOR = 0.35
        /** Пів серця: інакше «поранений» означало б будь-яку подряпину. */
        const val MIN_WOUND = 1.0f

        val UP: Vec3 = Vec3(0.0, 1.0, 0.0)
        /** Дзеркало 5×5 порошинок із кроком 0,22 бл. — приблизно на зріст гравця. */
        const val HALF = 2
        const val CELL = 0.22
    }
}
