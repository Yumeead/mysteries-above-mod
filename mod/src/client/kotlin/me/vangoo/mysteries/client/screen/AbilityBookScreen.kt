package me.vangoo.mysteries.client.screen

import com.mojang.blaze3d.platform.InputConstants
import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.valueobjects.SanityLoss
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.AbilityGroup
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.beyonder.ClientMarionettes
import me.vangoo.mysteries.client.input.ModKeys
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.AbilityActionPayload
import me.vangoo.mysteries.net.payload.AbilityActionPayload.Action
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import org.lwjgl.glfw.GLFW
import kotlin.math.roundToInt

/** How a group is called on screen: the built-in one by its translation, the rest by the player's name. */
fun groupTitle(group: AbilityGroup): Component =
    if (group.isAll) Component.translatable("book.$MOD_ID.all") else Component.literal(group.name.ifBlank { "…" })

/**
 * Where a power comes from, as one line: the Sequence in the pathway colour, then its name —
 * "Посл. 9 · Sailor". Off the pathway (stolen, lent) it says so instead.
 */
fun originLine(origin: AbilityRosterPayload.Origin?, pathwayRgb: Int): Component =
    if (origin == null) {
        Component.translatable("book.$MOD_ID.off_pathway").withColor(UiTheme.MUTED)
    } else {
        Component.translatable("book.$MOD_ID.sequence", origin.level).withColor(pathwayRgb)
            .append(Component.literal(" · ${origin.name}").withColor(UiTheme.MUTED))
    }

/**
 * The Beyonder's grimoire (key B): the one place where the player reads who they are and sets up
 * how they use it. Four sections on bookmarks down the spine:
 *
 * - **Огляд** — pathway emblem, Sequence, spirituality, mastery, sanity. Only numbers the server
 *   sends; a value the mod does not track yet (acting) is shown as missing, never invented.
 * - **Здібності** — the roster, searchable and filterable by type, cut into Sequences; the right
 *   page is the chosen power in full, its live state and its modes.
 * - **Клавіші** — every bindable target of the active group with its key. A click captures the
 *   next press (Esc cancels, Backspace clears); a clash with vanilla or another mod is shown, not
 *   forbidden. Hotkeys do not fire while any screen is open, so editing cannot cast.
 * - **Групи** — create, rename, reorder, delete; membership is a checklist.
 *
 * The open group is the active one everywhere, as before: the wheel and the hotkeys follow it.
 * Everything drawn is built on change or in [tick] (20 times a second at most), never in the frame.
 */
class AbilityBookScreen(initial: Page? = null) : Screen(Component.translatable("book.$MOD_ID.title")) {

    enum class Page(val icon: String) { STATUS("status"), LIBRARY("library"), KEYS("keys"), GROUPS("groups") }

    private var page = initial ?: lastPage

    // ── Geometry, fixed per init ────────────────────────────────────────────────
    private var bx = 0; private var by = 0; private var bw = 0; private var bh = 0
    private var cx = 0; private var cy = 0; private var cw = 0; private var ch = 0
    private var lw = 0; private var rx = 0; private var rw = 0

    // ── Model ──────────────────────────────────────────────────────────────────
    private var entries: List<AbilityRosterPayload.Entry> = ClientAbilityState.entries
    private var chosen: String? = ClientAbilityState.selected ?: entries.firstOrNull()?.identity
    private var accent = UiTheme.ACCENT
    private var emblem: Identifier = emblemOf(null)
    private var headerTitle: FormattedCharSequence = FormattedCharSequence.EMPTY
    private var headerSub: FormattedCharSequence = FormattedCharSequence.EMPTY

    private var search = ""
    private var filter: AbilityType? = null
    private var scroll = 0
    private var detailScroll = 0

    /** A list line: a Sequence heading (`entry == null`) or an ability, or (keys page) one of its modes. */
    private class Row(
        val entry: AbilityRosterPayload.Entry?,
        val mode: AbilityRosterPayload.Mode?,
        val text: FormattedCharSequence,
    ) {
        var right: FormattedCharSequence = FormattedCharSequence.EMPTY
        var rightIcon: String? = null
        var rightColor = UiTheme.MUTED
        var conflict: String? = null
    }

    private var rows: List<Row> = emptyList()

    // Detail page (library)
    private var detail: List<Pair<FormattedCharSequence, Int>> = emptyList()
    private var detailName: FormattedCharSequence = FormattedCharSequence.EMPTY
    private var detailOrigin: FormattedCharSequence = FormattedCharSequence.EMPTY
    private var detailType: FormattedCharSequence = FormattedCharSequence.EMPTY
    private var detailState: Component = Component.empty()
    private var detailStateColor = UiTheme.MUTED
    private var detailKey: FormattedCharSequence = FormattedCharSequence.EMPTY

    // Keys page
    private var capturing: String? = null
    private var footer: List<Pair<FormattedCharSequence, Int>> = emptyList()

    // Groups page
    private var groupRows: List<FormattedCharSequence> = emptyList()
    private var groupCounts: List<FormattedCharSequence> = emptyList()
    private var confirmDelete = 0

    // Status page
    private var statusLines: List<Stat> = emptyList()

    private class Stat(val icon: String, val label: Component, val value: Component, val caption: Component?, val share: Float?, val rgb: Int, val marks: IntArray = IntArray(0))

    private val group: AbilityGroup get() = ClientOptions.group

    private val listTop get() = cy + if (page == Page.LIBRARY) 24 else 6
    private val visibleRows get() = ((cy + ch - 6 - listTop - if (page == Page.KEYS) FOOTER else 0) / ROW).coerceAtLeast(1)

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        bw = minOf(width - 12, MAX_W)
        bh = minOf(height - 10, MAX_H)
        bx = (width - bw) / 2
        by = (height - bh) / 2
        cx = bx + SPINE
        cy = by + HEADER
        cw = bx + bw - 8 - cx
        ch = by + bh - 8 - cy
        lw = (cw - GAP) * 9 / 20
        rx = cx + lw + GAP
        rw = cx + cw - rx

        when (page) {
            Page.LIBRARY -> {
                val box = EditBox(font, cx + 18, cy + 7, lw - 18 - FILTERS * 15 - 8, 10, Component.translatable("book.$MOD_ID.search"))
                box.setBordered(false)
                box.setTextColor(UiTheme.argb(UiTheme.TEXT, 1f))
                box.setHint(Component.translatable("book.$MOD_ID.search").withColor(UiTheme.MUTED))
                box.setMaxLength(32)
                box.value = search
                box.setResponder { text -> search = text; scroll = 0; rebuild() }
                addRenderableWidget(box)
            }
            Page.GROUPS -> if (!group.isAll) {
                val name = EditBox(font, rx + 8, cy + 20, rw - 16, 10, Component.translatable("book.$MOD_ID.group_name"))
                name.setBordered(false)
                name.setTextColor(UiTheme.argb(UiTheme.TEXT, 1f))
                name.setMaxLength(NAME_LENGTH)
                name.value = group.name
                // Renaming must not rebuild the widgets, or the box would lose focus on every letter.
                name.setResponder { text -> group.name = text; ClientOptions.save(); rebuild() }
                addRenderableWidget(name)
            }
            else -> Unit
        }
        rebuild()
    }

    override fun tick() {
        if (!ClientAbilityState.isBeyonder) {
            onClose()
            return
        }
        if (confirmDelete > 0) confirmDelete--
        if (ClientAbilityState.entries !== entries) {
            entries = ClientAbilityState.entries
            if (chosenEntry() == null) chosen = entries.firstOrNull()?.identity
            rebuildWidgets()
            return
        }
        // Cooldowns, spirituality and toggles move on their own: refresh the live labels.
        refreshLive()
    }

    override fun removed() {
        lastPage = page
    }

    // ── Model building ─────────────────────────────────────────────────────────

    private fun chosenEntry(): AbilityRosterPayload.Entry? = chosen?.let { id -> entries.firstOrNull { it.identity == id } }

    private fun rebuild() {
        val roster = ClientAbilityState.roster
        val worn = roster?.worn
        val pathway = worn?.pathwayId ?: roster?.pathwayId
        accent = pathway?.let { PathwayBranding.textOf(it).packed } ?: UiTheme.ACCENT
        emblem = emblemOf(pathway)
        if (roster != null) {
            headerTitle = fit(Component.literal(pathway!!).withColor(accent), bw / 2)
            val seq = if (worn != null) {
                Component.translatable("book.$MOD_ID.sequence", worn.sequence.level)
            } else {
                Component.translatable("book.$MOD_ID.sequence", roster.sequence.level).append(" · ").append(roster.sequenceName)
            }
            headerSub = fit(Component.literal(" · ").append(seq).withColor(UiTheme.MUTED), bw / 2)
        }
        when (page) {
            Page.STATUS -> rebuildStatus()
            Page.LIBRARY -> { rebuildLibrary(); rebuildDetail() }
            Page.KEYS -> rebuildKeys()
            Page.GROUPS -> rebuildGroups()
        }
        refreshLive()
    }

    private fun rebuildStatus() {
        val roster = ClientAbilityState.roster ?: return
        val spirit = ClientAbilityState.spirituality
        val mastery = ClientAbilityState.mastery
        val sanity = ClientAbilityState.sanityLoss
        val atTop = roster.sequence.level == 0
        statusLines = listOfNotNull(
            Stat(
                "spirit", tr("stat.spirituality"),
                spirit?.let { Component.literal("${it.current} / ${it.maximum}") } ?: tr("no_data"),
                null, spirit?.let { it.current.toFloat() / it.maximum.coerceAtLeast(1) }, UiTheme.ACCENT,
            ),
            Stat(
                "mastery", tr("stat.mastery"), Component.literal("%.1f%%".format(mastery.value)),
                when {
                    atTop -> tr("mastery.top")
                    mastery.canAdvance() -> tr("mastery.ready").withColor(accent)
                    else -> tr("mastery.grows")
                },
                (mastery.value / 100.0).toFloat(), accent,
            ),
            Stat(
                "sanity", tr("stat.sanity"), Component.literal("${sanity.scale} / 100"),
                tr("sanity.${band(sanity)}").append(
                    if (sanity.calculateFailureChance() > 0) tr("sanity.chance", (sanity.calculateFailureChance() * 100).roundToInt()) else Component.empty(),
                ),
                sanity.scale / 100f, UiTheme.CRITICAL, SANITY_MARKS,
            ),
            // The plugin's acting mechanic has no port yet: say so instead of drawing a fake bar.
            Stat("mask", tr("stat.acting"), Component.literal("—"), tr("acting.missing"), null, UiTheme.MUTED),
            ClientMarionettes.takeIf { it.hasThreads || it.limit > 0 }?.let {
                Stat("body", tr("stat.marionettes"), Component.literal("${it.entries.size} / ${it.limit}"), null, null, accent)
            },
            Stat(
                "active", tr("stat.abilities"), Component.literal("${entries.size}"),
                tr(
                    "abilities.split",
                    entries.count { it.type == AbilityType.ACTIVE },
                    entries.count { it.type == AbilityType.TOGGLEABLE_PASSIVE },
                    entries.count { it.type == AbilityType.PERMANENT_PASSIVE },
                ),
                null, accent,
            ),
            Stat(
                "cooldown", tr("stat.now"), Component.empty(),
                tr(
                    "abilities.now",
                    entries.count { ClientAbilityState.remainingTicks(it.identity) > 0 },
                    entries.count { ClientAbilityState.isToggled(it.identity) },
                    entries.count { ClientAbilityState.isSustaining(it.identity) },
                ),
                null, UiTheme.MUTED,
            ),
        )
        // The Sequences walked so far, by the names the roster brings with each power.
        val names = entries.mapNotNull { it.origin }.associate { it.level to it.name } + (roster.sequence.level to roster.sequenceName.string)
        walked = names.keys.filter { it >= roster.sequence.level }.sortedDescending().map { level ->
            fit(tr("sequence", level).withColor(if (level == roster.sequence.level) accent else UiTheme.ACCENT_DEEP)
                .append(Component.literal(" · ${names[level]}").withColor(if (level == roster.sequence.level) UiTheme.TEXT else UiTheme.MUTED)), lw - 24)
        }
    }

    private var walked: List<FormattedCharSequence> = emptyList()

    private fun rebuildLibrary() {
        val pathwayRgb = accent
        val query = search.trim().lowercase()
        val shown = entries.filter { entry ->
            (filter == null || entry.type == filter) &&
                (query.isEmpty() || entry.name.string.lowercase().contains(query) || entry.description.string.lowercase().contains(query))
        }
        val width = lw - 8 - 24 - 34
        rows = shown.withIndex().flatMap { (i, entry) ->
            val heading = if (i == 0 || shown[i - 1].origin?.level != entry.origin?.level) {
                listOf(Row(null, null, fit(originLine(entry.origin, pathwayRgb), lw - 12)))
            } else {
                emptyList()
            }
            heading + Row(entry, null, fit(entry.name, width))
        }
        scroll = scroll.coerceIn(0, (rows.size - visibleRows).coerceAtLeast(0))
    }

    private fun rebuildDetail() {
        val entry = chosenEntry()
        detailScroll = 0
        if (entry == null) {
            detail = emptyList()
            return
        }
        val textW = rw - 16
        detailName = fit(entry.name.copy().withColor(accent), rw - 40)
        detailOrigin = fit(originLine(entry.origin, accent), rw - 40)
        detailType = fit(tr("type.${entry.type.name.lowercase()}").withColor(UiTheme.MUTED), textW - 12)
        detailKey = fit(
            if (entry.type == AbilityType.PERMANENT_PASSIVE) tr("always_on").withColor(UiTheme.MUTED)
            else tr("key", ModKeys.hotkeyLabel(entry.identity) ?: tr("no_key")).withColor(UiTheme.TEXT),
            textW - 12,
        )
        detail = buildList {
            font.split(entry.description, textW).forEach { add(it to UiTheme.TEXT) }
        }
    }

    /** Every bindable target of the active group: the abilities and, under each, its modes. */
    private fun rebuildKeys() {
        val bindable = ModKeys.visible(group).filter { it.type != AbilityType.PERMANENT_PASSIVE }
        val nameW = cw - 16 - 24 - KEY_BADGE
        rows = bindable.flatMap { entry ->
            listOf(Row(entry, null, fit(entry.name, nameW))) + entry.modes.mapIndexed { index, mode ->
                val branch = if (index == entry.modes.lastIndex) "└ " else "├ "
                Row(entry, mode, fit(Component.literal(branch).append(mode.name), nameW - 8))
            }
        }
        rows.forEach { row ->
            val target = AbilityGroup.target(row.entry!!.identity, row.mode?.id)
            val keyName = group.keyOf(target)
            row.rightColor = UiTheme.TEXT
            row.right = when {
                capturing == target -> fit(tr("press_key").withColor(UiTheme.ACCENT), KEY_BADGE - 6)
                keyName == null -> FormattedCharSequence.forward("—", net.minecraft.network.chat.Style.EMPTY)
                else -> fit(InputConstants.getKey(keyName).displayName.copy(), KEY_BADGE - 14)
            }
            row.conflict = keyName?.takeIf { capturing != target }?.let { conflicts(it) }
            if (row.conflict != null) row.rightColor = UiTheme.CRITICAL
        }
        scroll = scroll.coerceIn(0, (rows.size - visibleRows).coerceAtLeast(0))
        val clash = rows.firstOrNull { capturing == null && it.conflict != null }
        footer = buildList {
            if (capturing != null) {
                add(fit(tr("capture_hint").withColor(UiTheme.ACCENT), cw - 16) to UiTheme.ACCENT)
            } else {
                add(fit(tr("keys_hint").withColor(UiTheme.MUTED), cw - 16) to UiTheme.MUTED)
                if (clash != null) {
                    add(fit(tr("conflict", clash.conflict!!).withColor(UiTheme.CRITICAL), cw - 26) to UiTheme.CRITICAL)
                }
            }
        }
    }

    private fun conflicts(keyName: String): String? {
        val key = InputConstants.getKey(keyName)
        val clashes = minecraft.options.keyMappings.filter { it.matches(key) }
        return clashes.takeIf { it.isNotEmpty() }?.joinToString(", ") { Component.translatable(it.name).string }
    }

    private fun rebuildGroups() {
        groupRows = ClientOptions.groups.map { fit(groupTitle(it), lw - 50) }
        groupCounts = ClientOptions.groups.map {
            val count = if (it.isAll) entries.size else it.abilities!!.count { id -> entries.any { e -> e.identity == id } }
            FormattedCharSequence.forward("$count", net.minecraft.network.chat.Style.EMPTY)
        }
        val nameW = rw - 16 - 32
        rows = entries.map { Row(it, null, fit(it.name, nameW)) }
        scroll = scroll.coerceIn(0, (rows.size - groupVisible()).coerceAtLeast(0))
    }

    private fun groupVisible() = ((cy + ch - 6 - (cy + 36)) / ROW).coerceAtLeast(1)

    /** The labels that move without any click: cooldown seconds, toggles, affordability, the numbers. */
    private fun refreshLive() {
        when (page) {
            Page.LIBRARY -> {
                for (row in rows) {
                    val entry = row.entry ?: continue
                    val seconds = ClientAbilityState.remainingSeconds(entry.identity)
                    row.rightIcon = null
                    row.right = FormattedCharSequence.EMPTY
                    when {
                        seconds > 0 -> { row.rightIcon = "cooldown"; row.right = plain("$seconds"); row.rightColor = UiTheme.MUTED }
                        ClientAbilityState.isToggled(entry.identity) -> { row.rightIcon = "toggle"; row.rightColor = accent }
                        ClientAbilityState.isSustaining(entry.identity) -> { row.rightIcon = "sustain"; row.rightColor = accent }
                        !ClientAbilityState.canAfford(entry) -> { row.rightIcon = "lock"; row.rightColor = UiTheme.CRITICAL }
                    }
                }
                val entry = chosenEntry() ?: return
                val seconds = ClientAbilityState.remainingSeconds(entry.identity)
                val (text, color) = when {
                    entry.type == AbilityType.PERMANENT_PASSIVE -> tr("state.passive") to UiTheme.MUTED
                    seconds > 0 -> tr("state.cooldown", seconds) to UiTheme.MUTED
                    ClientAbilityState.isToggled(entry.identity) -> tr("state.on") to accent
                    ClientAbilityState.isSustaining(entry.identity) -> tr("state.sustaining") to accent
                    entry.type == AbilityType.TOGGLEABLE_PASSIVE -> tr("state.off") to UiTheme.MUTED
                    !ClientAbilityState.canAfford(entry) -> tr("state.poor") to UiTheme.CRITICAL
                    else -> tr("state.ready") to UiTheme.TEXT
                }
                detailState = text
                detailStateColor = color
            }
            Page.STATUS -> if (statusTick++ % 10 == 0) rebuildStatus()
            else -> Unit
        }
    }

    private var statusTick = 0

    // ── Actions ─────────────────────────────────────────────────────────────────

    private fun open(target: Page) {
        if (target == page) return
        page = target
        capturing = null
        scroll = 0
        rebuildWidgets()
    }

    private fun activate(index: Int) {
        ClientOptions.activeGroup = index
        capturing = null
        confirmDelete = 0
        scroll = 0
        rebuildWidgets()
    }

    private fun addGroup() {
        if (ClientOptions.groups.size >= MAX_GROUPS) return
        ClientOptions.editGroups {
            val name = Component.translatable("book.$MOD_ID.new_group", ClientOptions.groups.size).string
            ClientOptions.groups += AbilityGroup(name, mutableListOf())
            ClientOptions.activeGroup = ClientOptions.groups.lastIndex
        }
        rebuildWidgets()
    }

    /** Moves the open group one place; the built-in "all" stays first. */
    private fun moveGroup(step: Int) {
        val from = ClientOptions.activeGroup
        val to = from + step
        if (from == 0 || to < 1 || to > ClientOptions.groups.lastIndex) return
        ClientOptions.editGroups {
            val list = ClientOptions.groups
            list[from] = list[to].also { list[to] = list[from] }
            ClientOptions.activeGroup = to
        }
        rebuild()
    }

    /** Deleting asks twice: the first click arms, a second one within three seconds deletes. */
    private fun deleteGroup() {
        val index = ClientOptions.activeGroup
        if (index == 0) return
        if (confirmDelete == 0) {
            confirmDelete = CONFIRM_TICKS
            return
        }
        confirmDelete = 0
        ClientOptions.editGroups {
            ClientOptions.groups.removeAt(index)
            ClientOptions.activeGroup = (index - 1).coerceAtLeast(0)
        }
        rebuildWidgets()
    }

    private fun bind(key: InputConstants.Key?) {
        val target = capturing ?: return
        ClientOptions.editGroups { if (key == null) group.unbind(target) else group.bind(key.name, target) }
        capturing = null
        rebuild()
    }

    // ── Input ───────────────────────────────────────────────────────────────────

    override fun keyPressed(event: KeyEvent): Boolean {
        if (capturing != null) {
            when (event.key()) {
                GLFW.GLFW_KEY_ESCAPE -> { capturing = null; rebuild() }
                GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> bind(null)
                else -> bind(InputConstants.getKey(event))
            }
            return true
        }
        val typing = focused is EditBox
        // The book key closes the book again — unless the player is typing.
        if (!typing && ModKeys.isBookKey(event)) {
            onClose()
            return true
        }
        // 1–4 open the sections without the mouse.
        if (!typing && event.key() in GLFW.GLFW_KEY_1..GLFW.GLFW_KEY_4) {
            open(Page.entries[event.key() - GLFW.GLFW_KEY_1])
            return true
        }
        return super.keyPressed(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (capturing != null) {
            if (event.button() > GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                bind(InputConstants.Type.MOUSE.getOrCreate(event.button()))
            } else {
                // A click elsewhere cancels the capture rather than binding the main buttons.
                capturing = null
                rebuild()
            }
            return true
        }
        val x = event.x().toInt()
        val y = event.y().toInt()

        Page.entries.forEachIndexed { i, p ->
            if (inside(x, y, bx - 2, tabY(i), TAB_W, TAB_H)) { open(p); return true }
        }
        if (super.mouseClicked(event, doubleClick)) return true
        // A click anywhere else lets go of a focused text field.
        focused = null

        when (page) {
            Page.STATUS -> Unit
            Page.LIBRARY -> clickLibrary(x, y)
            Page.KEYS -> clickKeys(x, y)
            Page.GROUPS -> clickGroups(x, y)
        }
        return true
    }

    private fun clickLibrary(x: Int, y: Int) {
        // The type filters next to the search field.
        for (i in 0 until FILTERS) {
            if (inside(x, y, filterX(i), cy + 4, 14, 14)) {
                filter = FILTER_TYPES[i].takeIf { it != filter }
                scroll = 0
                rebuild()
                return
            }
        }
        if (x in cx until cx + lw && y >= listTop) {
            val row = rows.getOrNull(scroll + (y - listTop) / ROW)?.takeIf { (y - listTop) / ROW < visibleRows } ?: return
            val entry = row.entry ?: return
            chosen = entry.identity
            rebuildDetail()
            refreshLive()
            return
        }
        val entry = chosenEntry() ?: return
        // A mode line picks the mode on the server; the wheel's card and the HUD follow it.
        entry.modes.forEachIndexed { i, mode ->
            if (inside(x, y, rx + 6, modesTop() + i * MODE_ROW, rw - 12, MODE_ROW)) {
                if (ClientAbilityState.modeOf(entry.identity) != mode.id) {
                    ModChannels.ABILITY_ACTION.sendToServer(AbilityActionPayload(Action.MODE, entry.identity, mode.id))
                }
                return
            }
        }
        // The key line leads straight to that ability's row on the keys page, already listening.
        if (entry.type != AbilityType.PERMANENT_PASSIVE && inside(x, y, rx + 4, keyLineY() - 2, rw - 8, 13)) {
            open(Page.KEYS)
            capturing = entry.identity
            scroll = rows.indexOfFirst { it.entry?.identity == entry.identity && it.mode == null }.coerceAtLeast(0)
            rebuild()
            return
        }
    }

    private fun clickKeys(x: Int, y: Int) {
        if (x !in cx until cx + cw || y < listTop) return
        val index = (y - listTop) / ROW
        if (index >= visibleRows) return
        val row = rows.getOrNull(scroll + index) ?: return
        capturing = AbilityGroup.target(row.entry!!.identity, row.mode?.id)
        rebuild()
    }

    private fun clickGroups(x: Int, y: Int) {
        // Group cards on the left page.
        ClientOptions.groups.indices.forEach { i ->
            if (inside(x, y, cx + 4, groupY(i), lw - 8, GROUP_ROW - 2)) {
                if (i != ClientOptions.activeGroup) activate(i)
                return
            }
        }
        val actionsY = cy + ch - 20
        val actions = listOf(::addGroup, { moveGroup(-1) }, { moveGroup(1) }, ::deleteGroup)
        actions.forEachIndexed { i, action ->
            if (inside(x, y, cx + 6 + i * 18, actionsY, 16, 16)) {
                action()
                rebuild()
                return
            }
        }
        // Membership checklist on the right.
        if (group.isAll) return
        val top = cy + 36
        if (x in rx until rx + rw && y >= top) {
            val index = (y - top) / ROW
            if (index >= groupVisible()) return
            val row = rows.getOrNull(scroll + index) ?: return
            ClientOptions.editGroups { group.toggle(row.entry!!.identity) }
            rebuild()
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY == 0.0) return false
        val step = if (scrollY < 0) 1 else -1
        when (page) {
            Page.LIBRARY -> if (mouseX < rx) {
                scroll = (scroll + step).coerceIn(0, (rows.size - visibleRows).coerceAtLeast(0))
                refreshLive()
            } else {
                detailScroll = (detailScroll + step).coerceIn(0, (detail.size - detailVisible()).coerceAtLeast(0))
            }
            Page.KEYS -> scroll = (scroll + step).coerceIn(0, (rows.size - visibleRows).coerceAtLeast(0))
            Page.GROUPS -> if (mouseX >= rx) scroll = (scroll + step).coerceIn(0, (rows.size - groupVisible()).coerceAtLeast(0))
            Page.STATUS -> return false
        }
        return true
    }

    // ── Frame ───────────────────────────────────────────────────────────────────

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick)
        // Bookmarks first: the active one is drawn over the cover's edge, the rest tuck under it.
        Page.entries.forEachIndexed { i, p -> if (p != page) sprite(graphics, TAB, bx - 2, tabY(i), TAB_W, TAB_H) }
        sprite(graphics, COVER, bx, by, bw, bh)
        sprite(graphics, TAB_ON, bx - 2, tabY(page.ordinal), TAB_W, TAB_H)
        Page.entries.forEachIndexed { i, p ->
            val rgb = if (p == page) UiTheme.ACCENT else UiTheme.MUTED
            sprite(graphics, tabIcon(p), bx + 5, tabY(i) + 5, 12, 12, rgb)
        }
        // The emblem stands on the spine above the bookmarks; the header runs beside it.
        sprite(graphics, emblem, bx + 7, by + 8, 24, 24, accent)
        when (page) {
            Page.STATUS, Page.LIBRARY, Page.GROUPS -> {
                sprite(graphics, LEAF, cx, cy, lw, ch)
                sprite(graphics, LEAF, rx, cy, rw, ch)
            }
            Page.KEYS -> sprite(graphics, LEAF, cx, cy, cw, ch)
        }
        // The wells under the text fields go here: widgets are drawn after the background.
        if (page == Page.LIBRARY) sprite(graphics, INSET, cx + 4, cy + 4, lw - 8 - FILTERS * 15 - 2, 14)
        if (page == Page.GROUPS && !group.isAll) sprite(graphics, INSET, rx + 5, cy + 17, rw - 10, 15)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        // Header: pathway and Sequence, the section's name on the right, an ornamented rule.
        graphics.text(font, headerTitle, cx + 2, by + 10, -1)
        graphics.text(font, headerSub, cx + 2 + font.width(headerTitle), by + 10, -1)
        val section = tr("page.${page.name.lowercase()}")
        graphics.text(font, section, cx + cw - font.width(section) - 2, by + 10, UiTheme.argb(UiTheme.ACCENT, 1f))
        rule(graphics, cx, by + 23, cw)

        when (page) {
            Page.STATUS -> drawStatus(graphics)
            Page.LIBRARY -> drawLibrary(graphics, mouseX, mouseY)
            Page.KEYS -> drawKeys(graphics, mouseX, mouseY)
            Page.GROUPS -> drawGroups(graphics, mouseX, mouseY)
        }
    }

    private fun drawStatus(graphics: GuiGraphicsExtractor) {
        val roster = ClientAbilityState.roster ?: return
        // Left page: the seal and the emblem, then who the Beyonder is.
        val mid = cx + lw / 2
        val big = if (ch >= 200) 72 else 48
        val sealY = cy + 8
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SEAL, mid - 36, sealY + big / 2 - 35, 71, 71, UiTheme.argb(accent, 0.28f))
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, emblem, mid - big / 2, sealY, big, big, UiTheme.argb(0x000000, 0.6f))
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, emblem, mid - big / 2, sealY - 1, big, big, UiTheme.argb(accent, 1f))

        var y = sealY + big + 10
        val worn = roster.worn
        val pathway = Component.literal(worn?.pathwayId ?: roster.pathwayId)
        graphics.centeredText(font, pathway, mid, y, UiTheme.argb(accent, 1f))
        y += 13
        // The Sequence is the largest line on the page: who you are, in one number.
        val level = worn?.sequence?.level ?: roster.sequence.level
        graphics.pose().pushMatrix()
        graphics.pose().translate(mid.toFloat(), y.toFloat())
        graphics.pose().scale(2f, 2f)
        graphics.centeredText(font, tr("sequence_full", level), 0, 0, UiTheme.argb(UiTheme.TEXT, 1f))
        graphics.pose().popMatrix()
        y += 20
        if (worn == null) graphics.centeredText(font, roster.sequenceName, mid, y, UiTheme.argb(UiTheme.MUTED, 1f))
        else graphics.centeredText(font, tr("own_pathway", "${roster.pathwayId} · ${roster.sequenceName.string}"), mid, y, UiTheme.argb(UiTheme.MUTED, 1f))
        y += 14
        // The ladder: nine to zero, everything walked so far lit.
        val step = 9
        val left = mid - (10 * step) / 2 + 2
        for (i in 0..9) {
            val n = 9 - i
            val lit = n >= level
            val x = left + i * step
            graphics.fill(x, y, x + 5, y + 5, UiTheme.argb(if (n == level) accent else if (lit) UiTheme.ACCENT_DEEP else UiTheme.TRACK, 1f))
            if (n == level) graphics.outline(x - 1, y - 1, 7, 7, UiTheme.argb(UiTheme.TEXT, 0.9f))
        }
        y += 14
        if (worn == null && walked.isNotEmpty()) {
            rule(graphics, cx + 12, y, lw - 24)
            y += 7
            val room = (cy + ch - 6 - y) / LINE
            walked.take(room).forEachIndexed { i, line -> graphics.text(font, line, cx + 12, y + i * LINE, -1) }
        }

        // Right page: the numbers.
        var sy = cy + 8
        val x0 = rx + 8
        val barW = rw - 16
        for (stat in statusLines) {
            if (sy + 10 > cy + ch - 4) break
            sprite(graphics, icon(stat.icon), x0, sy, 9, 9, stat.rgb)
            graphics.text(font, stat.label, x0 + 13, sy + 1, UiTheme.argb(UiTheme.MUTED, 1f))
            graphics.text(font, stat.value, x0 + barW - font.width(stat.value), sy + 1, UiTheme.argb(UiTheme.TEXT, 1f))
            var ly = sy + 11
            if (stat.share != null) {
                sprite(graphics, INSET, x0, ly, barW, 6)
                val filled = ((barW - 2) * stat.share.coerceIn(0f, 1f)).toInt()
                if (filled > 0) {
                    graphics.fill(x0 + 1, ly + 1, x0 + 1 + filled, ly + 5, UiTheme.argb(stat.rgb, 1f))
                    graphics.fill(x0 + 1, ly + 1, x0 + 1 + filled, ly + 2, UiTheme.argb(0xFFFFFF, 0.18f))
                }
                for (mark in stat.marks) {
                    val mx = x0 + 1 + (barW - 2) * mark / 100
                    graphics.fill(mx, ly, mx + 1, ly + 6, UiTheme.argb(UiTheme.ACCENT_DEEP, 1f))
                }
                ly += 8
            }
            stat.caption?.let {
                graphics.text(font, fit(it.copy().withStyle { s -> if (s.color == null) s.withColor(UiTheme.MUTED) else s }, barW), x0, ly, -1)
                ly += LINE
            }
            sy = ly + 7
        }
    }

    private fun drawLibrary(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        // Search well and type filters.
        sprite(graphics, icon("search"), cx + 7, cy + 6, 9, 9, UiTheme.MUTED)
        for (i in 0 until FILTERS) {
            val on = FILTER_TYPES[i] == filter
            sprite(graphics, if (on) ROW_ON else INSET, filterX(i), cy + 4, 14, 14)
            sprite(graphics, icon(FILTER_ICONS[i]), filterX(i) + 3, cy + 7, 9, 9, if (on) UiTheme.ACCENT else UiTheme.MUTED)
        }

        val top = listTop
        val shown = rows.drop(scroll).take(visibleRows)
        // Row backings and icons first (items go in their own pass), text after the stratum.
        shown.forEachIndexed { i, row ->
            val y = top + i * ROW
            val entry = row.entry ?: return@forEachIndexed
            if (entry.identity == chosen) sprite(graphics, ROW_ON, cx + 3, y, lw - 6 - 4, ROW - 1)
            else if (inside(mouseX, mouseY, cx + 3, y, lw - 10, ROW - 1)) graphics.outline(cx + 3, y, lw - 10, ROW - 1, UiTheme.argb(UiTheme.ACCENT_DEEP, 1f))
            graphics.item(entry.icon, cx + 6, y + 1)
        }
        if (rows.isEmpty()) graphics.centeredText(font, tr(if (entries.isEmpty()) "no_abilities" else "nothing_found"), cx + lw / 2, top + 10, UiTheme.argb(UiTheme.MUTED, 1f))
        scrollbar(graphics, cx + lw - 5, top, visibleRows * ROW, rows.size, visibleRows, scroll)

        val entry = chosenEntry()
        if (entry != null) {
            sprite(graphics, SLOT, rx + 6, cy + 6, 20, 20)
            graphics.item(entry.icon, rx + 8, cy + 8)
        }
        graphics.nextStratum()

        shown.forEachIndexed { i, row ->
            val y = top + i * ROW
            val e = row.entry
            if (e == null) {
                graphics.text(font, row.text, cx + 6, y + 6, -1)
                val tw = font.width(row.text)
                UiTheme.hairline(graphics, cx + 10 + tw, y + 10, lw - 24 - tw, accent, 0.4f)
                return@forEachIndexed
            }
            graphics.text(font, row.text, cx + 26, y + 5, UiTheme.argb(if (e.identity == chosen) UiTheme.TEXT else UiTheme.MUTED, 1f))
            var rx0 = cx + lw - 10
            if (row.right != FormattedCharSequence.EMPTY) {
                rx0 -= font.width(row.right)
                graphics.text(font, row.right, rx0, y + 5, UiTheme.argb(row.rightColor, 1f))
                rx0 -= 2
            }
            val live = row.rightIcon
            if (live != null) sprite(graphics, icon(live), rx0 - 9, y + 4, 9, 9, row.rightColor)
            else sprite(graphics, icon(typeIcon(e.type)), rx0 - 9, y + 4, 9, 9, UiTheme.ACCENT_DEEP)
        }

        if (entry == null) return
        val x0 = rx + 8
        graphics.text(font, detailName, rx + 32, cy + 7, -1)
        graphics.text(font, detailOrigin, rx + 32, cy + 17, -1)
        var y = cy + 31
        sprite(graphics, icon(typeIcon(entry.type)), x0, y, 9, 9, accent)
        graphics.text(font, detailType, x0 + 12, y + 1, -1)
        y += 12
        // The numbers as icons: one-off cost, upkeep, cooldown.
        var nx = x0
        if (entry.cost > 0) nx = chip(graphics, "spirit", "${entry.cost}", nx, y, UiTheme.ACCENT, !ClientAbilityState.canAfford(entry))
        if (entry.upkeep > 0) nx = chip(graphics, "upkeep", tr("per_second", entry.upkeep).string, nx, y, UiTheme.ACCENT, false)
        if (entry.cooldownSeconds > 0) nx = chip(graphics, "cooldown", tr("seconds", entry.cooldownSeconds).string, nx, y, UiTheme.MUTED, false)
        if (nx != x0) y += 13
        graphics.text(font, detailState, x0, y, UiTheme.argb(detailStateColor, 1f))
        y += 12
        // Modes: the ● follows the server's pick.
        if (entry.modes.isNotEmpty()) {
            val picked = ClientAbilityState.modeOf(entry.identity)
            entry.modes.forEachIndexed { i, mode ->
                val my = modesTop() + i * MODE_ROW
                val on = mode.id == picked
                if (on) sprite(graphics, ROW_ON, rx + 6, my, rw - 12, MODE_ROW - 1)
                else if (inside(mouseX, mouseY, rx + 6, my, rw - 12, MODE_ROW - 1)) graphics.outline(rx + 6, my, rw - 12, MODE_ROW - 1, UiTheme.argb(UiTheme.ACCENT_DEEP, 1f))
                graphics.text(font, if (on) "●" else "○", rx + 10, my + 2, UiTheme.argb(if (on) UiTheme.ACCENT else UiTheme.MUTED, 1f))
                graphics.text(font, mode.name, rx + 20, my + 2, UiTheme.argb(if (on) UiTheme.TEXT else UiTheme.MUTED, 1f))
            }
            y = modesTop() + entry.modes.size * MODE_ROW + 2
        }
        rule(graphics, rx + 8, y, rw - 16)
        y += 6
        // The description, scrolled within what is left above the key line.
        val lines = detailVisibleFrom(y)
        detail.drop(detailScroll).take(lines).forEachIndexed { i, (line, color) ->
            graphics.text(font, line, x0, y + i * LINE, UiTheme.argb(color, 1f))
        }
        if (detail.size > lines) scrollbar(graphics, rx + rw - 5, y, lines * LINE, detail.size, lines, detailScroll)

        val ky = keyLineY()
        UiTheme.hairline(graphics, rx + 8, ky - 4, rw - 16, UiTheme.ACCENT_DEEP, 1f)
        val keyHover = entry.type != AbilityType.PERMANENT_PASSIVE && inside(mouseX, mouseY, rx + 4, ky - 2, rw - 8, 13)
        sprite(graphics, icon("key"), x0, ky, 9, 9, if (keyHover) UiTheme.ACCENT else UiTheme.MUTED)
        graphics.text(font, detailKey, x0 + 12, ky + 1, if (keyHover) UiTheme.argb(UiTheme.ACCENT, 1f) else -1)
    }

    private fun drawKeys(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val top = listTop
        val shown = rows.drop(scroll).take(visibleRows)
        val badgeX = cx + cw - 8 - KEY_BADGE
        shown.forEachIndexed { i, row ->
            val y = top + i * ROW
            val target = AbilityGroup.target(row.entry!!.identity, row.mode?.id)
            when {
                capturing == target -> sprite(graphics, ROW_ON, cx + 4, y, cw - 8, ROW - 1)
                inside(mouseX, mouseY, cx + 4, y, cw - 8, ROW - 1) -> graphics.outline(cx + 4, y, cw - 8, ROW - 1, UiTheme.argb(UiTheme.ACCENT_DEEP, 1f))
            }
            sprite(graphics, INSET, badgeX, y + 2, KEY_BADGE, ROW - 5)
            if (row.mode == null) graphics.item(row.entry.icon, cx + 7, y + 1)
        }
        if (rows.isEmpty()) graphics.centeredText(font, tr("keys_empty"), cx + cw / 2, top + 10, UiTheme.argb(UiTheme.MUTED, 1f))
        scrollbar(graphics, cx + cw - 5, top, visibleRows * ROW, rows.size, visibleRows, scroll)
        graphics.nextStratum()

        shown.forEachIndexed { i, row ->
            val y = top + i * ROW
            val x = if (row.mode == null) cx + 28 else cx + 36
            graphics.text(font, row.text, x, y + 5, UiTheme.argb(if (row.mode == null) UiTheme.TEXT else UiTheme.MUTED, 1f))
            val bw = font.width(row.right)
            val conflictIcon = row.conflict != null
            val textX = badgeX + (KEY_BADGE - bw + if (conflictIcon) 11 else 0) / 2
            if (conflictIcon) sprite(graphics, icon("warn"), textX - 12, y + 4, 9, 9, UiTheme.CRITICAL)
            graphics.text(font, row.right, textX, y + 5, UiTheme.argb(row.rightColor, 1f))
        }

        // The group these keys belong to, and the footer: hint, capture or clash.
        val fy = cy + ch - FOOTER
        UiTheme.hairline(graphics, cx + 8, fy - 3, cw - 16, UiTheme.ACCENT_DEEP, 1f)
        footer.forEachIndexed { i, (line, _) ->
            val ly = fy + 2 + i * LINE
            if (line === footer.getOrNull(1)?.first) sprite(graphics, icon("warn"), cx + 8, ly - 1, 9, 9, UiTheme.CRITICAL)
            graphics.text(font, line, if (i == 1) cx + 20 else cx + 8, ly, -1)
        }
        val groupLabel = tr("keys_group", groupTitle(group))
        graphics.text(font, groupLabel, cx + cw - 8 - font.width(groupLabel), fy + 2, UiTheme.argb(UiTheme.MUTED, 1f))
    }

    private fun drawGroups(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        graphics.text(font, tr("groups_title"), cx + 8, cy + 6, UiTheme.argb(UiTheme.ACCENT, 1f))
        ClientOptions.groups.indices.forEach { i ->
            val y = groupY(i)
            val on = i == ClientOptions.activeGroup
            sprite(graphics, if (on) ROW_ON else ROW_OFF, cx + 4, y, lw - 8, GROUP_ROW - 2)
            if (!on && inside(mouseX, mouseY, cx + 4, y, lw - 8, GROUP_ROW - 2)) graphics.outline(cx + 4, y, lw - 8, GROUP_ROW - 2, UiTheme.argb(UiTheme.ACCENT_DEEP, 1f))
            graphics.text(font, groupRows.getOrElse(i) { FormattedCharSequence.EMPTY }, cx + 10, y + 5, UiTheme.argb(if (on) UiTheme.TEXT else UiTheme.MUTED, 1f))
            val count = groupCounts.getOrElse(i) { FormattedCharSequence.EMPTY }
            graphics.text(font, count, cx + lw - 12 - font.width(count), y + 5, UiTheme.argb(if (on) UiTheme.ACCENT else UiTheme.MUTED, 1f))
        }
        // Action buttons: new, up, down, delete.
        val actionsY = cy + ch - 20
        val active = ClientOptions.activeGroup
        val enabled = listOf((ClientOptions.groups.size < MAX_GROUPS), active > 1, active in 1 until ClientOptions.groups.lastIndex, active > 0)
        ACTION_ICONS.forEachIndexed { i, name ->
            val x = cx + 6 + i * 18
            val armed = i == 3 && confirmDelete > 0
            val hover = inside(mouseX, mouseY, x, actionsY, 16, 16)
            sprite(graphics, if (armed) ROW_ON else INSET, x, actionsY, 16, 16)
            val rgb = when {
                !enabled[i] -> UiTheme.TRACK
                armed || (i == 3 && hover) -> UiTheme.CRITICAL
                hover -> UiTheme.ACCENT
                else -> UiTheme.MUTED
            }
            sprite(graphics, icon(name), x + 4, actionsY + 4, 9, 9, rgb)
        }
        val hint = when {
            confirmDelete > 0 -> tr("delete_confirm").withColor(UiTheme.CRITICAL)
            else -> ACTION_ICONS.indices.firstOrNull { inside(mouseX, mouseY, cx + 6 + it * 18, actionsY, 16, 16) }
                ?.let { tr("action.${ACTION_ICONS[it]}").withColor(UiTheme.MUTED) }
        }
        hint?.let { graphics.text(font, fit(it, lw - 84), cx + 80, actionsY + 4, -1) }

        // Right page: the open group's name and its members.
        if (group.isAll) {
            graphics.text(font, tr("all_group"), rx + 8, cy + 6, UiTheme.argb(UiTheme.ACCENT, 1f))
            graphics.textWithWordWrap(font, tr("all_group_hint"), rx + 8, cy + 20, rw - 16, UiTheme.argb(UiTheme.MUTED, 1f))
            return
        }
        graphics.text(font, tr("group_name"), rx + 8, cy + 6, UiTheme.argb(UiTheme.MUTED, 1f))
        val top = cy + 36
        val shown = rows.drop(scroll).take(groupVisible())
        shown.forEachIndexed { i, row ->
            val y = top + i * ROW
            if (inside(mouseX, mouseY, rx + 4, y, rw - 8, ROW - 1)) graphics.outline(rx + 4, y, rw - 8, ROW - 1, UiTheme.argb(UiTheme.ACCENT_DEEP, 1f))
            sprite(graphics, INSET, rx + 7, y + 4, 10, 10)
            graphics.item(row.entry!!.icon, rx + 20, y + 1)
        }
        scrollbar(graphics, rx + rw - 5, top, groupVisible() * ROW, rows.size, groupVisible(), scroll)
        graphics.nextStratum()
        shown.forEachIndexed { i, row ->
            val y = top + i * ROW
            val member = group.contains(row.entry!!.identity)
            if (member) sprite(graphics, icon("check"), rx + 8, y + 4, 9, 9, accent)
            graphics.text(font, row.text, rx + 40, y + 5, UiTheme.argb(if (member) UiTheme.TEXT else UiTheme.MUTED, 1f))
        }
    }

    // ── Drawing helpers ─────────────────────────────────────────────────────────

    private fun sprite(graphics: GuiGraphicsExtractor, id: Identifier, x: Int, y: Int, w: Int, h: Int, rgb: Int = 0xFFFFFF) =
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h, UiTheme.argb(rgb, 1f))

    /** A gold rule with a lozenge in the middle — the book's divider. */
    private fun rule(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int) {
        val mid = x + w / 2
        graphics.fill(x, y, x + w, y + 1, UiTheme.argb(UiTheme.ACCENT_DEEP, 1f))
        graphics.fill(mid - 2, y - 1, mid + 3, y + 2, UiTheme.argb(UiTheme.PANEL, 1f))
        graphics.fill(mid, y - 2, mid + 1, y + 3, UiTheme.argb(UiTheme.ACCENT, 1f))
        graphics.fill(mid - 2, y, mid + 3, y + 1, UiTheme.argb(UiTheme.ACCENT, 1f))
        graphics.fill(mid - 1, y - 1, mid + 2, y + 2, UiTheme.argb(UiTheme.ACCENT, 1f))
    }

    private fun scrollbar(graphics: GuiGraphicsExtractor, x: Int, y: Int, h: Int, total: Int, shown: Int, offset: Int) {
        if (total <= shown) return
        graphics.fill(x, y, x + 2, y + h, UiTheme.argb(UiTheme.TRACK, 1f))
        val thumb = (h * shown / total).coerceAtLeast(8)
        val ty = y + (h - thumb) * offset / (total - shown).coerceAtLeast(1)
        graphics.fill(x, ty, x + 2, ty + thumb, UiTheme.argb(UiTheme.ACCENT_DEEP, 1f))
    }

    /** An icon and a number side by side; returns where the next chip starts. */
    private fun chip(graphics: GuiGraphicsExtractor, iconName: String, text: String, x: Int, y: Int, rgb: Int, critical: Boolean): Int {
        sprite(graphics, if (critical && iconName == "spirit") DRAINED_SPIRIT else icon(iconName), x, y, if (critical && iconName == "spirit") 5 else 9, if (critical && iconName == "spirit") 7 else 9, if (critical) UiTheme.CRITICAL else rgb)
        graphics.text(font, text, x + 11, y + 1, UiTheme.argb(if (critical) UiTheme.CRITICAL else UiTheme.TEXT, 1f))
        return x + 11 + font.width(text) + 8
    }

    private fun tabY(i: Int) = by + HEADER + 4 + i * (TAB_H + 3)
    private fun filterX(i: Int) = cx + lw - 4 - (FILTERS - i) * 15
    private fun groupY(i: Int) = cy + 20 + i * GROUP_ROW
    private fun modesTop(): Int {
        val entry = chosenEntry() ?: return 0
        val hasChips = entry.cost > 0 || entry.upkeep > 0 || entry.cooldownSeconds > 0
        return cy + 31 + 12 + (if (hasChips) 13 else 0) + 12
    }
    private fun keyLineY() = cy + ch - 15
    private fun detailVisibleFrom(y: Int) = ((keyLineY() - 6 - y) / LINE).coerceAtLeast(1)
    private fun detailVisible(): Int {
        val entry = chosenEntry() ?: return 1
        return detailVisibleFrom(modesTop() + entry.modes.size * MODE_ROW + 8)
    }

    private fun inside(x: Int, y: Int, x0: Int, y0: Int, w: Int, h: Int) = x >= x0 && x < x0 + w && y >= y0 && y < y0 + h
    private fun inside(x: Double, y: Double, x0: Int, y0: Int, w: Int, h: Int) = inside(x.toInt(), y.toInt(), x0, y0, w, h)

    private fun tr(key: String, vararg args: Any): net.minecraft.network.chat.MutableComponent = Component.translatable("book.$MOD_ID.$key", *args)

    private fun plain(text: String): FormattedCharSequence = FormattedCharSequence.forward(text, net.minecraft.network.chat.Style.EMPTY)

    /** The text cut to [width] with an ellipsis — long names must not run under the numbers. */
    private fun fit(text: Component, width: Int): FormattedCharSequence {
        if (font.width(text) <= width) return text.visualOrderText
        val cut = font.substrByWidth(text, (width - font.width(ELLIPSIS)).coerceAtLeast(0))
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of(ELLIPSIS)))
    }

    private fun band(sanity: SanityLoss): String = when {
        sanity.isNegligible() -> "negligible"
        sanity.isMinor() -> "minor"
        sanity.isModerate() -> "moderate"
        sanity.isSerious() -> "serious"
        sanity.isSevere() -> "severe"
        sanity.isCritical() -> "critical"
        else -> "extreme"
    }

    private fun typeIcon(type: AbilityType) = when (type) {
        AbilityType.ACTIVE -> "active"
        AbilityType.TOGGLEABLE_PASSIVE -> "toggle"
        AbilityType.PERMANENT_PASSIVE -> "passive"
    }

    companion object {
        /** The section the book opens on: the one it was closed on. */
        private var lastPage = Page.LIBRARY

        private const val MAX_W = 560
        private const val MAX_H = 330
        private const val SPINE = 36
        private const val HEADER = 30
        private const val GAP = 6
        private const val ROW = 18
        private const val LINE = 10
        private const val MODE_ROW = 12
        private const val GROUP_ROW = 18
        private const val TAB_W = 24
        private const val TAB_H = 22
        private const val KEY_BADGE = 84
        private const val FOOTER = 24
        private const val FILTERS = 4
        private const val NAME_LENGTH = 24
        private const val MAX_GROUPS = 9
        private const val CONFIRM_TICKS = 60
        private const val ELLIPSIS = "…"

        /** Band edges of the sanity scale, as the rules count them (`SanityLoss`). */
        private val SANITY_MARKS = intArrayOf(10, 20, 40, 60, 80, 95)
        private val FILTER_TYPES = listOf(null, AbilityType.ACTIVE, AbilityType.TOGGLEABLE_PASSIVE, AbilityType.PERMANENT_PASSIVE)
        private val FILTER_ICONS = listOf("all", "active", "toggle", "passive")
        private val ACTION_ICONS = listOf("plus", "up", "down", "cross")

        private fun sprite(name: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, "grimoire/$name")
        private val COVER = sprite("cover")
        private val LEAF = sprite("leaf")
        private val INSET = sprite("inset")
        private val SLOT = sprite("slot")
        private val ROW_ON = sprite("row_on")
        private val ROW_OFF = sprite("row")
        private val TAB = sprite("tab")
        private val TAB_ON = sprite("tab_on")
        private val SEAL: Identifier = Identifier.fromNamespaceAndPath(MOD_ID, "wheel/atmos_seal")
        private val DRAINED_SPIRIT: Identifier = Identifier.fromNamespaceAndPath(MOD_ID, "spirit/drained")
        private val EMBLEMS = setOf("sun", "tyrant", "whitetower", "door", "error", "visionary", "justiciar", "fool", "death")
        private val icons = HashMap<String, Identifier>()

        private fun icon(name: String): Identifier = icons.getOrPut(name) { sprite("icon/$name") }
        private fun tabIcon(page: Page): Identifier = icons.getOrPut("tab/" + page.icon) { sprite("tab/${page.icon}") }

        /** The pathway's emblem; a pathway without one yet (the stubs) gets the heptagram. */
        fun emblemOf(pathway: String?): Identifier =
            sprite("emblem/" + (pathway?.lowercase()?.takeIf { it in EMBLEMS } ?: "default"))
    }
}
