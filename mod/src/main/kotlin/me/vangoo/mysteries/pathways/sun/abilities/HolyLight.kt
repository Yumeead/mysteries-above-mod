package me.vangoo.mysteries.pathways.sun.abilities

import me.vangoo.domain.valueobjects.HolyAffinity
import me.vangoo.mysteries.ability.context.Beyonders
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.EntityTypeTags
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity

/**
 * Спільний шар шляху Сонця: кого світло вважає **скверною** і скільки йому за це буде.
 *
 * Спадкоємець плагінного `HolyTargetClassifier`, але зі списку типів лишилось нуль
 * рядків. Там було десять `EntityType` руками — `ZOMBIE`, `HUSK`, `DROWNED`, … — тобто
 * копія ванільного тега, яку доводилось би доповнювати з кожним оновленням гри й яка
 * нічого не знала про нежить з інших модів.
 *
 * `SENSITIVE_TO_SMITE` — це той самий перелік, але **даними**: ванілька тримає його для
 * зачарування «Кара», інші моди дописують туди своє, а датапак може переписати цілком.
 * Питання «що таке нежить» у гри вже є відповідь, і вона краща за нашу.
 *
 * Гравці класифікуються не тегом, а шляхом ([HolyAffinity] у `core`) — це правило
 * проєкту, не гри, і тому живе в правилах.
 */

/**
 * Кому світло **допомагає**.
 *
 * Не «всі, хто не скверна»: між скверною й своїми стоїть решта світу — крипери, павуки,
 * корови. Лікувати крипера, бо він не нежить, — саме та вада, яку дає поділ надвоє там,
 * де категорій три. Своїми вважаються гравці, які не пішли темним шляхом.
 */
internal fun LivingEntity.isFaithful(beyonders: Beyonders): Boolean =
    this is ServerPlayer && !isProfane(beyonders)

/** Темний шлях або нежить: по такій цілі світло б'є на повну. */
internal fun LivingEntity.isProfane(beyonders: Beyonders): Boolean = when (this) {
    is ServerPlayer -> HolyAffinity.isDark(beyonders.of(uuid)?.pathway?.name)
    else -> type.builtInRegistryHolder().`is`(EntityTypeTags.SENSITIVE_TO_SMITE)
}

/**
 * Удар святим світлом. Повертає завдану шкоду — нуль означає, що ціль його не отримала.
 *
 * Множник бере [HolyAffinity]: по скверні повна шкода, по решті — [HolyAffinity]`.NEUTRAL`.
 * Світло не розбирає своїх і чужих навмисно: воно судить за природою цілі, а не за тим,
 * хто з ким дружить. Єдиний виняток — сам кастер.
 */
internal fun smite(
    level: ServerLevel,
    caster: ServerPlayer,
    beyonders: Beyonders,
    target: LivingEntity,
    baseDamage: Int,
): Float {
    if (target === caster) return 0f
    val amount = (baseDamage * HolyAffinity.damageMultiplier(target.isProfane(beyonders))).toFloat()
    if (amount <= 0f) return 0f
    target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), amount)
    return amount
}

/**
 * Знімає з цілі все шкідливе, лишаючи корисне.
 *
 * Категорія ефекту — ванільні дані (`MobEffectCategory`), тож свого списку «поганих
 * ефектів» мод не тримає: чужий мод, який чесно позначив свій дебаф `HARMFUL`, теж
 * очиститься. Копія списку тут була б тією самою помилкою, що й перелік нежиті.
 */
internal fun cleanse(target: LivingEntity): Int {
    val harmful = target.activeEffects
        .filter { it.effect.value().category == MobEffectCategory.HARMFUL }
        .map { it.effect }
    harmful.forEach { target.removeEffect(it) }
    return harmful.size
}
