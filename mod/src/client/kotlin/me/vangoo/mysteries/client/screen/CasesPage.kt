package me.vangoo.mysteries.client.screen

import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.beyonder.ClientDossiers
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.net.payload.DossiersPayload
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.PlayerFaceExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.SpawnEggItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

/**
 * The book's «Справи»: the Sheriff's files of those they have recognised (Recognition), shown only while there is at
 * least one. Left, the remembered ones, newest first, each with their face and whether they are near; right, the
 * chosen one in full: the body as this client sees it (or the face), what they are, whether they are a Beyonder, where
 * they are now or were last sensed, and when and where they were first remembered.
 *
 * Built on change and twice a second, never in the frame; the book hands in its geometry and its sprite drawer.
 */
internal class CasesPage(
    private val font: Font,
    private val sprite: (GuiGraphicsExtractor, Identifier, Int, Int, Int, Int) -> Unit,
    private val rowOn: Identifier,
    private val slot: Identifier,
) {
    private var cx = 0; private var cy = 0; private var lw = 0; private var rx = 0; private var rw = 0; private var ch = 0
    private var accent = UiTheme.ACCENT

    private var chosen: UUID? = null
    private var scroll = 0
    private var built: List<DossiersPayload.Entry>? = null

    private class Row(val entry: DossiersPayload.Entry, val name: FormattedCharSequence, val state: Component, val stateRgb: Int)

    private var rows: List<Row> = emptyList()
    private var lines: List<Pair<FormattedCharSequence, Int>> = emptyList()
    private var title: FormattedCharSequence = FormattedCharSequence.EMPTY
    private var kind: FormattedCharSequence = FormattedCharSequence.EMPTY

    fun layout(cx: Int, cy: Int, lw: Int, rx: Int, rw: Int, ch: Int, accent: Int) {
        this.cx = cx; this.cy = cy; this.lw = lw; this.rx = rx; this.rw = rw; this.ch = ch; this.accent = accent
        rebuild()
    }

    /** Whether the files changed since the page was built: the book then lays everything out anew. */
    val stale: Boolean get() = built !== ClientDossiers.entries

    fun rebuild() {
        val entries = ClientDossiers.entries
        built = entries
        if (entries.none { it.file.id == chosen }) chosen = entries.firstOrNull()?.file?.id
        rows = entries.map { entry ->
            val (state, rgb) = stateOf(entry)
            Row(entry, fit(Component.literal(entry.file.name), lw - HEAD - 22 - font.width(state)), state, rgb)
        }
        scroll = scroll.coerceIn(0, (rows.size - visibleRows()).coerceAtLeast(0))
        val entry = entries.firstOrNull { it.file.id == chosen } ?: return
        val file = entry.file
        title = fit(Component.literal(file.name), rw - PORTRAIT - 24)
        // A creature without a name of its own is called by its kind: no need to say it twice.
        kind = kindOf(entry).takeIf { it.string != file.name }?.let { fit(it, rw - PORTRAIT - 24) } ?: FormattedCharSequence.EMPTY
        val out = ArrayList<Pair<FormattedCharSequence, Int>>()
        fun line(text: Component, rgb: Int) = font.split(text, rw - 18).forEach { out += it to rgb }
        fun gap() { out += FormattedCharSequence.EMPTY to UiTheme.MUTED }
        if (file.beyonder) line(tr("beyonder"), accent) else if (file.player) line(tr("ordinary"), UiTheme.MUTED)
        gap()
        val body = bodyOf(file.id)
        val self = Minecraft.getInstance().player
        when {
            file.dead -> {
                line(tr("dead"), UiTheme.CRITICAL)
                line(tr("fell", place(file.seenPos, file.seenIn), date(file.seenAt)), UiTheme.MUTED)
            }
            body != null && self != null -> {
                line(tr("near", self.distanceTo(body).roundToInt()), accent)
                line(tr("now_at", place(body.blockPosition(), body.level().dimension().identifier().toString())), UiTheme.MUTED)
            }
            else -> {
                line(tr("away"), UiTheme.MUTED)
                line(tr("last_seen", place(file.seenPos, file.seenIn), date(file.seenAt)), UiTheme.MUTED)
            }
        }
        gap()
        line(tr("remembered", date(file.rememberedAt)), UiTheme.MUTED)
        line(Component.literal(place(file.rememberedPos, file.rememberedIn)), UiTheme.MUTED)
        lines = out
    }

    /** Twice a second: nearness and distance move on their own. */
    fun refresh(tick: Int) {
        if (tick % 10 == 0) rebuild()
    }

    fun click(x: Int, y: Int) {
        if (x !in cx until cx + lw || y < listTop()) return
        val index = (y - listTop()) / ROW
        if (index >= visibleRows()) return
        val row = rows.getOrNull(scroll + index) ?: return
        chosen = row.entry.file.id
        rebuild()
    }

    fun scrollBy(mouseX: Double, step: Int) {
        if (mouseX < rx) scroll = (scroll + step).coerceIn(0, (rows.size - visibleRows()).coerceAtLeast(0))
    }

    fun draw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val top = listTop()
        val shown = rows.drop(scroll).take(visibleRows())
        shown.forEachIndexed { i, row ->
            val y = top + i * ROW
            val on = row.entry.file.id == chosen
            if (on) sprite(graphics, rowOn, cx + 3, y, lw - 10, ROW - 1)
            else if (mouseX in cx + 3 until cx + lw - 7 && mouseY in y until y + ROW - 1) {
                graphics.outline(cx + 3, y, lw - 10, ROW - 1, UiTheme.argb(UiTheme.ACCENT_DEEP, 1f))
            }
            face(graphics, row.entry, cx + 6, y + (ROW - HEAD) / 2, HEAD)
        }
        val entry = rows.firstOrNull { it.entry.file.id == chosen }?.entry
        if (entry != null) {
            sprite(graphics, slot, rx + 6, cy + 6, PORTRAIT + 4, PORTRAIT + 4)
            val body = bodyOf(entry.file.id) as? LivingEntity
            if (body != null && !entry.file.dead) {
                val size = (PORTRAIT * 0.85f / maxOf(body.bbHeight, body.bbWidth, 0.5f)).roundToInt()
                InventoryScreen.extractEntityInInventoryFollowsMouse(
                    graphics, rx + 8, cy + 8, rx + 8 + PORTRAIT, cy + 8 + PORTRAIT, size, 0.0625f,
                    mouseX.toFloat(), mouseY.toFloat(), body,
                )
            } else {
                face(graphics, entry, rx + 8 + (PORTRAIT - FACE) / 2, cy + 8 + (PORTRAIT - FACE) / 2, FACE)
            }
        }
        graphics.nextStratum()

        shown.forEachIndexed { i, row ->
            val y = top + i * ROW
            val on = row.entry.file.id == chosen
            graphics.text(font, row.name, cx + HEAD + 12, y + (ROW - font.lineHeight) / 2, UiTheme.argb(if (on) UiTheme.TEXT else UiTheme.MUTED, 1f))
            graphics.text(font, row.state, cx + lw - 10 - font.width(row.state), y + (ROW - font.lineHeight) / 2, UiTheme.argb(row.stateRgb, 1f))
        }
        if (entry == null) return
        val hx = rx + PORTRAIT + 16
        graphics.text(font, title, hx, cy + 10, UiTheme.argb(accent, 1f))
        graphics.text(font, kind, hx, cy + 22, UiTheme.argb(UiTheme.MUTED, 1f))
        var y = cy + PORTRAIT + 18
        UiTheme.hairline(graphics, rx + 8, y - 6, rw - 16, UiTheme.ACCENT_DEEP, 1f)
        for ((line, rgb) in lines) {
            if (y > cy + ch - 12) break
            graphics.text(font, line, rx + 9, y, UiTheme.argb(rgb, 1f))
            y += LINE
        }
    }

    /** A player's true face; a creature's egg, if it has one. */
    private fun face(graphics: GuiGraphicsExtractor, entry: DossiersPayload.Entry, x: Int, y: Int, size: Int) {
        val face = entry.face
        if (face != null) {
            PlayerFaceExtractor.extractRenderState(graphics, face, x, y, size)
            return
        }
        val type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(entry.file.kind) ?: return).orElse(null) ?: return
        val egg = SpawnEggItem.byId(type).orElse(null) ?: return
        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        graphics.pose().scale(size / 16f, size / 16f)
        graphics.item(ItemStack(egg), 0, 0)
        graphics.pose().popMatrix()
    }

    private fun stateOf(entry: DossiersPayload.Entry): Pair<Component, Int> = when {
        entry.file.dead -> tr("state.dead") to UiTheme.CRITICAL
        bodyOf(entry.file.id) != null -> tr("state.near") to accent
        else -> tr("state.away") to UiTheme.MUTED
    }

    private fun kindOf(entry: DossiersPayload.Entry): Component {
        if (entry.file.player) return tr("player")
        val type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(entry.file.kind) ?: return Component.empty()).orElse(null)
        return type?.description ?: Component.literal(entry.file.kind)
    }

    /** The remembered one as this client has them, if it has them at all. */
    private fun bodyOf(id: UUID): Entity? = Minecraft.getInstance().level?.entitiesForRendering()?.firstOrNull { it.uuid == id }

    private fun place(pos: BlockPos, dimension: String): String {
        val world = when (dimension) {
            "minecraft:overworld" -> tr("world.overworld").string
            "minecraft:the_nether" -> tr("world.nether").string
            "minecraft:the_end" -> tr("world.end").string
            "$MOD_ID:spirit_world" -> tr("world.spirit").string
            else -> dimension.substringAfter(':')
        }
        return "$world, ${pos.x} ${pos.y} ${pos.z}"
    }

    private fun date(millis: Long): String = DATE.format(Instant.ofEpochMilli(millis))

    private fun listTop() = cy + 6
    private fun visibleRows() = ((ch - 12) / ROW).coerceAtLeast(1)

    private fun fit(text: Component, width: Int): FormattedCharSequence {
        if (font.width(text) <= width) return text.visualOrderText
        return Component.literal(font.plainSubstrByWidth(text.string, (width - font.width("…")).coerceAtLeast(0)) + "…").visualOrderText
    }

    private fun tr(key: String, vararg args: Any) = Component.translatable("book.$MOD_ID.cases.$key", *args)

    private companion object {
        const val HEAD = 16
        const val ROW = 22
        const val PORTRAIT = 48
        const val FACE = 32
        const val LINE = 10
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault())
    }
}
