package me.vangoo.mysteries.pathways.common

import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.AbilityContext
import me.vangoo.mysteries.ability.PermanentPassiveAbility
import me.vangoo.mysteries.ability.context.on
import me.vangoo.mysteries.ability.grantAttribute
import me.vangoo.mysteries.ability.modifierId
import me.vangoo.mysteries.ability.revokeAttribute
import me.vangoo.mysteries.event.IncomingDamage
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import kotlin.math.roundToInt

/**
 * Спільна пасивка «Бойова майстерність»: зброя в руках слухається краще.
 *
 * ## Бонус шкоди — атрибут, і його **видно**
 *
 * Плагін тримав це числом усередині обробника `EntityDamageByEntityEvent`: гравець бачив
 * лише те, що ворог падає швидше. Модифікатор `ATTACK_DAMAGE` натомість друкується
 * ванількою просто в підказці меча — зворотний зв'язок, якого в плагіні не було взагалі,
 * ціною нуля рядків.
 *
 * ## Парирування стало парируванням
 *
 * У плагіні це був пласкі −20 % вхідної шкоди, поки в руці зброя, — тобто пасивний щит із
 * дивною умовою. Тут умова та, що робить його **майстерністю**: парирувати можна лише те,
 * що ти бачиш. Удар у спину проходить повністю, а розворот до нападника — це вже рішення
 * гравця, а не стан інвентаря.
 *
 * Побічний улов: `directCause` для стріли — сама стріла, тож обернений до лучника Арбітр
 * відбиває постріл. Плагін такого не вмів: він дивився на `getDamager()`, тобто на
 * лучника, який стоїть за сорок блоків десь позаду.
 */
class CombatProficiency : PermanentPassiveAbility(AbilityIdentity.of("common_combat_proficiency")) {

    override val displayName: Component = Component.literal("Бойова майстерність")

    private val modifier = modifierId(identity.id, "attack_damage")

    override fun description(sequence: Sequence): Component = Component.literal(
        "Зброя у ваших руках слухається краще за чужу.\n" +
            "▪ Шкода зброї: +${weaponBonus(sequence)}\n" +
            "▪ Парирування: −${(PARRY_REDUCTION * 100).roundToInt()}% від удару, який ви бачите " +
            "(зброя в головній руці, нападник перед вами)",
    )

    override fun onActivate(context: AbilityContext) {
        val owner = context.caster.uuid
        context.caster.grantAttribute(Attributes.ATTACK_DAMAGE, modifier, weaponBonus(context.sequence))

        context.events.on<IncomingDamage> { damage ->
            val victim = damage.victim
            if (victim.uuid != owner) return@on
            val blow = damage.directCause ?: return@on
            if (!holdsWeapon(victim) || !faces(victim, blow)) return@on

            damage.scale(1.0 - PARRY_REDUCTION)
            clang(victim, blow, damage.attacker)
        }
    }

    override fun onDeactivate(context: AbilityContext) {
        context.caster.revokeAttribute(Attributes.ATTACK_DAMAGE, modifier)
    }

    /**
     * Тег `melee_weapon_enchantable` — ванільна відповідь на питання «це зброя?»: мечі,
     * сокири й тризуб. Свого списку матеріалів мод не тримає, тож зброя з іншого мода,
     * яка чесно ставить тег, парирує теж.
     */
    private fun holdsWeapon(victim: LivingEntity): Boolean =
        victim.getItemInHand(InteractionHand.MAIN_HAND).typeHolder().`is`(ItemTags.MELEE_WEAPON_ENCHANTABLE)

    /** Удар у спину не парирується: майстерність — це те, що ти бачиш. */
    private fun faces(victim: LivingEntity, blow: Entity): Boolean {
        val toBlow = blow.position().subtract(victim.position())
        if (toBlow.lengthSqr() < 1.0e-4) return false
        return victim.getViewVector(1.0f).dot(toBlow.normalize()) >= FACING_DOT
    }

    /** Дзвін і іскра в точці зустрічі — плюс рядок нападнику: він мусить зрозуміти, чому. */
    private fun clang(victim: LivingEntity, blow: Entity, attacker: LivingEntity?) {
        val level = victim.level() as? ServerLevel ?: return
        val contact = victim.eyePosition.add(blow.position().subtract(victim.position()).normalize().scale(0.6))
        level.sendParticles(ParticleTypes.CRIT, contact.x, contact.y, contact.z, 10, 0.1, 0.1, 0.1, 0.25)
        level.playSound(
            null, contact.x, contact.y, contact.z,
            SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.4f,
        )
        // Рядок іде відповідальному за удар, а не тому, що прилетіло: лучник має
        // зрозуміти, чому стріла не спрацювала, а сама стріла читати не вміє.
        (attacker as? ServerPlayer)?.sendSystemMessage(Component.literal("Ваш удар парировано"), true)
    }

    private fun weaponBonus(sequence: Sequence): Double =
        scale(BASE_BONUS_TENTHS, sequence, SequenceScaler.ScalingStrategy.MODERATE) / 10.0

    private companion object {
        /** +2,0 шкоди на Посл. 9 — десятими, бо [scale] цілочисельний. */
        const val BASE_BONUS_TENTHS = 20
        const val PARRY_REDUCTION = 0.35
        /** ~66° від напрямку погляду в кожен бік: конус, а не голка. */
        const val FACING_DOT = 0.4
    }
}
