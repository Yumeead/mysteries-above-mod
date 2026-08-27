package me.vangoo.mysteries.ability.context

import me.vangoo.mysteries.event.GameEvent
import kotlin.reflect.KClass

/** Підписка живе, доки її не скасують або доки не спливе оголошений строк. */
const val EVENT_FOREVER: Int = Int.MAX_VALUE

/**
 * На що здібність підписалась і як цю підписку зняти.
 *
 * Тримати його **не обов'язково**: підписка вмирає й сама — разом зі здібністю
 * (див. [Events]). Об'єкт потрібен лише тому, хто хоче відписатись раніше: одноразова
 * реакція гасить себе першим же спрацюванням.
 */
interface Subscription {

    /** `false` — підписка вже знята або її строк вийшов. */
    val isActive: Boolean

    /** Ідемпотентно. */
    fun cancel()
}

/**
 * Підписка на події гри — **від імені цієї здібності цього гравця**.
 *
 * ## Чим це не `IEventContext` плагіна
 *
 * Плагінний інтерфейс приймав `UUID playerId` окремим аргументом, а знімався пакетно —
 * `unsubscribeAll(casterId)`. Наслідок: здібність, яка прибирала за собою, знімала
 * підписки **всіх інших здібностей того самого гравця**. Двадцять здібностей, що
 * поводяться коректно, ламали одна одну саме тим, що поводились коректно.
 *
 * Тут власника не передають — його знає сам сервіс: контекст видано під конкретну пару
 * «гравець + здібність», і чужу підписку зняти нема як. Той самий хід, що вже зроблено
 * для сесій у `Scheduling.session` — ключ береться з контексту, а не з аргументу.
 *
 * ## Хто гасить підписки
 *
 * Здібність не мусить робити нічого. Реєстр знімає їх сам, коли:
 *
 * | коли | де |
 * |---|---|
 * | здібність активують наново (вхід у світ, піднесення) | `PassiveTicker.activate` |
 * | здібність втрачено (демоція, зміна шляху, `forget`) | там само |
 * | гравець вийшов зі світу | `AbilityServices` |
 * | сервер зупинився | `AbilityServices.reset` |
 *
 * Перша ланка важлива окремо: **активація замінює підписки**, точно як перекаст
 * замінює сесію. Пасивка, що підписується в `onActivate`, після смерті й респавну не
 * подвоїться, і писати їй для цього нічого не треба.
 */
interface Events {

    /**
     * Підписатись на події типу [type].
     *
     * @param durationTicks скільки тіків жити; [EVENT_FOREVER] — доки живе сама здібність
     */
    fun <E : GameEvent> subscribe(
        type: KClass<E>,
        durationTicks: Int,
        handler: (E) -> Unit,
    ): Subscription
}

/**
 * Те саме, але тип події видно очима:
 *
 * ```kotlin
 * context.events.on<IncomingDamage> { damage ->
 *     if (damage.victim === context.caster) damage.scale(0.75)
 * }
 * ```
 */
inline fun <reified E : GameEvent> Events.on(
    durationTicks: Int = EVENT_FOREVER,
    noinline handler: (E) -> Unit,
): Subscription = subscribe(E::class, durationTicks, handler)
