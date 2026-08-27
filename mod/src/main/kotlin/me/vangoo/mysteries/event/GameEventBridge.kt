package me.vangoo.mysteries.event

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity

/**
 * Єдине місце, де гра перетворюється на [GameEvent].
 *
 * ## Порядок пошуку гачка
 *
 * Це головне правило фази 06, і воно не про зручність, а про те, хто платитиме за
 * наступне оновлення Minecraft:
 *
 * 1. **готовий гачок Fabric API** — його поломку розгрібають мейнтейнери Fabric, і
 *    роблять це до того, як мод узагалі побачить нову версію;
 * 2. **власний `Block` чи `Item`** — ванільні перевизначення замість перехоплення чужих;
 * 3. **такт `PassiveTicker`** — рух і присідання це опитування, замасковане під подію;
 * 4. **і лише тоді міксин.**
 *
 * Із 99 хендлерів плагіна на четвертий крок припадає ~11, і в цьому файлі — рівно один.
 *
 * ## Чому шкода все-таки дійшла до міксину
 *
 * `ServerLivingEntityEvents.ALLOW_DAMAGE` існує й **скасувати** шкоду вміє: його
 * зворотний виклик віддає `boolean`. Але шістнадцять файлів плагіна шкоду не скасовують,
 * а **правлять** (`event.setDamage(...)`) — луска зрізає чверть, лють додає половину.
 * Числа `boolean` не несе, і жодного іншого гачка на 26.2 немає: атрибута загального
 * зниження шкоди у ванільці теж не існує (є лише `armor`, `armor_toughness` і
 * `explosion_knockback_resistance`).
 *
 * Тому одна подія збирається з двох джерел:
 *
 * | половина | звідки | що вміє |
 * |---|---|---|
 * | число | міксин, `@ModifyVariable` на аргументі `hurtServer` | змінити [IncomingDamage.amount] |
 * | скасування | `ALLOW_DAMAGE`, готовий гачок | не пустити шкоду далі |
 *
 * ## Чому це не гонка
 *
 * Порядок гарантований не міксином, а **порядком виконання одного методу**: наш гачок
 * стоїть у `HEAD`, а Fabric свій `ALLOW_DAMAGE` фіксує на виклику `isSleeping()` уже в
 * тілі `hurtServer`. Тобто [beforeDamage] завжди відпрацьовує першим, а [pending] його
 * дочитує той самий виклик того самого методу на тому самому потоці.
 *
 * Якщо ванілька вийде з методу між цими двома точками, у [pending] лишиться скасована
 * подія — і саме тому [beforeDamage] обнуляє поле **першим рядком**, а `ALLOW_DAMAGE`
 * читає його з вилученням. Прострочене значення не доживає до чужої шкоди в жодному разі.
 */
object GameEventBridge {

    /**
     * Куди складати події. `null` — мод іще не піднявся: міксин у грі вже є, бо його
     * поставив завантажувач класів, тож мовчазний вихід тут обов'язковий.
     *
     * Зупинку сервера це поле **переживає**, як переживає її сам планувальник: шов
     * ставиться один раз на процес, а не один раз на світ. Обнулити його на
     * `SERVER_STOPPED` означало б, що другий світ одиночної гри лишиться без шкоди.
     */
    private var sink: ((GameEvent) -> Unit)? = null

    /** Скасована шкода, що чекає на свій `ALLOW_DAMAGE`. Живе частку одного виклику. */
    private var pending: IncomingDamage? = null

    fun install(sink: (GameEvent) -> Unit) {
        check(this.sink == null) { "Шов подій уже встановлено" }
        this.sink = sink

        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, _, _ ->
            val parked = pending
            pending = null
            parked == null || parked.victim !== entity || !parked.isCancelled
        }
    }

    /**
     * Зупинка сервера. Прибирається лише те, що належить світу, — недочитане скасування;
     * підписки чистить сам `EventHub`, а гачки лишаються на місці до кінця процесу.
     */
    fun reset() {
        pending = null
    }

    /**
     * Точка приземлення міксину — **єдина**, і кличе її тільки він.
     *
     * Уся логіка живе тут, а не в міксині, свідомо: тіло ін'єкції — один виклик, тож
     * її поломка на новій версії гри коштує п'ять рядків Java, а не переписування
     * обробників.
     *
     * @return скільки шкоди пускати далі; без жодного підписника — рівно [amount]
     */
    @JvmStatic
    fun beforeDamage(
        victim: LivingEntity,
        level: ServerLevel,
        source: DamageSource,
        amount: Float,
    ): Float {
        pending = null

        val sink = sink ?: return amount
        if (amount <= 0f) return amount
        // Невразливість (креатив, `/damage` по невразливій сутності, імунітет до типу шкоди)
        // ванілька відсіє сама — але вже після нашого HEAD. Здібності про таку «шкоду»
        // знати не варто: вона нікуди не піде, а лічильники влучань зіб'є.
        if (victim.isInvulnerableTo(level, source)) return amount

        val event = IncomingDamage(victim, source, amount)
        sink(event)
        if (event.isCancelled) pending = event
        return event.amount
    }
}
