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
 * A named set of abilities with hotkeys of its own: switch the group and the same keys cast
 * something else (a fighting rotation, a travelling one).
 *
 * [abilities] `null` is the built-in "all" group: it shows the whole roster and cannot be
 * deleted. A named group keeps identities the roster may not hold right now (a borrowed power,
 * a demotion): they are filtered out when shown, never pruned, so a temporary change of the
 * roster does not wipe a layout the player built by hand.
 *
 * Plain data with no game types, so its rules are checked by a unit test.
 */
class AbilityGroup(
    var name: String,
    val abilities: MutableList<String>?,
    /** `InputConstants.Key` name (`key.keyboard.c`, `key.mouse.4`) → ability identity. */
    val hotkeys: MutableMap<String, String> = LinkedHashMap(),
) {
    val isAll: Boolean get() = abilities == null

    fun contains(identity: String): Boolean = abilities?.contains(identity) ?: true

    fun keyOf(identity: String): String? = hotkeys.entries.firstOrNull { it.value == identity }?.key

    /** One key casts one ability, and one ability has one key: binding moves both. */
    fun bind(key: String, identity: String) {
        unbind(identity)
        hotkeys[key] = identity
    }

    fun unbind(identity: String) {
        hotkeys.values.removeAll { it == identity }
    }

    /** Add or remove; a removed ability loses its key in this group too. */
    fun toggle(identity: String) {
        val list = abilities ?: return
        if (!list.remove(identity)) list.add(identity) else unbind(identity)
    }
}

/**
 * Client settings — and the ability groups along with them.
 *
 * There was no configuration in the project at all, so there is nothing to reuse. This is the
 * least that covers the requirement: one `config/mysteries_above_client.json` file, Gson
 * (already on the classpath with the game), reading at startup, writing on change.
 *
 * **There is nothing about game rules here.** Everything below is about how the player sees
 * things and what they use, not about what they are allowed to do. The slot map lives here
 * rather than on the server for one reason: a layout must survive leaving the game and is
 * about how the player works, not what they may do. A hotkey is a shortcut to an identity, not a
 * right to cast it: the server verifies the identity it is sent the same way it would any other.
 *
 * Groups and hotkeys are edited in `AbilityBookScreen`; the rest lives in the file only.
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

    /** A multiplier on top of the vanilla `guiScale`, for the HUD and the wheel alike. */
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

    /** Turns the HUD fade into an instant switch; the cooldown veil stays, it shows state. */
    var reducedMotion: Boolean by persisted(false)

    /** Index 0 is always the built-in "all" group. */
    val groups: MutableList<AbilityGroup> = mutableListOf(AbilityGroup("", null))

    var activeGroup: Int by persisted(0)

    /** The active group; an index left dangling by a deletion falls back to "all". */
    val group: AbilityGroup get() = groups.getOrElse(activeGroup) { groups[0] }

    /** Groups are mutable data: edit them inside, and the file is written once after. */
    fun editGroups(change: () -> Unit) {
        change()
        if (activeGroup !in groups.indices) activeGroup = 0
        save()
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

            dto.groups?.let { saved ->
                groups.clear()
                groups += AbilityGroup("", null)
                saved.forEachIndexed { index, group ->
                    val hotkeys = LinkedHashMap(group.hotkeys.orEmpty())
                    if (index == 0) {
                        groups[0].hotkeys += hotkeys
                    } else {
                        groups += AbilityGroup(group.name.orEmpty(), group.abilities.orEmpty().toMutableList(), hotkeys)
                    }
                }
            }
            dto.activeGroup?.let { activeGroup = it.coerceIn(groups.indices) }
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
            it.groups = groups.map { group -> GroupDto(group.name, group.abilities, group.hotkeys) }
            it.activeGroup = activeGroup
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
        var groups: List<GroupDto>? = null
        var activeGroup: Int? = null
    }

    private class GroupDto(
        var name: String? = null,
        var abilities: List<String>? = null,
        var hotkeys: Map<String, String>? = null,
    )

    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 2.0f
    private const val MIN_OPACITY = 0.2f
}
