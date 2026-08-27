package me.vangoo.mysteries.client

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import me.vangoo.mysteries.MOD_ID
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Коли HUD здібностей видно. */
enum class HudMode { ALWAYS, AUTO, NEVER }

/** До якого краю екрана притулений HUD. */
enum class HudAnchor { LEFT_CENTER, RIGHT_CENTER, LEFT_TOP, RIGHT_TOP }

/**
 * Клієнтські налаштування — і швидкі слоти разом із ними.
 *
 * Конфігурації в проєкті не було жодної, тож перевикористовувати нема чого. Це
 * найменше, що покриває вимогу: один файл `config/mysteries_above_client.json`,
 * Gson (уже на класпасі разом із грою), читання на старті, запис на зміну.
 *
 * **Тут немає нічого про правила гри.** Усе, що лежить нижче, — про те, як гравець
 * бачить і чим користується, а не про те, що йому дозволено. Мапа слотів живе саме
 * тут, а не на сервері, з однієї причини: слот мусить пережити вихід із гри, а
 * серверне сховище приїде лише у фазі 09. Слот — це ярлик до ідентичності, а не
 * право її кастувати: сервер перевіряє надіслану ідентичність тим самим шляхом, що
 * й будь-яку іншу.
 *
 * Екран налаштувань — не в цій фазі (M7); поки що файл і клавіші.
 */
object ClientOptions {

    private val log = LoggerFactory.getLogger("$MOD_ID/options")

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /** `true`, поки триває читання файла: інакше кожне присвоєння писало б його назад. */
    private var loading = false

    private val file: Path
        get() = FabricLoader.getInstance().configDir.resolve(MOD_ID + "_client.json")

    var hud: HudMode by persisted(HudMode.AUTO)

    var anchor: HudAnchor by persisted(HudAnchor.LEFT_CENTER)

    /** Множник поверх ванільного `guiScale`. */
    var hudScale: Float by persisted(1.0f) { it.coerceIn(MIN_SCALE, MAX_SCALE) }

    /** Непрозорість панелей: 0 — невидимі, 1 — суцільні. */
    var opacity: Float by persisted(0.78f) { it.coerceIn(MIN_OPACITY, 1.0f) }

    /** Скільки тримати клавішу касту, поки відкриється колесо (M4). */
    var wheelHoldMillis: Int by persisted(180) { it.coerceIn(60, 600) }

    /** Частка радіуса колеса, що не кастує нічого (M4). */
    var wheelDeadZoneRatio: Float by persisted(0.28f) { it.coerceIn(0.05f, 0.6f) }

    var showCooldownNumbers: Boolean by persisted(true)

    var showSpirituality: Boolean by persisted(true)

    var showDescriptions: Boolean by persisted(true)

    var reducedMotion: Boolean by persisted(false)

    private val bindings = LinkedHashMap<Int, String>()

    /** Швидкий слот → ідентичність здібності. Порожня, поки гравець нічого не призначив. */
    val slots: Map<Int, String> get() = bindings

    /** Ідентичність у слоті, або `null`, якщо слот вільний. */
    fun slot(number: Int): String? = bindings[number]

    /**
     * Записати слот; `null` — звільнити його.
     *
     * Це сховище, а не правила: «перевісити», «зняти», «здібності більше немає» —
     * робота `AbilitySlots` (M5), який і кличе цей метод.
     */
    fun bindSlot(number: Int, identity: String?) {
        require(number in SLOT_RANGE) { "Слота $number не існує" }

        val changed = if (identity == null) {
            bindings.remove(number) != null
        } else {
            bindings.put(number, identity) != identity
        }
        if (changed) save()
    }

    // ── Файл ────────────────────────────────────────────────────────────────────

    /** Кличеться один раз на старті клієнта. Відсутній чи побитий файл — типові значення. */
    fun load() {
        val path = file
        if (!Files.exists(path)) {
            // Файла, якого не існує, гравець не відредагує. Перший запуск його й створює.
            save()
            return
        }

        val dto = try {
            gson.fromJson(Files.readString(path), Dto::class.java)
        } catch (failure: IOException) {
            log.warn("Не вдалось прочитати {}: {}", path, failure.message)
            null
        } catch (failure: JsonParseException) {
            log.warn("Пошкоджений {}: {}", path, failure.message)
            null
        } ?: return

        loading = true
        try {
            dto.hud?.let { name -> hud = enumOrNull<HudMode>(name) ?: hud }
            dto.hudAnchor?.let { name -> anchor = enumOrNull<HudAnchor>(name) ?: anchor }
            dto.hudScale?.let { hudScale = it }
            dto.opacity?.let { opacity = it }
            dto.wheelHoldMillis?.let { wheelHoldMillis = it }
            dto.wheelDeadZoneRatio?.let { wheelDeadZoneRatio = it }
            dto.showCooldownNumbers?.let { showCooldownNumbers = it }
            dto.showSpirituality?.let { showSpirituality = it }
            dto.showDescriptions?.let { showDescriptions = it }
            dto.reducedMotion?.let { reducedMotion = it }

            bindings.clear()
            dto.slots?.forEach { (key, identity) ->
                val number = key.toIntOrNull()
                if (number != null && number in SLOT_RANGE) bindings[number] = identity
            }
        } finally {
            loading = false
        }
    }

    /** Запис на зміну. Файл дрібний, а змін за сесію — одиниці. */
    fun save() {
        if (loading) return

        val dto = Dto().also {
            it.hud = hud.name
            it.hudAnchor = anchor.name
            it.hudScale = hudScale
            it.opacity = opacity
            it.wheelHoldMillis = wheelHoldMillis
            it.wheelDeadZoneRatio = wheelDeadZoneRatio
            it.showCooldownNumbers = showCooldownNumbers
            it.showSpirituality = showSpirituality
            it.showDescriptions = showDescriptions
            it.reducedMotion = reducedMotion
            it.slots = bindings.entries.associate { (number, identity) -> number.toString() to identity }
        }

        val path = file
        try {
            Files.createDirectories(path.parent)
            Files.writeString(path, gson.toJson(dto))
        } catch (failure: IOException) {
            log.warn("Не вдалось записати {}: {}", path, failure.message)
        }
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun <T> persisted(initial: T, sanitize: ((T) -> T)? = null): ReadWriteProperty<Any?, T> =
        Persisted(initial, sanitize)

    /**
     * Властивість, яка сама себе зберігає.
     *
     * Десять полів з однаковим «нормалізуй, порівняй, запиши» — це десять місць, де
     * можна забути `save()`. Делегат лишає одне.
     */
    private class Persisted<T>(initial: T, private val sanitize: ((T) -> T)?) : ReadWriteProperty<Any?, T> {

        private var value: T = sanitize?.invoke(initial) ?: initial

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            val fresh = sanitize?.invoke(value) ?: value
            if (fresh == this.value) return
            this.value = fresh
            save()
        }
    }

    /**
     * Форма файла для Gson — усі поля необов'язкові.
     *
     * Gson будує об'єкт повз конструктор, тож `data class` із непорожніми типами тут
     * збрехав би: відсутнє поле стало б `null` у полі, оголошеному не-нульовим.
     * Замість того — нульові поля й нормалізація при читанні.
     */
    private class Dto {
        var hud: String? = null
        var hudAnchor: String? = null
        var hudScale: Float? = null
        var opacity: Float? = null
        var wheelHoldMillis: Int? = null
        var wheelDeadZoneRatio: Float? = null
        var showCooldownNumbers: Boolean? = null
        var showSpirituality: Boolean? = null
        var showDescriptions: Boolean? = null
        var reducedMotion: Boolean? = null
        var slots: Map<String, String>? = null
    }

    /** Шість слотів — рівно стільки прив'язок зареєструє `ModKeys` (M5). */
    val SLOT_RANGE: IntRange = 1..6

    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 2.0f
    private const val MIN_OPACITY = 0.2f
}
