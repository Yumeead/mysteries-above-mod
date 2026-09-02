package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityOutcome
import me.vangoo.mysteries.ability.ActiveAbility
import me.vangoo.mysteries.ability.CastContext
import me.vangoo.mysteries.ability.session
import me.vangoo.mysteries.ability.sustain
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Door, Посл. 9 (Учень): **Відкриття дверей**.
 *
 * Перша й найдешевша сила шляху, і вже вона робить те, чим Двері є: скорочує відстань.
 * Учень підходить до стіни, і стіни на мить не стає — рівно на її товщину.
 *
 * ## Прохід не сам по собі, а **для загону**
 *
 * З Посл. 7 прохід лишається відкритим п'ять секунд, і крізь нього проходить будь-хто,
 * хто підійде. Це та сама межа, що в плагіні, і вона того варта: сила, яка рятує лише
 * себе, і сила, яка виводить крізь стіну загін, — різні сили, а не одна сильніша.
 *
 * Кожен проходить **один раз**: без цього гравець, що лишився стояти біля входу, їздив
 * би туди-сюди щотакту.
 */
class DoorOpening : ActiveAbility(AbilityIdentity.of("door_opening")) {

    override val displayName: Component = Component.literal("Відкриття дверей")

    override val spiritualityCost: Int = 50

    override fun cooldownSeconds(sequence: Sequence): Int = COOLDOWN_SECONDS

    override fun description(sequence: Sequence): Component = Component.literal(
        "Примарні двері в перешкоді перед вами.\n" +
            "▪ Стіна до ${thickness(sequence)} бл. завтовшки\n" +
            (
                if (sequence.level <= SHARED_FROM_SEQUENCE) {
                    "▪ Прохід лишається на ${PORTAL_TICKS / TICKS_PER_SECOND} с — крізь нього " +
                        "пройде будь-хто поблизу\n"
                } else {
                    "▪ З Посл. $SHARED_FROM_SEQUENCE прохід лишається відкритим для інших\n"
                }
                ) +
            "▪ Перепочинок ${COOLDOWN_SECONDS} с",
    )

    override fun cast(context: CastContext): AbilityOutcome {
        if (Vec3(context.look.x, 0.0, context.look.z).lengthSqr() < 1.0e-6) {
            return AbilityOutcome.failed("Дивіться на стіну, а не вгору")
        }
        val forward = horizontal(context.look)

        val wall = wallInFront(context)
            ?: return AbilityOutcome.failed("Ви повинні стояти близько до суцільної стіни")

        val maxThickness = thickness(context.sequence)
        val exit = exitBehind(context.level, wall, forward, maxThickness)
            ?: run {
                context.level.playSound(
                    null, context.origin.x, context.origin.y, context.origin.z,
                    SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 1.0f, 0.5f,
                )
                return AbilityOutcome.failed(
                    "Ця стіна товща за $maxThickness бл. або за нею немає місця",
                )
            }

        // Двері стоять **на лицьовій грані** стіни, а не в центрі блока, у який упав
        // промінь. Інакше рама опиняється всередині каменю — а частинку в товщі блока
        // не видно взагалі, і сила виглядає так, ніби ефекту немає.
        //
        // Y бере саме `wall`, а не `context.caster.y`: обидві половинки прохід мусять
        // стояти на одному рівні. Раніше вхід малювався на висоті ока Учня, а вихід —
        // на висоті блока стіни (`exitBehind` завжди зберігає Y стіни), і якщо гравець
        // стояв не рівно на цілому блоці, двері виявлялись «не рівно одні відносно одних».
        val face = Vec3(wall.x + 0.5, wall.y.toDouble(), wall.z + 0.5).subtract(forward.scale(FACE_CLEARANCE))
        val open = if (context.sequence.level <= SHARED_FROM_SEQUENCE) PORTAL_TICKS else DOOR_FLASH_TICKS
        doorway(context.scheduling, context.level, face, forward, open, context.effects.pathwayColor)
        doorway(
            context.scheduling, context.level,
            exit.add(forward.scale(FACE_CLEARANCE)), forward, open, context.effects.pathwayColor,
        )

        step(context.caster, exit)

        if (context.sequence.level > SHARED_FROM_SEQUENCE) {
            return AbilityOutcome.done("Ви пройшли крізь стіну")
        }

        // Хто вже пройшов. Стан одного касту, а не поле здібності: екземпляр здібності
        // спільний для всіх Учнів на сервері.
        val passed = HashSet<UUID>()
        context.session {
            sustain(periodTicks = WATCH_PERIOD_TICKS, durationTicks = PORTAL_TICKS) { opener ->
                val level = opener.level() as? ServerLevel ?: return@sustain
                level.getEntitiesOfClass(
                    ServerPlayer::class.java,
                    AABB.ofSize(face, ENTRY_RADIUS * 2, ENTRY_RADIUS * 2, ENTRY_RADIUS * 2),
                ).forEach { walker ->
                    if (!passed.add(walker.uuid)) return@forEach
                    step(walker, exit)
                }
            }
        }

        return AbilityOutcome.done("Прохід відкрито — крізь нього пройдуть і інші")
    }

    /** Суцільний блок на відстані витягнутої руки; рідина й трава дверима не стають. */
    private fun wallInFront(context: CastContext): BlockPos? {
        val hit = context.caster.pick(ACTIVATION_RANGE, 1.0f, false) as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null
        return hit.blockPos.takeIf { context.level.getBlockState(it).isSolid }
    }

    /**
     * Перше місце за стіною, де вміщається гравець: під ноги й під голову потрібне
     * повітря. Опори під ногами не питаємо — Учень виходить туди, куди виходить прохід,
     * а не туди, де зручно.
     *
     * Напрямок округлюється до однієї з восьми сітчастих осей **один раз**, а не на
     * кожному кроці глибини. `Math.round(forward.x * depth)` округлював незалежно X і Z
     * при кожному `depth` — і для похилого погляду (не рівно по осі чи діагоналі) це
     * давало нерівномірний дрейф: крок міг піти на клітину вбік, якої в попередньому
     * кроці не було. Вихід опинявся не навпроти входу, а десь збоку, і телепорт цілив
     * туди ж. Фіксований `(dx, dz)`, помножений на `depth`, тримає всі кроки на одній
     * прямій лінії сітки — тій самій, що дивиться Учень.
     */
    private fun exitBehind(
        level: ServerLevel,
        wall: BlockPos,
        forward: Vec3,
        maxThickness: Int,
    ): Vec3? {
        val dx = Math.round(forward.x).toInt()
        val dz = Math.round(forward.z).toInt()
        for (depth in 1..maxThickness) {
            val step = wall.offset(dx * depth, 0, dz * depth)
            if (level.getBlockState(step).isAir && level.getBlockState(step.above()).isAir) {
                return Vec3(step.x + 0.5, step.y.toDouble(), step.z + 0.5)
            }
        }
        return null
    }

    private fun step(walker: ServerPlayer, exit: Vec3) {
        val level = walker.level() as? ServerLevel ?: return
        level.playSound(null, exit.x, exit.y, exit.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1.0f, 1.5f)
        walker.teleportTo(exit.x, exit.y, exit.z)
        level.sendParticles(ParticleTypes.PORTAL, exit.x, exit.y + 1.0, exit.z, 20, 0.3, 0.5, 0.3, 0.1)
    }

    /** Спеціалізація шляху, тож `STRONG`: за це Двері й тримають. */
    private fun thickness(sequence: Sequence): Int =
        scale(BASE_THICKNESS, sequence, SequenceScaler.ScalingStrategy.STRONG)

    private companion object {
        const val BASE_THICKNESS = 10
        const val ACTIVATION_RANGE = 2.5
        const val COOLDOWN_SECONDS = 15
        const val SHARED_FROM_SEQUENCE = 7
        const val PORTAL_TICKS = 5 * TICKS_PER_SECOND
        /** Скільки видно двері, коли прохід одноразовий: рівно щоб побачити, що сталось. */
        const val DOOR_FLASH_TICKS = 2 * TICKS_PER_SECOND
        const val WATCH_PERIOD_TICKS = 5
        const val ENTRY_RADIUS = 3.0
        /** На скільки двері винесені перед гранню блока: у товщі каменю частинок не видно. */
        const val FACE_CLEARANCE = 0.55
    }
}
