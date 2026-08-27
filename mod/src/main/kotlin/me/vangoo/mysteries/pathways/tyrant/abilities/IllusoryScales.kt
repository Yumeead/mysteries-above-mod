package me.vangoo.mysteries.pathways.tyrant.abilities

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.event.IncomingDamage
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import kotlin.math.roundToInt

/**
 * Tyrant, Посл. 9 (Матрос): **Ілюзорна луска**.
 *
 * Прихована під шкірою луска робить Матроса слизьким і твердим на дотик, мов риба.
 * Механічно — опір шкоді, але лише поки тіло у воді, де луска «оживає». На суші ефекту немає.
 *
 * ## Перша здібність на подіях (фаза 06)
 *
 * До появи шва луска була **опитуванням**: двічі на секунду перевіряла воду й накладала
 * ванільний `RESISTANCE`. Це давало три вади, і всі три зникли разом із тактом.
 *
 * | було | стало |
 * |---|---|
 * | ефект видно в списку зілль, і він знімався чужим молоком | нічого на гравця не кладеться |
 * | вихід із води встигав лишити опір на пів секунди | вода перевіряється в мить удару |
 * | `RESISTANCE I` — рівно 20 %, інших чисел ванілька не має | будь-яке число, ось [REDUCTION] |
 *
 * Плюс зник цілий метод `removeOwnScales`: він існував лише щоб відрізнити **свій**
 * накладений опір від випитого гравцем зілля, бо плагін гасив ефект без розбору.
 *
 * ## Підписка тримає UUID, а не гравця
 *
 * Обробник живе довше за один такт, а `ServerPlayer` — ні: після смерті сервер створює
 * новий об'єкт. Тому з контексту береться лише [AbilityContext.caster]`.uuid`, а сам
 * потерпілий читається з події. Те саме правило, що й у `SessionScope.owner`, просто
 * тут воно виражається ще дешевше — жертву подія й так несе.
 */
class IllusoryScales : PermanentPassiveAbility(AbilityIdentity.of("tyrant_illusory_scales")) {

    override val displayName: Component = Component.literal("Ілюзорна луска")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Прихована під шкірою луска робить вас слизьким і твердим, мов риба. " +
            "У воді вона зрізає ${(REDUCTION * 100).roundToInt()}% вхідної шкоди.",
    )

    override fun onActivate(context: AbilityContext) {
        val owner = context.caster.uuid

        context.events.on<IncomingDamage> { damage ->
            val victim = damage.victim
            if (victim.uuid != owner) return@on
            // Луска «оживає» лише у воді — і перевіряється це в мить удару, а не в
            // останньому такті до нього.
            if (!victim.isInWater) return@on

            damage.scale(1.0 - REDUCTION)
            shimmer(victim)
        }
    }

    /** Луска блиснула, погасивши удар. Поки удару немає — її не видно взагалі. */
    private fun shimmer(victim: LivingEntity) {
        val level = victim.level() as? ServerLevel ?: return
        level.sendParticles(
            ParticleTypes.BUBBLE,
            victim.x, victim.y + victim.bbHeight / 2.0, victim.z,
            12, 0.35, 0.45, 0.35, 0.02,
        )
    }

    private companion object {
        /**
         * Частка зрізаної шкоди. Двадцять відсотків — рівно те, що давав ванільний
         * `RESISTANCE I` у попередній редакції: механіку змінено, баланс — ні.
         *
         * Із Послідовністю не росте навмисно: у плагіні луска теж не росла, а Матрос,
         * що піднявся, отримує сильніші версії стійкості окремими здібностями.
         */
        const val REDUCTION = 0.20
    }
}
