package me.vangoo.mysteries.ability

import me.vangoo.domain.abilities.AbilitySpec
import me.vangoo.domain.entities.Beyonder
import me.vangoo.domain.valueobjects.Sequence
import me.vangoo.mysteries.ability.context.Beyonders
import me.vangoo.mysteries.ability.context.Cooldowns
import me.vangoo.mysteries.ability.context.Events
import me.vangoo.mysteries.ability.context.Messaging
import me.vangoo.mysteries.ability.context.Scheduling
import me.vangoo.mysteries.ability.context.Targeting
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/**
 * Усе, що здібність бачить, поки працює.
 *
 * ## Чому це не порт `IAbilityContext`
 *
 * У плагіні контекст був **фасадом над Bukkit**: 16 інтерфейсів, які приймали `UUID`
 * і повертали `Location`, бо домену не можна було торкатись `org.bukkit`, а плагін
 * не мав під рукою самої сутності — лише її ідентифікатор і серверний хендл.
 * Звідси 27 методів `IDataContext` виду `getHealth(UUID)`, `getFoodLevel(UUID)`,
 * `isSneaking(UUID)`.
 *
 * Тут ця причина зникла. Здібності живуть у моді, тож [caster] — це живий
 * [ServerPlayer], і `caster.health`, `caster.isInWater`, `caster.addEffect(...)`
 * доступні напряму. **Обгортка навколо одного ванільного виклику в контекст не
 * додається** — це те саме правило, що діяло в плагіні, просто тепер під нього
 * потрапляє вся арифметика гравця й сутностей.
 *
 * Лишається те, чим ванільний Minecraft не володіє: **сервіси самого мода**.
 *
 * ## Каст — це [CastContext], не цей інтерфейс
 *
 * Пасивка цокає без квитка: платити нема за що, гасити нема чого. Тому квиток лежить не
 * тут, а у [CastContext], який бачить лише [ActiveAbility.cast]. Пасивці не доведеться
 * писати `cast!!` — вона про квиток просто не знає.
 *
 * ## Події — сервіс, а не хук
 *
 * [events] — не список ванільних подій, а підписка **від імені цієї здібності цього
 * гравця**: власника видно з контексту, тож зняти чужу підписку нема як, а свої
 * реєстр гасить сам. Це найпомітніша різниця з плагіном, де здібності знімали
 * підписки одна одній через `unsubscribeAll(casterId)`.
 *
 * ## Що ще сюди прийде
 *
 * Контекст росте разом із механікою, а не наперед: інтерфейс до підсистеми, якої
 * ще немає, — це інтерфейс ні до чого.
 *
 * | що | фаза |
 * |---|---|
 * | `effects` — словник фігур із частинок | 07 |
 * | `ui` — екрани вибору й ввід | 11 |
 * | `summons` — data-driven істоти | 12 |
 */
interface AbilityContext {

    // ── Кастер ──────────────────────────────────────────────────────────────────

    val caster: ServerPlayer

    /** Доменна половина кастера: Послідовність, духовність, глузд, список здібностей. */
    val beyonder: Beyonder

    /** Здібність, яка зараз працює. Ключ її сесії й джерело її ж цін. */
    val ability: AbilitySpec

    val level: ServerLevel get() = caster.level()

    val sequence: Sequence get() = beyonder.sequence

    val origin: Vec3 get() = caster.position()

    val eye: Vec3 get() = caster.eyePosition

    val look: Vec3 get() = caster.getViewVector(1.0f)

    // ── Сервіси мода ────────────────────────────────────────────────────────────

    val beyonders: Beyonders
    val cooldowns: Cooldowns
    val targeting: Targeting
    val messaging: Messaging
    val scheduling: Scheduling
    val events: Events
}

/**
 * Контекст **одного касту**: те саме, що [AbilityContext], плюс квиток.
 *
 * Вужчий тип рівно там, де він потрібен — у [ActiveAbility.cast] та її вартових. Усе
 * решта (такт пасивки, увімкнення тогла) працює на [AbilityContext] і квитка не бачить.
 */
interface CastContext : AbilityContext {

    /**
     * Квиток цього касту. [PendingCast.complete] списує духовність, нараховує
     * засвоєння і ставить кулдаун; не викликати — і каст безкоштовний.
     *
     * Активна здібність, що повернула [AbilityOutcome.Done], нічого з ним не робить:
     * гасить [AbilityExecutor]. Здібність, що чекає вибору гравця, **тримає саме цей
     * об'єкт** (не весь контекст — той несе ідентичність одного касту) і гасить його,
     * коли ефект справді стався.
     */
    val cast: PendingCast
}

// ── Сесії ───────────────────────────────────────────────────────────────────────

/**
 * Відкриває сесію цієї здібності для цього гравця, гасячи попередню.
 *
 * Ключ береться з контексту, тож переплутати чужу сесію зі своєю не вийде. У тіло
 * контекст **не передається**: він несе живого `ServerPlayer` і живе рівно один виклик,
 * а сесія переживає і респавн, і зміну виміру — гравця вона питає наново щотакту.
 */
fun AbilityContext.session(block: suspend SessionScope.() -> Unit): Session =
    scheduling.session(caster.uuid, ability, block)

/** Чи цокає зараз сесія цієї здібності в цього гравця. */
val AbilityContext.sessionRunning: Boolean
    get() = scheduling.isRunning(caster.uuid, ability.identity)

/** Гасить сесію цієї здібності. `true` — вона була жива. */
fun AbilityContext.stopSession(): Boolean = scheduling.stop(caster.uuid, ability.identity)
