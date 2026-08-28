package me.vangoo.mysteries.ability

import me.vangoo.mysteries.modId
import net.minecraft.core.Holder
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attribute
import net.minecraft.world.entity.ai.attributes.AttributeModifier

/**
 * Модифікатор атрибута, накладений здібністю.
 *
 * ## Чому це замінює половину плагінних пасивок
 *
 * У Bukkit «сильне тіло» доводилось відігравати ефектом зілля: `SPEED`, `STRENGTH`,
 * `HEALTH_BOOST` на нескінченну тривалість, поновлювані тактом. Це давало три вади
 * одразу — іконка в списку зілль поруч зі справжніми зіллями, зняття чужим молоком і
 * крок сили, прибитий до цілих рівнів ефекту (`SPEED I` — рівно +20 %, інших чисел
 * ванілька не має).
 *
 * Атрибут не має жодної з них: число довільне, іконки немає, молоко його не бере, а
 * бонус до шкоди ще й **сам показується в підказці предмета** — зворотний зв'язок,
 * якого плагін дати не міг узагалі. Те саме, що вже сталось з `IllusoryScales`:
 * механіку змінено, баланс — ні.
 *
 * ## Транзієнтний, а не постійний
 *
 * `addPermanentModifier` запікає модифікатор у файл гравця — і той переживе втрату
 * здібності, зміну шляху й видалення мода. Здібність, яку більше не мають, не має
 * лишати по собі +4 до здоров'я. Тому **тільки** транзієнтні: реєстр накладає їх
 * наново при кожній активації (вхід, респавн, піднесення), а `addOrUpdate` робить
 * повторну активацію ідемпотентною.
 */
fun LivingEntity.grantAttribute(
    attribute: Holder<Attribute>,
    id: Identifier,
    amount: Double,
    operation: AttributeModifier.Operation = AttributeModifier.Operation.ADD_VALUE,
) {
    getAttribute(attribute)?.addOrUpdateTransientModifier(AttributeModifier(id, amount, operation))
}

/** Знімає модифікатор [id]. Мовчить, якщо його не було: втрата здібності — не помилка. */
fun LivingEntity.revokeAttribute(attribute: Holder<Attribute>, id: Identifier) {
    getAttribute(attribute)?.removeModifier(id)
}

/** Ідентифікатор модифікатора: `mysteries_above:<здібність>/<атрибут>`. */
fun modifierId(ability: String, attribute: String): Identifier = modId("$ability/$attribute")
