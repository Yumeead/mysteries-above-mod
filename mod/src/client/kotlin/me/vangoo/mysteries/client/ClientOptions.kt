package me.vangoo.mysteries.client

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import me.vangoo.domain.hud.HudPart
import me.vangoo.domain.hud.HudPosition
import me.vangoo.domain.hud.HudSettings
import me.vangoo.domain.hud.HorizontalAnchor
import me.vangoo.domain.hud.VerticalAnchor
import me.vangoo.domain.hud.SpiritualityMode
import me.vangoo.domain.hud.InterfaceStyle
import me.vangoo.domain.hud.HealthStyle
import me.vangoo.domain.hud.HungerStyle
import me.vangoo.domain.hud.HalfHeartStyle
import me.vangoo.domain.hud.SpiritualityNumbers
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.render.WheelSkin
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
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
    /**
     * `InputConstants.Key` name (`key.keyboard.c`, `key.mouse.4`) → target: an ability identity,
     * or `identity#mode` for one mode of it (see [target]).
     */
    val hotkeys: MutableMap<String, String> = LinkedHashMap(),
) {
    val isAll: Boolean get() = abilities == null

    fun contains(identity: String): Boolean = abilities?.contains(identity) ?: true

    fun keyOf(target: String): String? = hotkeys.entries.firstOrNull { it.value == target }?.key

    /** One key casts one ability, and one ability has one key: binding moves both. */
    fun bind(key: String, identity: String) {
        unbind(identity)
        hotkeys[key] = identity
    }

    fun unbind(target: String) {
        hotkeys.values.removeAll { it == target }
    }

    /** Add or remove; a removed ability loses its keys in this group too, its modes' among them. */
    fun toggle(identity: String) {
        val list = abilities ?: return
        if (!list.remove(identity)) list.add(identity) else hotkeys.values.removeAll { abilityOf(it) == identity }
    }

    companion object {
        /** What a hotkey casts: the ability as it stands, or one of its modes. */
        fun target(identity: String, mode: String?): String = if (mode == null) identity else "$identity#$mode"

        fun abilityOf(target: String): String = target.substringBefore('#')

        fun modeOf(target: String): String? = target.substringAfter('#', "").ifEmpty { null }
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

    var hudSettings: HudSettings by persisted(HudSettings())

    var showSpirituality: Boolean
        get() = hudSettings.spirituality != SpiritualityMode.OFF
        set(value) {
            hudSettings = hudSettings.copy(spirituality = if (!value) SpiritualityMode.OFF
                else hudSettings.spirituality.takeUnless { it == SpiritualityMode.OFF } ?: SpiritualityMode.FLASK)
        }

    var showDescriptions: Boolean by persisted(true)

    /** Turns the HUD fade into an instant switch; the cooldown veil stays, it shows state. */
    var reducedMotion: Boolean by persisted(false)

    /** The wheel's look; geometry and input are the same for every skin. */
    var wheelSkin: WheelSkin by persisted(WheelSkin.ATMOSPHERIC)

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
            val migrated = HudSettings.migrate(dto.spiritualityMode, dto.spiritualityNumbers, dto.showSpirituality)
            val positions = readPositions(dto.hudPositions)
            val scales = buildMap {
                dto.hudScales.orEmpty().forEach { (name, scale) ->
                    val part = enumOrNull<HudPart>(name) ?: return@forEach
                    if (scale != null) put(part, HudSettings.validScale(scale))
                }
            }
            hudSettings = migrated.copy(positions = positions, scales = scales, flaskBacking = dto.flaskBacking ?: false,
                interfaceStyle = dto.interfaceStyle?.let { enumOrNull<InterfaceStyle>(it) } ?: InterfaceStyle.MOD,
                health = dto.healthStyle?.let { enumOrNull<HealthStyle>(it) } ?: HealthStyle.HEARTS,
                hunger = dto.hungerStyle?.let { enumOrNull<HungerStyle>(it) } ?: HungerStyle.ICONS,
                halfHeart = dto.halfHeartStyle?.let { enumOrNull<HalfHeartStyle>(it) } ?: HalfHeartStyle.HEIGHT,
                healthNumbers = dto.healthNumbers?.let { enumOrNull<SpiritualityNumbers>(it) } ?: SpiritualityNumbers.ON_CHANGE,
                hungerNumbers = dto.hungerNumbers?.let { enumOrNull<SpiritualityNumbers>(it) } ?: SpiritualityNumbers.ON_CHANGE,
                compactHealthAbove40 = dto.compactHealthAbove40 ?: false,
                panelPositions = readPositions(dto.hudPanelPositions))
            dto.showDescriptions?.let { showDescriptions = it }
            dto.reducedMotion?.let { reducedMotion = it }
            dto.wheelSkin?.let { name -> wheelSkin = enumOrNull<WheelSkin>(name) ?: wheelSkin }

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
            it.spiritualityMode = hudSettings.spirituality.name
            it.spiritualityNumbers = hudSettings.numbers.name
            it.flaskBacking = hudSettings.flaskBacking
            it.interfaceStyle = hudSettings.interfaceStyle.name
            it.healthStyle = hudSettings.health.name
            it.hungerStyle = hudSettings.hunger.name
            it.halfHeartStyle = hudSettings.halfHeart.name
            it.healthNumbers = hudSettings.healthNumbers.name
            it.hungerNumbers = hudSettings.hungerNumbers.name
            it.compactHealthAbove40 = hudSettings.compactHealthAbove40
            it.hudScales = hudSettings.scales.mapKeys { (part, _) -> part.name }
            it.hudPositions = hudSettings.positions.mapKeys { (part, _) -> part.name }.mapValues { (_, position) ->
                PositionDto().apply {
                    horizontal = position.horizontal.name
                    vertical = position.vertical.name
                    offsetX = position.offsetX
                    offsetY = position.offsetY
                }
            }
            it.hudPanelPositions = hudSettings.panelPositions.mapKeys { (part, _) -> part.name }.mapValues { (_, position) ->
                PositionDto().apply {
                    horizontal = position.horizontal.name
                    vertical = position.vertical.name
                    offsetX = position.offsetX
                    offsetY = position.offsetY
                }
            }
            it.showDescriptions = showDescriptions
            it.reducedMotion = reducedMotion
            it.wheelSkin = wheelSkin.name
            it.groups = groups.map { group -> GroupDto(group.name, group.abilities, group.hotkeys) }
            it.activeGroup = activeGroup
        }

        val path = file
        var temporary: Path? = null
        try {
            Files.createDirectories(path.parent)
            temporary = Files.createTempFile(path.parent, MOD_ID, ".tmp")
            Files.writeString(temporary, gson.toJson(dto))
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (failure: IOException) {
            log.warn("Could not write {}: {}", path, failure.message)
        } finally {
            temporary?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name.equals(name, ignoreCase = true) }

    private fun readPositions(values: Map<String, PositionDto?>?): Map<HudPart, HudPosition> = buildMap {
        values.orEmpty().forEach { (name, savedOrNull) ->
            val saved = savedOrNull ?: return@forEach
            val part = enumOrNull<HudPart>(name) ?: return@forEach
            val horizontal = saved.horizontal?.let { enumOrNull<HorizontalAnchor>(it) } ?: return@forEach
            val vertical = saved.vertical?.let { enumOrNull<VerticalAnchor>(it) } ?: return@forEach
            put(part, HudPosition(horizontal, vertical, (saved.offsetX ?: 0).coerceIn(-16384, 16384),
                (saved.offsetY ?: 0).coerceIn(-16384, 16384)))
        }
    }

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
        var spiritualityMode: String? = null
        var spiritualityNumbers: String? = null
        var flaskBacking: Boolean? = null
        var interfaceStyle: String? = null
        var healthStyle: String? = null
        var hungerStyle: String? = null
        var halfHeartStyle: String? = null
        var healthNumbers: String? = null
        var hungerNumbers: String? = null
        var compactHealthAbove40: Boolean? = null
        var hudPositions: Map<String, PositionDto?>? = null
        var hudPanelPositions: Map<String, PositionDto?>? = null
        var hudScales: Map<String, Float?>? = null
        var showDescriptions: Boolean? = null
        var reducedMotion: Boolean? = null
        var wheelSkin: String? = null
        var groups: List<GroupDto>? = null
        var activeGroup: Int? = null
    }

    private class PositionDto {
        var horizontal: String? = null
        var vertical: String? = null
        var offsetX: Int? = null
        var offsetY: Int? = null
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
