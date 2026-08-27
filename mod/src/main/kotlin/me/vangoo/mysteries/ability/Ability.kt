package me.vangoo.mysteries.ability

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.services.SequenceScaler
import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Sequence
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import kotlin.math.ceil

/**
 * Здібність: те, чим вона **є** (успадковано від [AbilitySpec], живе в `core`), плюс
 * те, що вона **робить** (усе решта тут).
 *
 * Порядок виконання касту тримає [AbilityExecutor], а не базовий клас. У плагіні це
 * був `final Ability.execute()` з чергою хуків `canExecute → preExecution → roll →
 * performExecution → postExecution`; половина з них — оркестрація, яка здібності не
 * належить. Тут здібність відповідає лише на два питання: «чи можу зараз» і «що
 * зробити».
 */
abstract class Ability(
    override val identity: AbilityIdentity,
    override val type: AbilityType,
) : AbilitySpec {

    /** Назва для гравця — українською. */
    abstract val displayName: Component

    /** Опис для меню; залежить від Послідовності, бо від неї залежать числа. */
    abstract fun description(sequence: Sequence): Component

    /**
     * Іконка для меню — звичайний [ItemStack].
     *
     * Найдешевше, що лишає двері відчиненими: мод і далі не несе жодної власної
     * текстури (та сама лінія, що вже проведена зіллям — ванільна пляшка з тінтом
     * замість 220 моделей), по дроту стек їде готовим `ItemStack.STREAM_CODEC`, а
     * малюється одним ванільним викликом.
     *
     * Типова іконка — за типом здібності; перекривається однорядково. Колір шляху
     * накладає збирач ростера, якщо здібність не оголосила власного `DYED_COLOR`.
     *
     * Кожен виклик має віддавати **свій** стек: збирач ростера його фарбує.
     */
    open val icon: ItemStack get() = when (type) {
        AbilityType.ACTIVE -> ItemStack(Items.AMETHYST_SHARD)
        AbilityType.TOGGLEABLE_PASSIVE -> ItemStack(Items.ECHO_SHARD)
        AbilityType.PERMANENT_PASSIVE -> ItemStack(Items.PRISMARINE_SHARD)
    }

    override val spiritualityCost: Int get() = 0

    override fun cooldownSeconds(sequence: Sequence): Int = 0

    /** Додаткова умова касту понад володіння, кулдаун і духовність. */
    open fun canCast(context: CastContext): Boolean = true

    /**
     * Ціль, чий опір за Послідовністю треба перекинути перед ефектом. `null` — здібність
     * не змагається (більшість). Опір рахується тільки проти інших Потойбічних.
     */
    open fun contestedTarget(context: CastContext): LivingEntity? = null

    /**
     * Втрата здібності або вимкнення мода: прибрати те, що лишилось у світі.
     *
     * **Сесії гасити не треба** — реєстр гасить їх сам (вихід гравця, зупинка сервера,
     * перекаст), а прибирання за сесією живе в її `finally`. Сюди йде тільки те, що
     * пережило б і скасовану сесію: прикликані істоти, змінені блоки, запечатані предмети.
     */
    open fun cleanUp() {}

    /** Скейлінг сили від Послідовності. Власних множників не писати. */
    protected fun scale(
        base: Int,
        sequence: Sequence,
        strategy: SequenceScaler.ScalingStrategy,
    ): Int = ceil(base * SequenceScaler.calculateMultiplier(sequence.level, strategy)).toInt()

    final override fun equals(other: Any?): Boolean =
        other is Ability && other.identity == identity

    final override fun hashCode(): Int = identity.hashCode()

    override fun toString(): String = "${this::class.simpleName}($identity)"
}

/** Здібність, яку гравець вмикає явно: коштує духовності й лишає кулдаун. */
abstract class ActiveAbility(identity: AbilityIdentity) : Ability(identity, AbilityType.ACTIVE) {

    abstract fun cast(context: CastContext): AbilityOutcome
}

/**
 * Те, що цокає власним тактом.
 *
 * [tickPeriodTicks] — головна відмінність від плагіна. Там `PassiveAbilityScheduler`
 * кликав `tick()` усім і кожен тік, а пасивка, якій вистачало разу на секунду, сама
 * рахувала `if (++runs % 20 == 0)`. Лічильник у полі здібності — це стан, який
 * розсинхронізується з перезаходом гравця; оголошений період — просто число.
 */
sealed interface Ticking {

    /** Період такту в тіках. 1 — щотік, 20 — раз на секунду. */
    val tickPeriodTicks: Int get() = 1

    fun tick(context: AbilityContext)
}

/** Пасивка, що працює завжди, поки здібність є в Потойбічного. */
abstract class PermanentPassiveAbility(
    identity: AbilityIdentity,
) : Ability(identity, AbilityType.PERMANENT_PASSIVE), Ticking {

    /** Вхід у гру або отримання здібності при піднесенні. */
    open fun onActivate(context: AbilityContext) {}

    /** Вихід із гри або втрата здібності. */
    open fun onDeactivate(context: AbilityContext) {}
}

/**
 * Пасивка з перемикачем, який тримає гравець.
 *
 * Якщо сила має гаснути **сама** — вичерпалась духовність, скінчився час, — це не
 * сюди: перемикач лежить поза здібністю, і дотягнутись до нього зсередини не вийде.
 * Такі беруться [ActiveAbility] плюс сесія: тіло сесії саме вирішує, коли згаснути,
 * а `finally` знімає накладене.
 */
abstract class ToggleablePassiveAbility(
    identity: AbilityIdentity,
) : Ability(identity, AbilityType.TOGGLEABLE_PASSIVE), Ticking {

    abstract fun onEnable(context: AbilityContext)

    abstract fun onDisable(context: AbilityContext)
}
