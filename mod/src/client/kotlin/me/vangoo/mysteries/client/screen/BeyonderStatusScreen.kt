package me.vangoo.mysteries.client.screen

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import me.vangoo.mysteries.net.payload.BeyonderStatusPayload
import me.vangoo.mysteries.net.payload.StatusRequestPayload
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * The phase 02 test screen, brought up to milestone M2: it now prints the ability list.
 *
 * It is still disposable by design — the real surfaces (the HUD, the wheel, the book) arrive
 * with milestones M3–M6, and then this screen is deleted. Until then it proves exactly one
 * thing, and exactly the thing that needs proving now: the read model reached the client whole
 * and stays alive between packets. The names, costs and icons came with the roster, the
 * spirituality and the cooldown seconds with the state, and they count down between packets
 * because [ClientAbilityState] does the counting itself.
 */
class BeyonderStatusScreen(private var status: BeyonderStatusPayload) :
    Screen(Component.literal("Стан Потойбічного")) {

    /** A list row: an icon and two ready-made texts. */
    private data class Row(val icon: ItemStack, val title: Component, val detail: Component, val accent: Boolean)

    private var responses = 1
    private var header: List<Component> = emptyList()
    private var rows: List<Row> = emptyList()

    override fun init() {
        val buttonWidth = 120
        val centerX = width / 2 - buttonWidth / 2

        addRenderableWidget(
            Button.Builder(Component.literal("Оновити")) {
                ModChannels.STATUS_REQUEST.sendToServer(StatusRequestPayload)
            }.bounds(centerX, height - 52, buttonWidth, 20).build(),
        )

        addRenderableWidget(
            Button.Builder(Component.literal("Закрити")) { onClose() }
                .bounds(centerX, height - 28, buttonWidth, 20)
                .build(),
        )

        rebuild()
    }

    /**
     * The client tick, not the frame: the cooldown seconds count down twenty times a second at
     * most, while `extractRenderState` is called every frame and nothing may be built in it —
     * retained-mode only reads what is ready.
     */
    override fun tick() {
        rebuild()
    }

    /** Called from the client receiver when the server has sent fresh state. */
    fun update(fresh: BeyonderStatusPayload) {
        status = fresh
        responses++
        rebuild()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)

        var y = TOP
        graphics.centeredText(font, title, width / 2, y, TITLE_COLOR)
        y += LINE + 4

        for (line in header) {
            graphics.centeredText(font, line, width / 2, y, TEXT_COLOR)
            y += LINE
        }

        y += 6
        val left = width / 2 - LIST_WIDTH / 2
        for (row in rows) {
            graphics.item(row.icon, left, y)
            graphics.text(font, row.title, left + 20, y, if (row.accent) ACCENT_COLOR else TITLE_COLOR)
            graphics.text(font, row.detail, left + 20, y + 10, TEXT_COLOR)
            y += ROW
        }
    }

    private fun rebuild() {
        header = buildHeader()
        rows = buildRows()
    }

    private fun buildHeader(): List<Component> {
        val beyonder = status.beyonder
            ?: return listOf(
                Component.literal("Ще не Потойбічний"),
                Component.literal("відповідей від сервера: $responses"),
            )

        // Spirituality comes from the state channel rather than the status one: that arrives only
        // on events, while this one arrives on regeneration too, so the number really does grow
        // before your eyes.
        val spirituality = ClientAbilityState.spirituality
            ?: beyonder.spirituality

        return listOf(
            pathwayLine(beyonder),
            Component.literal("Духовність: ${spirituality.current} / ${spirituality.maximum}"),
            Component.literal("відповідей від сервера: $responses"),
        )
    }

    /**
     * Inside a puppet the list holds the puppet's powers, so its pathway heads the line in that
     * pathway's colour, and the Marionettist's own follows in grey — whose body, then whose soul.
     */
    private fun pathwayLine(beyonder: BeyonderStatusPayload.Beyonder): Component {
        val own = "${beyonder.pathwayId} · Послідовність ${beyonder.sequence.level}"
        val worn = beyonder.worn ?: return Component.literal("Шлях: $own")
        return Component.literal("Шлях: ")
            .append(
                Component.literal("${worn.pathwayId} · Послідовність ${worn.sequence.level}")
                    .withColor(PathwayBranding.textOf(worn.pathwayId).packed),
            )
            .append(Component.literal("  (ваш: $own)").withColor(OWN_PATHWAY_GREY))
    }

    private fun buildRows(): List<Row> {
        val selected = ClientAbilityState.selected

        return ClientAbilityState.entries.map { entry ->
            val chosen = entry.identity == selected
            Row(
                icon = entry.icon,
                title = Component.literal(if (chosen) "▶ " else "  ").append(entry.name),
                detail = Component.literal(detailOf(entry)),
                accent = chosen,
            )
        }
    }

    private fun detailOf(entry: AbilityRosterPayload.Entry): String {
        val parts = mutableListOf<String>()

        // Non-zero prices rather than the ability type: a sustained power is declared active, and
        // "✦ 0" on it would mean it is free.
        if (entry.cost > 0) parts += "✦ ${entry.cost}"
        if (entry.upkeep > 0) parts += "✦ ${entry.upkeep}/с"
        if (parts.isEmpty()) {
            parts += when (entry.type) {
                AbilityType.ACTIVE -> "✦ 0"
                AbilityType.TOGGLEABLE_PASSIVE -> "перемикач"
                AbilityType.PERMANENT_PASSIVE -> "пасивна"
            }
        }

        val seconds = ClientAbilityState.remainingSeconds(entry.identity)
        if (seconds > 0) parts += "$seconds с" else if (entry.type == AbilityType.ACTIVE) parts += "готова"

        if (ClientAbilityState.isToggled(entry.identity)) parts += "●"
        if (ClientAbilityState.isSustaining(entry.identity)) parts += "◐"
        if (!ClientAbilityState.canAfford(entry)) parts += "✕ бракує"

        return parts.joinToString(" · ")
    }

    override fun isPauseScreen(): Boolean = false

    private companion object {
        /** The Marionettist's own pathway, set aside while they wear someone else's. */
        const val OWN_PATHWAY_GREY = 0x8A8A8A

        const val TOP = 24
        const val LINE = 12
        const val ROW = 22
        const val LIST_WIDTH = 200
        const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        const val TEXT_COLOR = 0xFFA0A0A0.toInt()
        const val ACCENT_COLOR = 0xFFC8A44B.toInt()
    }
}
