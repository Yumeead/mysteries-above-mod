package me.vangoo.mysteries.pathways.whitetower.abilities

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.Ticking
import me.vangoo.mysteries.ability.grantAttribute
import me.vangoo.mysteries.ability.modifierId
import me.vangoo.mysteries.ability.revokeAttribute
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.math.roundToInt

/**
 * WhiteTower, Посл. 9 (Читач): **Загострений розум**.
 *
 * Читач не може дивитись, не читаючи. Досить придивитись (присісти) — і те, на що
 * впав погляд, саме розповідає про себе: скільки в нього життя й броні, чи він
 * Потойбічний і чийого шляху, скільки в цього каменю міцності й чи він світиться.
 *
 * ## Чому це замінює `EnhancedMentalAttributes` (547 рядків)
 *
 * Плагінна пасивка була мішком з-під усього: множник досвіду, шанс покращити
 * зачарування, дублікат зілля біля стійки, «чуття скарбів», «аналіз», «слід». Шість
 * механік, жодної видимої, і чотири окремі такти на них — 5, 10, 40 і 600 тіків.
 * Гравець не бачив нічого, крім чисел, які іноді ставали кращими.
 *
 * Читач має **одну** механіку, і вона показує рівно те, чим Читач є: він знає більше
 * за інших про те, на що дивиться. Знання про чужий шлях і Послідовність — не дрібниця,
 * а найцінніша розвідка в грі, де сила залежить від того й того.
 *
 * Із мішка вижив один пункт, і саме той, що виражається в грі одним рядком: ванільна
 * [Attributes.LUCK] — «уважний знаходить краще», і працює це в кожній таблиці здобичі
 * гри без жодного обробника.
 *
 * ## Чому такт, а не подія
 *
 * Погляд нікуди не «прилітає»: події «гравець подивився на щось» у грі не існує й
 * існувати не може. Це той самий випадок, що й `ResistanceToHorror` — годинник тут
 * доречний. Але цокає він лише під присідання, тож у звичайній грі коштує одного
 * порівняння двічі на секунду.
 */
class EnhancedMind :
    PermanentPassiveAbility(AbilityIdentity.of("whitetower_enhanced_mind")),
    Ticking {

    /** Двічі на секунду: рядок над хотбаром висить довше, тож частіше не треба. */
    override val tickPeriodTicks: Int = 10

    override val displayName: Component = Component.literal("Загострений розум")

    private val luckModifier = modifierId(identity.id, "luck")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Ви не вмієте просто дивитись — ви читаєте.\n" +
            "▪ Присядьте, і ціль під прицілом розповість про себе: життя, броню, шлях і " +
            "Послідовність, якщо перед вами Потойбічний\n" +
            "▪ Удача: +${luck(sequence)} (уважний знаходить краще)",
    )

    override fun onActivate(context: AbilityContext) {
        context.caster.grantAttribute(Attributes.LUCK, luckModifier, luck(context.sequence).toDouble())
    }

    override fun onDeactivate(context: AbilityContext) {
        context.caster.revokeAttribute(Attributes.LUCK, luckModifier)
    }

    override fun tick(context: AbilityContext) {
        // Читання — вибір гравця, а не фон: інакше рядок над хотбаром зайнятий завжди.
        if (!context.caster.isShiftKeyDown) return
        val note = read(context) ?: return
        context.messaging.actionBar(context.caster, note)
    }

    /** Спершу жива ціль: камінь за нею цікавить менше, ніж вона сама. */
    private fun read(context: AbilityContext): Component? =
        context.targeting.lookingAt(RANGE)?.let { creature(context, it) } ?: block(context)

    private fun creature(context: AbilityContext, target: LivingEntity): Component {
        val vitals = "${target.name.string} · ❤ ${target.health.roundToInt()}/${target.maxHealth.roundToInt()}" +
            if (target.armorValue > 0) " · броня ${target.armorValue}" else ""

        val beyonder = context.beyonders.of(target) ?: return Component.literal(vitals)
        val sequenceName = beyonder.pathway.sequenceName(beyonder.sequenceLevel)
        return Component.literal("$vitals · ${beyonder.pathway.name} ${beyonder.sequenceLevel} ($sequenceName)")
    }

    private fun block(context: AbilityContext): Component? {
        val hit = context.caster.pick(RANGE, 1.0f, false) as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null

        val pos = hit.blockPos
        val state = context.level.getBlockState(pos)
        val hardness = state.getDestroySpeed(context.level, pos)
        val light = state.lightEmission

        return Component.literal(
            state.block.name.string +
                " · міцність ${if (hardness < 0) "нескінченна" else "%.1f".format(hardness)}" +
                if (light > 0) " · світло $light" else "",
        )
    }

    private fun luck(sequence: Sequence): Int =
        scale(BASE_LUCK, sequence, SequenceScaler.ScalingStrategy.WEAK)

    private companion object {
        const val RANGE = 24.0
        /** +1 на Посл. 9, +2 біля вершини: удача у ванільці — величина груба. */
        const val BASE_LUCK = 1
    }
}
