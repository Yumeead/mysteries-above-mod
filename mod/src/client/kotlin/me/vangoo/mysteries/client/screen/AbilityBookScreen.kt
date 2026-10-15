package me.vangoo.mysteries.client.screen

import com.mojang.blaze3d.platform.InputConstants
import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.AbilityGroup
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.input.ModKeys
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.AbilityActionPayload
import me.vangoo.mysteries.net.payload.AbilityActionPayload.Action
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

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
 * The ability book: every ability the player holds, read in full, and the place where groups and
 * hotkeys are set up. The wheel and the hotkeys only use what is decided here.
 *
 * Tabs along the top are the groups, and the open tab **is** the active group — there is no
 * second notion of "the group being edited". The list on the left is the whole roster; in a named
 * group the members are ticked and a click on "add / remove" changes that. The right side is the
 * chosen ability: its full description, its numbers, and its key in this group.
 *
 * A key is captured from the next press: Esc cancels, Backspace clears, and a mouse button beyond
 * the two main ones counts too. Any key is accepted; if vanilla or another mod already uses it,
 * the book says so — both will fire, and choosing is the player's business.
 *
 * The list is cut into Sequences, the way the pathway grants them: a heading "Посл. 9 · Sailor" in the
 * pathway colour, then what awoke there. An ability with modes is a small tree: its modes hang under it as indented rows. Clicking a mode
 * picks it on the server (the ● follows), and a key bound on a mode row casts that ability in that
 * mode — so one door can sit on C as "drag" and on V as "purge".
 *
 * Every text is built on change (opening, a click, a scroll, a fresh roster), never in the frame.
 */
class AbilityBookScreen : Screen(Component.translatable("book.$MOD_ID.title")) {

    private var entries: List<AbilityRosterPayload.Entry> = ClientAbilityState.entries
    private var chosen: String? = ClientAbilityState.selected ?: entries.firstOrNull()?.identity

    /** The mode row chosen under [chosen]; `null` — the ability row itself. */
    private var chosenMode: String? = null
    private var capturing = false
    private var scroll = 0

    private var description: List<Component> = emptyList()
    private var warnings: List<Component> = emptyList()
    private var header: List<Component> = emptyList()
    private var accent = UiTheme.ACCENT

    /**
     * One line of the list: a Sequence heading (`entry == null`), an ability, or one of its modes
     * (then [text] carries the tree branch).
     */
    private class Row(val entry: AbilityRosterPayload.Entry?, val mode: AbilityRosterPayload.Mode?, val text: Component)

    /** Every row, the rows on screen and their hotkey labels, cut to [scroll] and [rows]. */
    private var tree: List<Row> = emptyList()
    private var shown: List<Row> = emptyList()
    private var labels: List<Component?> = emptyList()
    private var title: Component = Component.empty()

    private val group: AbilityGroup get() = ClientOptions.group

    private val listTop get() = TOP + ROW_STEP * 2
    private val listWidth get() = width / 2 - MARGIN - GAP / 2
    private val rows get() = ((height - listTop - MARGIN) / ROW).coerceAtLeast(1)
    private val detailX get() = width / 2 + GAP / 2
    private val detailWidth get() = width / 2 - MARGIN - GAP / 2

    override fun init() {
        var x = MARGIN
        ClientOptions.groups.forEachIndexed { index, group ->
            val title = groupTitle(group)
            val w = (font.width(title) + TAB_PADDING).coerceIn(TAB_MIN, TAB_MAX)
            val tab = Button.builder(title) { open(index) }.bounds(x, TOP, w, BUTTON).build()
            tab.active = index != ClientOptions.activeGroup
            addRenderableWidget(tab)
            x += w + 2
        }
        if (ClientOptions.groups.size < MAX_GROUPS) {
            addRenderableWidget(Button.builder(Component.literal("+")) { addGroup() }.bounds(x, TOP, BUTTON, BUTTON).build())
        }

        if (!group.isAll) {
            val name = EditBox(font, MARGIN, TOP + ROW_STEP, NAME_WIDTH, BUTTON, Component.translatable("book.$MOD_ID.group_name"))
            name.setMaxLength(NAME_LENGTH)
            name.value = group.name
            // Renaming must not rebuild the widgets, or the box would lose focus on every letter.
            name.setResponder { text -> group.name = text; ClientOptions.save() }
            addRenderableWidget(name)
            addRenderableWidget(
                Button.builder(Component.translatable("book.$MOD_ID.delete_group")) { deleteGroup() }
                    .bounds(MARGIN + NAME_WIDTH + 4, TOP + ROW_STEP, DELETE_WIDTH, BUTTON).build(),
            )
        }

        val entry = chosenEntry()
        if (entry != null && entry.type != AbilityType.PERMANENT_PASSIVE) {
            val key = when {
                capturing -> Component.translatable("book.$MOD_ID.press_key")
                else -> Component.translatable(
                    "book.$MOD_ID.key",
                    ModKeys.hotkeyLabel(entry.identity, chosenMode) ?: Component.translatable("book.$MOD_ID.no_key"),
                )
            }
            addRenderableWidget(
                Button.builder(key) { capturing = !capturing; rebuildWidgets() }
                    .bounds(detailX, height - MARGIN - BUTTON, detailWidth, BUTTON).build(),
            )
            if (!group.isAll) {
                val label = if (group.contains(entry.identity)) "book.$MOD_ID.remove" else "book.$MOD_ID.add"
                addRenderableWidget(
                    Button.builder(Component.translatable(label)) { ClientOptions.editGroups { group.toggle(entry.identity) }; rebuildWidgets() }
                        .bounds(detailX, height - MARGIN - BUTTON * 2 - 4, detailWidth, BUTTON).build(),
                )
            }
        }
        rebuildDetail()
    }

    override fun tick() {
        if (ClientAbilityState.entries.isEmpty()) {
            onClose()
            return
        }
        if (ClientAbilityState.entries !== entries) {
            entries = ClientAbilityState.entries
            if (chosenEntry() == null) chosen = entries.firstOrNull()?.identity
            if (chosenEntry()?.modes?.none { it.id == chosenMode } != false) chosenMode = null
            rebuildWidgets()
        }
    }

    // ── Actions ─────────────────────────────────────────────────────────────────

    private fun open(index: Int) {
        ClientOptions.activeGroup = index
        capturing = false
        rebuildWidgets()
    }

    private fun addGroup() {
        ClientOptions.editGroups {
            val name = Component.translatable("book.$MOD_ID.new_group", ClientOptions.groups.size).string
            ClientOptions.groups += AbilityGroup(name, mutableListOf())
            ClientOptions.activeGroup = ClientOptions.groups.lastIndex
        }
        rebuildWidgets()
    }

    private fun deleteGroup() {
        val index = ClientOptions.activeGroup
        if (index == 0) return
        ClientOptions.editGroups {
            ClientOptions.groups.removeAt(index)
            ClientOptions.activeGroup = 0
        }
        rebuildWidgets()
    }

    private fun bind(key: InputConstants.Key?) {
        val target = AbilityGroup.target(chosen ?: return, chosenMode)
        ClientOptions.editGroups { if (key == null) group.unbind(target) else group.bind(key.name, target) }
        capturing = false
        rebuildWidgets()
    }

    // ── Input ───────────────────────────────────────────────────────────────────

    override fun keyPressed(event: KeyEvent): Boolean {
        if (capturing) {
            when (event.key()) {
                GLFW.GLFW_KEY_ESCAPE -> { capturing = false; rebuildWidgets() }
                GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE -> bind(null)
                else -> bind(InputConstants.getKey(event))
            }
            return true
        }
        // The book key closes the book again — unless the player is typing a group name.
        if (focused !is EditBox && ModKeys.isBookKey(event)) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (capturing && event.button() > GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            bind(InputConstants.Type.MOUSE.getOrCreate(event.button()))
            return true
        }
        if (super.mouseClicked(event, doubleClick)) return true

        val x = event.x()
        val y = event.y()
        if (x < MARGIN || x > MARGIN + listWidth || y < listTop) return false
        val index = ((y - listTop) / ROW).toInt()
        val row = tree.getOrNull(scroll + index)?.takeIf { index < rows } ?: return false
        if (row.entry == null) return true
        chosen = row.entry.identity
        chosenMode = row.mode?.id
        // A mode row also picks the mode: the wheel's card and the HUD follow it.
        if (row.mode != null && ClientAbilityState.modeOf(row.entry.identity) != row.mode.id) {
            ModChannels.ABILITY_ACTION.sendToServer(AbilityActionPayload(Action.MODE, row.entry.identity, row.mode.id))
        }
        capturing = false
        rebuildWidgets()
        return true
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (mouseX > MARGIN + listWidth || scrollY == 0.0) return false
        scroll = (scroll + if (scrollY < 0) 1 else -1).coerceIn(0, (tree.size - rows).coerceAtLeast(0))
        rebuildList()
        return true
    }

    // ── State ───────────────────────────────────────────────────────────────────

    private fun chosenEntry(): AbilityRosterPayload.Entry? = chosen?.let { id -> entries.firstOrNull { it.identity == id } }

    private fun rebuildList() {
        val pathwayRgb = ClientAbilityState.roster?.let { PathwayBranding.textOf(it.pathwayId).packed } ?: UiTheme.ACCENT
        // The roster already runs Seq. 9 upwards, then off the pathway — a heading wherever that changes.
        tree = entries.withIndex().flatMap { (i, entry) ->
            val heading = if (i == 0 || entries[i - 1].origin?.level != entry.origin?.level) {
                listOf(Row(null, null, originLine(entry.origin, pathwayRgb)))
            } else {
                emptyList()
            }
            heading + Row(entry, null, entry.name) + entry.modes.mapIndexed { index, mode ->
                val branch = if (index == entry.modes.lastIndex) "└ " else "├ "
                Row(entry, mode, Component.literal(branch).append(mode.name))
            }
        }
        scroll = scroll.coerceIn(0, (tree.size - rows).coerceAtLeast(0))
        shown = tree.drop(scroll).take(rows)
        labels = shown.map { row -> row.entry?.let { ModKeys.hotkeyLabel(it.identity, row.mode?.id) } }
    }

    /**
     * Whose powers these are. Inside a puppet the list holds the puppet's, so its pathway heads
     * the line in that pathway's colour, and the Marionettist's own follows in grey — whose body,
     * then whose soul.
     */
    private fun rebuildHeader() {
        val roster = ClientAbilityState.roster
        accent = roster?.let { PathwayBranding.textOf(it.pathwayId).packed } ?: UiTheme.ACCENT
        val worn = roster?.worn
        header = when {
            roster == null -> emptyList()
            worn != null -> listOf(
                pathwayLine(worn.pathwayId, worn.sequence.level, worn.pathwayId),
                Component.translatable("book.$MOD_ID.own_pathway", "${roster.pathwayId} · ${roster.sequenceName.string}")
                    .withColor(UiTheme.MUTED),
            )
            else -> listOf(pathwayLine(roster.pathwayId, roster.sequence.level, roster.sequenceName.string))
        }
    }

    private fun pathwayLine(pathwayId: String, sequence: Int, name: String): Component =
        Component.translatable("book.$MOD_ID.pathway", name, sequence)
            .withColor(PathwayBranding.textOf(pathwayId).packed)

    /** The right column's text, built on change rather than in the frame. */
    private fun rebuildDetail() {
        rebuildList()
        rebuildHeader()
        val entry = chosenEntry()
        if (entry == null) {
            description = emptyList()
            warnings = emptyList()
            return
        }
        val mode = entry.modes.firstOrNull { it.id == chosenMode }
        title = if (mode == null) entry.name else entry.name.copy().append(" · ").append(mode.name)
        description = buildList {
            add(originLine(entry.origin, accent))
            add(Component.translatable("book.$MOD_ID.type.${entry.type.name.lowercase()}"))
            if (entry.cost > 0) add(Component.translatable("book.$MOD_ID.cost", entry.cost))
            if (entry.upkeep > 0) add(Component.translatable("book.$MOD_ID.upkeep", entry.upkeep))
            if (entry.cooldownSeconds > 0) add(Component.translatable("book.$MOD_ID.cooldown", entry.cooldownSeconds))
            add(entry.description)
        }
        val conflict = group.keyOf(AbilityGroup.target(entry.identity, chosenMode))?.let(InputConstants::getKey)?.let { key ->
            val clashes = minecraft.options.keyMappings.filter { it.matches(key) }
            if (clashes.isEmpty()) null
            else Component.translatable(
                "book.$MOD_ID.conflict",
                clashes.joinToString(", ") { Component.translatable(it.name).string },
            )
        }
        warnings = listOfNotNull(
            Component.translatable("book.$MOD_ID.capture_hint").takeIf { capturing },
            conflict,
        )
    }

    // ── Frame ───────────────────────────────────────────────────────────────────

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        val alpha = ClientOptions.opacity

        UiTheme.panel(graphics, MARGIN - 2, listTop - 2, listWidth + 4, rows * ROW + 4, alpha)
        UiTheme.panel(graphics, detailX - 2, listTop - 2, detailWidth + 4, height - listTop - MARGIN + 4, alpha)
        shown.forEachIndexed { index, row ->
            if (row.entry != null && row.mode == null) graphics.item(row.entry.icon, MARGIN + 2, listTop + index * ROW + 2)
        }

        // Items go in their own pass; text that must lie over them needs a new stratum.
        graphics.nextStratum()

        shown.forEachIndexed { index, row ->
            val y = listTop + index * ROW
            val entry = row.entry
            if (entry == null) {
                // A Sequence heading: the number and the name, a hairline running on to the edge.
                val textWidth = font.width(row.text)
                graphics.text(font, row.text, MARGIN + 4, y + 8, UiTheme.argb(UiTheme.TEXT, alpha))
                UiTheme.hairline(graphics, MARGIN + 8 + textWidth, y + 12, listWidth - textWidth - 12, accent, alpha * HEADING_RULE)
                return@forEachIndexed
            }
            val member = group.contains(entry.identity)
            val color = if (member && row.mode == null) UiTheme.TEXT else UiTheme.MUTED
            val a = if (member) alpha else alpha * DIM
            graphics.text(font, row.text, MARGIN + 22, y + 6, UiTheme.argb(color, a))
            // The mode the server holds as picked: a map lookup per row, nothing built.
            if (row.mode != null && ClientAbilityState.modeOf(entry.identity) == row.mode.id) {
                graphics.text(font, "●", MARGIN + 10, y + 6, UiTheme.argb(UiTheme.ACCENT, a))
            }
            if (!group.isAll && member && row.mode == null) graphics.text(font, "✔", MARGIN + listWidth - 10, y + 6, UiTheme.argb(UiTheme.ACCENT, alpha))
            labels[index]?.let {
                graphics.text(font, it, MARGIN + listWidth - 14 - font.width(it), y + 6, UiTheme.argb(UiTheme.MUTED, alpha))
            }
            if (entry.identity == chosen && row.mode?.id == chosenMode) graphics.outline(MARGIN, y, listWidth, ROW, UiTheme.argb(accent, alpha))
        }

        var y = listTop + 2
        header.forEach { line ->
            graphics.text(font, line, detailX + 2, y, UiTheme.argb(UiTheme.TEXT, alpha))
            y += font.lineHeight + 2
        }
        if (header.isNotEmpty()) y += 4

        if (chosenEntry() == null) return
        graphics.text(font, title, detailX + 2, y, UiTheme.argb(accent, alpha))
        y += font.lineHeight + 4
        description.forEachIndexed { index, line ->
            val color = if (index == description.lastIndex) UiTheme.TEXT else UiTheme.MUTED
            graphics.textWithWordWrap(font, line, detailX + 2, y, detailWidth - 4, UiTheme.argb(color, alpha))
            y += font.wordWrapHeight(line, detailWidth - 4) + 2
        }
        val buttonsTop = height - MARGIN - BUTTON * (if (group.isAll) 1 else 2) - 8
        warnings.forEachIndexed { index, line ->
            graphics.text(font, line, detailX + 2, buttonsTop - (warnings.size - index) * (font.lineHeight + 2), UiTheme.argb(UiTheme.CRITICAL, alpha))
        }
    }

    private companion object {
        const val MARGIN = 12
        const val GAP = 12
        const val TOP = 10
        const val BUTTON = 20
        const val ROW_STEP = 24
        const val ROW = 20
        const val TAB_PADDING = 16
        const val TAB_MIN = 40
        const val TAB_MAX = 110
        const val NAME_WIDTH = 140
        const val NAME_LENGTH = 24
        const val DELETE_WIDTH = 110
        const val MAX_GROUPS = 9
        const val DIM = 0.55f
        const val HEADING_RULE = 0.5f
    }
}
