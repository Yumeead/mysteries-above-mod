package me.vangoo.mysteries.pathways.door.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.ToggleablePassiveAbility
import me.vangoo.mysteries.schedule.TICKS_PER_SECOND
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Door, Посл. 7 (Астролог): **Духовна інтуїція**.
 *
 * Астролог відчуває **чужий намір** — не удар, а мить перед ним: моба, який його обрав,
 * і людину, яка не зводить із нього прицілу.
 *
 * ## Чому не «хтось узяв здібність у руки»
 *
 * У плагіні інтуїція слухала `PlayerItemHeldEvent`: здібності там були предметами в
 * хотбарі, тож перемикання слота і було «замахом». У моді предметів-здібностей немає
 * взагалі — каст іде з клавіші, — тож той сигнал не просто важко відтворити, його не
 * існує. А відтворити його **пізнішим** сигналом (каст уже стався) означало б написати
 * `DecryptPatterns` удруге: та сама подія, той самий радіус, той самий рядок.
 *
 * Тому інтуїція взяла інший сигнал — той, який гра справді має раніше за удар:
 *
 * | джерело | ванільне питання |
 * |---|---|
 * | звір | [Mob.getTarget] — кого він уже обрав |
 * | людина | куди дивиться й наскільки точно ([AIM] — косинус кута) |
 *
 * Обидва відповідають на «на мене зараз полюють», і жодне з них не потребує ні події,
 * ні міксину: це опитування, і воно тут доречне — на «мене взяли на приціл» гачка немає
 * й бути не може.
 *
 * ## Один запис стану на власника, не два виміри
 *
 * Плагін вів `casterId → attackerId → (назва, час)` і чистив прострочене окремим
 * прибиральником у такті. Попередження, однак, читається одне за раз: гравцеві важливо
 * «на мене дивляться», а не список. Тому стан — це один тік останнього попередження на
 * власника, і застаріти йому нема як.
 */
class SpiritualIntuition :
    ToggleablePassiveAbility(AbilityIdentity.of("door_spiritual_intuition")),
    Ticking {

    /** Двічі на секунду: намір визріває повільніше за це. */
    override val tickPeriodTicks: Int = 10

    override val displayName: Component = Component.literal("Духовна інтуїція")

    /** Коли власника попередили востаннє — щоб не гудіти щотакту. */
    private val lastWarned = HashMap<UUID, Long>()

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви відчуваєте, коли на вас полюють.\n" +
            "▪ Звір, що вас обрав, або людина, що не зводить прицілу, у ${RANGE.toInt()} бл.\n" +
            "▪ Попередження не частіше разу на ${QUIET_TICKS / TICKS_PER_SECOND} с",
    )

    override fun onEnable(context: AbilityContext) {
        lastWarned.remove(context.caster.uuid)
        val at = context.origin
        context.level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f,
        )
    }

    override fun onDisable(context: AbilityContext) {
        lastWarned.remove(context.caster.uuid)
        val at = context.origin
        context.level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.0f,
        )
    }

    override fun tick(context: AbilityContext) {
        val owner = context.caster.uuid
        val now = context.scheduling.tick
        if (now - (lastWarned[owner] ?: Long.MIN_VALUE) < QUIET_TICKS) return

        val threat = context.targeting.livingNear(RANGE).firstOrNull { aimsAt(it, context) } ?: return
        lastWarned[owner] = now

        val distance = Math.sqrt(threat.distanceToSqr(context.caster)).roundToInt()
        context.messaging.actionBar(
            context.caster,
            Component.literal("⚠ ${threat.name.string} тримає вас на прицілі — $distance бл."),
        )
        // Тихіше для далеких: інтуїція і є відчуттям відстані.
        val volume = (1.0 - distance / RANGE).coerceAtLeast(0.1).toFloat()
        val at = context.origin
        context.level.playSound(
            null, at.x, at.y, at.z,
            SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS, volume, 1.8f,
        )
    }

    /**
     * Звір відповідає своєю ціллю, людина — напрямком погляду.
     *
     * Поріг [AIM] береться по косинусу, а не по градусах: `dot` двох одиничних векторів
     * і **є** косинусом, тож `acos` тут був би арккосинусом заради того, щоб одразу
     * порівняти назад.
     */
    private fun aimsAt(threat: LivingEntity, context: AbilityContext): Boolean = when (threat) {
        is Mob -> threat.target === context.caster
        is Player -> {
            val toMe = context.origin.subtract(threat.position())
            toMe.lengthSqr() > 1.0e-6 && threat.getViewVector(1.0f).dot(toMe.normalize()) >= AIM
        }
        else -> false
    }

    private companion object {
        const val RANGE = 30.0
        const val QUIET_TICKS = 3 * TICKS_PER_SECOND
        /** ≈18°: ширший конус спрацьовував би на кожного, хто просто йде повз. */
        const val AIM = 0.95
    }
}
