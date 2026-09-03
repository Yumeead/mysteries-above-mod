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

/** When the ability HUD is visible. */
enum class HudMode { ALWAYS, AUTO, NEVER }

/** Which screen edge the HUD is attached to. */
enum class HudAnchor { LEFT_CENTER, RIGHT_CENTER, LEFT_TOP, RIGHT_TOP }

/**
 * Client settings — and the quick slots along with them.
 *
 * There was no configuration in the project at all, so there is nothing to reuse. This is the
 * least that covers the requirement: one `config/mysteries_above_client.json` file, Gson
 * (already on the classpath with the game), reading at startup, writing on change.
 *
 * **There is nothing about game rules here.** Everything below is about how the player sees
 * things and what they use, not about what they are allowed to do. The slot map lives here
 * rather than on the server for one reason: a slot must survive leaving the game, and server
 * storage only arrives in phase 09. A slot is a shortcut to an identity, not a right to cast
 * it: the server verifies the identity it is sent the same way it would any other.
 *
 * The settings screen is not part of this phase (M7); for now there is the file and the keys.
 */
object ClientOptions {

    private val log = LoggerFactory.getLogger("$MOD_ID/options")

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /** `true` while the file is being read: otherwise every assignment would write it back. */
    private var loading = false

    private val file: Path
        get() = FabricLoader.getInstance().configDir.resolve(MOD_ID + "_client.json")

    var hud: HudMode by persisted(HudMode.AUTO)

    var anchor: HudAnchor by persisted(HudAnchor.LEFT_CENTER)

    /** A multiplier on top of the vanilla `guiScale`. */
    var hudScale: Float by persisted(1.0f) { it.coerceIn(MIN_SCALE, MAX_SCALE) }

    /** Panel opacity: 0 — invisible, 1 — solid. */
    var opacity: Float by persisted(0.78f) { it.coerceIn(MIN_OPACITY, 1.0f) }

    /** How long to hold the cast key before the wheel opens (M4). */
    var wheelHoldMillis: Int by persisted(180) { it.coerceIn(60, 600) }

    /** The share of the wheel's radius that casts nothing (M4). */
    var wheelDeadZoneRatio: Float by persisted(0.28f) { it.coerceIn(0.05f, 0.6f) }

    var showCooldownNumbers: Boolean by persisted(true)

    var showSpirituality: Boolean by persisted(true)

    var showDescriptions: Boolean by persisted(true)

    var reducedMotion: Boolean by persisted(false)

    private val bindings = LinkedHashMap<Int, String>()

    /** Quick slot → ability identity. Empty until the player assigns anything. */
    val slots: Map<Int, String> get() = bindings

    /** The identity in the slot, or `null` if the slot is free. */
    fun slot(number: Int): String? = bindings[number]

    /**
     * Write a slot; `null` frees it.
     *
     * This is storage, not rules: "rebind", "clear", "the ability is gone" are the job of
     * `AbilitySlots` (M5), which is what calls this method.
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

    // ── File ────────────────────────────────────────────────────────────────────

    /** Called once at client startup. A missing or broken file means the defaults. */
    fun load() {
        val path = file
        if (!Files.exists(path)) {
            // A file that does not exist is one the player will not edit. The first run creates it.
            save()
            return
        }

        val dto = try {
            gson.fromJson(Files.readString(path), Dto::class.java)
        } catch (failure: IOException) {
            log.warn("Could not read {}: {}", path, failure.message)
            null
        } catch (failure: JsonParseException) {
            log.warn("Corrupt {}: {}", path, failure.message)
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

    /** Written on change. The file is tiny and there are only a handful of changes per session. */
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
            log.warn("Could not write {}: {}", path, failure.message)
        }
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun <T> persisted(initial: T, sanitize: ((T) -> T)? = null): ReadWriteProperty<Any?, T> =
        Persisted(initial, sanitize)

    /**
     * A property that saves itself.
     *
     * Ten fields with the same "normalise, compare, write" is ten places where `save()` can be
     * forgotten. The delegate leaves one.
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
     * The file's shape for Gson — every field optional.
     *
     * Gson builds the object past the constructor, so a `data class` with non-nullable types
     * would lie here: a missing field would become `null` in a field declared non-null.
     * Instead — nullable fields and normalisation on reading.
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

    /** Six slots — exactly as many bindings as `ModKeys` will register (M5). */
    val SLOT_RANGE: IntRange = 1..6

    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 2.0f
    private const val MIN_OPACITY = 0.2f
}
