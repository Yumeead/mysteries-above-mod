package me.vangoo.mysteries.event

import me.vangoo.domain.abilities.AbilitySpec
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity

/**
 * Подія мода: те, що сталось у світі й на що здібність має право відповісти.
 *
 * ## Чому це власний тип, а не ванільний гачок
 *
 * У плагіні здібність підписувалась на `Class<? extends org.bukkit.event.Event>` —
 * на **ванільну** подію напряму, а фільтр писала сама. Це давало 46 здібностей, кожна
 * зі своїм `event.getEntity().getUniqueId().equals(casterId)`, і жодного місця, де
 * видно, на що мод узагалі реагує.
 *
 * Тут між грою і здібністю стоїть власний тип. Виграш не в абстракції заради
 * абстракції, а в трьох конкретних речах:
 *
 * 1. **Джерело події — не її форма.** [IncomingDamage] їде з міксину, `ALLOW_DAMAGE`
 *    домішує до неї скасування — здібність цього не бачить і не мусить.
 * 2. **Мутабельне те, що справді мутабельне.** Ванільний `hurtServer` віддає шкоду
 *    аргументом; тут це [IncomingDamage.amount], і його не сплутати з тим, що змінити
 *    не можна.
 * 3. **Список подій мода читається в одному файлі.** Чого тут немає — того мод не ловить.
 *
 * ## Що сюди прийде
 *
 * Подій рівно стільки, скільки їх хтось слухає: подія, на яку не підписано нічого,
 * — це той самий інтерфейс до неіснуючої підсистеми, від якого відмовився контекст.
 * Наступна коштує один клас тут і один рядок у [GameEventBridge].
 *
 * Смерть і респавн навмисно **не** тут: їх слухає лише рантайм (сесії гасне, пасивки
 * перезапускаються), а не здібності, тож вони лишились штатними гачками Fabric у
 * `AbilityServices`. Шина існує для здібностей — усе, що потрібне тільки моду,
 * підписується на Fabric напряму.
 */
interface GameEvent

/**
 * Хтось живий **зараз отримає шкоду** — до броні, зачарувань і решти пом'якшень.
 *
 * Найдорожча подія фази 06 і єдина, під яку довелось писати міксин: `ALLOW_DAMAGE`
 * у Fabric API вміє шкоду **скасувати**, але не змінити — його зворотний виклик
 * повертає `boolean`. У плагіні ж шкоду правлять шістнадцять файлів
 * (`event.setDamage(...)`), тож без зміни числа половина бойових здібностей не
 * переїхала б узагалі.
 *
 * ## Момент
 *
 * Подія — це **намір** завдати шкоди, а не факт. Вона їде з початку `hurtServer`, тож:
 *
 * - невразливість (`isInvulnerableTo`) уже відсіяна — це робить [GameEventBridge];
 * - кадри невразливості (`invulnerableTime`) — ще **ні**: вони перевіряються пізніше
 *   в тілі методу. Здібність, що рахує влучання, порахує й ті, що ванілька відкине;
 * - броня, зачарування, щит, поглинання — теж ще ні. [amount] — «сира» шкода.
 *
 * Те саме застереження стосується й ванільного `ALLOW_DAMAGE`: він фіксується на
 * `isSleeping()`, тобто теж до кадрів невразливості.
 *
 * @property victim хто отримує шкоду; це не обов'язково гравець і не обов'язково Потойбічний
 * @property source ванільне джерело — тип шкоди, винуватець, зброя
 * @property original скільки шкоди було в намірі до того, як його побачили здібності
 */
class IncomingDamage(
    val victim: LivingEntity,
    val source: DamageSource,
    val original: Float,
) : GameEvent {

    /**
     * Скільки шкоди піде далі. Нижче нуля не опускається: від'ємна шкода в `hurtServer`
     * лікує, і здібність, що «зрізала більше, ніж було», не має цього робити мовчки.
     */
    var amount: Float = original
        set(value) {
            field = value.coerceAtLeast(0f)
        }

    /** Скасовану шкоду ванілька не завдасть узагалі — ні звуку, ні кадрів невразливості. */
    var isCancelled: Boolean = false
        private set

    /**
     * Хто вдарив, якщо це взагалі хтось живий. Стріла, вогняна куля й тризуб віддають
     * **стрільця**, а не снаряд — снаряд лежить у [directCause].
     */
    val attacker: LivingEntity? get() = source.entity as? LivingEntity

    /** Що саме долетіло: стріла, снаряд, сама сутність. */
    val directCause: Entity? get() = source.directEntity

    fun cancel() {
        isCancelled = true
    }

    /** Помножити шкоду: `0.75` — зрізати чверть, `1.5` — додати половину. */
    fun scale(factor: Double) {
        amount = (amount * factor).toFloat()
    }

    override fun toString(): String =
        "IncomingDamage(${victim.name.string}, ${source.msgId}, $original → $amount" +
            if (isCancelled) ", скасовано)" else ")"
}

/**
 * Потойбічний **щойно застосував силу** — і це помітно збоку.
 *
 * Друга подія мода й перша, джерело якої не гра, а сам мод: її публікує
 * [me.vangoo.mysteries.ability.AbilityExecutor] після того, як здібність відпрацювала.
 * Міксин тут ні до чого — точка вже наша.
 *
 * Подія існує тому, що на неї підписані дві здібності Дверей: `DecryptPatterns` читає
 * чужі патерни в радіусі, `Record` чекає саме на ту силу, яку записує. Правило те саме,
 * що й для решти шини: подія, на яку не підписано нічого, — інтерфейс до порожнечі.
 *
 * Публікується для [me.vangoo.mysteries.ability.AbilityOutcome.Done] і
 * [me.vangoo.mysteries.ability.AbilityOutcome.Awaiting]: сила, що зірвалась на відсутній
 * цілі, збоку не видно ніяк.
 *
 * @property caster хто кастував; живий об'єкт, бо подія розсилається синхронно з кастом
 * @property spec що саме кастували — половина `core`, тож назву дістає підписник
 */
class AbilityCast(
    val caster: ServerPlayer,
    val spec: AbilitySpec,
) : GameEvent {

    override fun toString(): String = "AbilityCast(${caster.name.string}, ${spec.identity})"
}
