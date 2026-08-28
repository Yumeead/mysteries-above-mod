package me.vangoo.mysteries.client.screen

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
 * Тестовий екран фази 02, дотягнутий до віхи M2: тепер він друкує список здібностей.
 *
 * Він і далі одноразовий за задумом — справжні поверхні (HUD, колесо, книга) приїдуть
 * віхами M3–M6, і тоді цей екран видаляється. Доти він доводить рівно одне, і саме те,
 * що потрібно довести зараз: читальна модель дійшла до клієнта цілою й живе між пакетами.
 * Назви, ціни й іконки прийшли ростером, духовність і секунди перепочинку — станом, а
 * спадають вони між пакетами, бо [ClientAbilityState] відлічує сам.
 */
class BeyonderStatusScreen(private var status: BeyonderStatusPayload) :
    Screen(Component.literal("Стан Потойбічного")) {

    /** Рядок списку: іконка та два готові тексти. */
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
     * Такт клієнта, а не кадр: секунди перепочинку спадають двадцять разів на секунду
     * щонайбільше, а `extractRenderState` кличеться щокадру й нічого будувати в ньому
     * не можна — retained-mode лише читає готове.
     */
    override fun tick() {
        rebuild()
    }

    /** Викликається з клієнтського приймача, коли сервер надіслав свіжий стан. */
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

        // Духовність — з каналу стану, а не зі статусного: той приходить лише на подіях,
        // а цей ще й на регенерацію, тож число справді росте на очах.
        val spirituality = ClientAbilityState.spirituality
            ?: beyonder.spirituality

        return listOf(
            Component.literal("Шлях: ${beyonder.pathwayId} · Послідовність ${beyonder.sequence.level}"),
            Component.literal("Дух: ${spirituality.current} / ${spirituality.maximum}"),
            Component.literal("відповідей від сервера: $responses"),
        )
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

        // Ненульові ціни, а не тип здібності: утримувана сила оголошена активною, і
        // «✦ 0» у неї означало б, що вона безкоштовна.
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
        if (!ClientAbilityState.canAfford(entry)) parts += "✕ мало"

        return parts.joinToString(" · ")
    }

    override fun isPauseScreen(): Boolean = false

    private companion object {
        const val TOP = 24
        const val LINE = 12
        const val ROW = 22
        const val LIST_WIDTH = 200
        const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        const val TEXT_COLOR = 0xFFA0A0A0.toInt()
        const val ACCENT_COLOR = 0xFFC8A44B.toInt()
    }
}
