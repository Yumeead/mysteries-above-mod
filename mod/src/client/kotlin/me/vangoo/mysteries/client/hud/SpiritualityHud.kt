package me.vangoo.mysteries.client.hud

import me.vangoo.domain.abilities.AbilityType
import me.vangoo.domain.hud.HudPart
import me.vangoo.domain.hud.BottomPanel
import me.vangoo.domain.hud.SpiritualityDisplay
import me.vangoo.domain.hud.SpiritualityMode
import me.vangoo.domain.hud.SpiritualityNumbers
import me.vangoo.domain.hud.TagMotion
import me.vangoo.domain.services.SpiritualityCalculator
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.HudMode
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.render.FlaskStyles
import me.vangoo.mysteries.client.render.SpiritualityFlask
import me.vangoo.mysteries.client.render.SpiritualityOrb
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.client.screen.HudEditorScreen
import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import kotlin.math.sin

/** The flask and the optional legacy track share confirmed state, numbers and placement. */
object SpiritualityHud : HudElement {
    private val display = SpiritualityDisplay()
    private val tag = TagMotion()
    private val calculator = SpiritualityCalculator()
    private var owner: java.util.UUID? = null
    private var shown: Pair<Int, Int>? = null
    private var caption: Component? = null
    private var currentText = "800"
    private var maximumText = "/1600"
    private var movement = 0f
    private val example = Component.literal("800 / 1600")
    private var ticks = 0

    fun part(preview: Boolean = false): HudPart? = when (ClientOptions.hudSettings.spirituality) {
        SpiritualityMode.FLASK -> HudPart.FLASK
        SpiritualityMode.BAR -> HudPart.BAR
        SpiritualityMode.OFF -> if (preview && !BottomHud.enabled) HudPart.FLASK else null
    }

    fun register() {
        FlaskStyles.register()
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, modId("spirituality_hud"), this)
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (owner != client.player?.uuid) {
                owner = client.player?.uuid
                display.reset(); tag.reset(); shown = null; caption = null
            }
            val speed = client.player?.deltaMovement?.horizontalDistance()?.toFloat() ?: 0f
            if (!client.isPaused) movement += ((speed * 7f).coerceIn(0f, 1f) - movement) * .15f
            val state = ClientAbilityState.spirituality
            if (client.player == null || !ClientAbilityState.isBeyonder || state == null || state.maximum <= 0) {
                shown = null
                caption = null
                display.reset()
                tag.reset()
            } else {
                if (!client.isPaused) {
                    ticks++
                    val sequence = ClientAbilityState.roster!!.sequence
                    display.tick(state.current, state.maximum, ClientOptions.reducedMotion, sequence.level,
                        calculator.calculateRegenerationRate(sequence, ClientAbilityState.mastery))
                    val style = FlaskStyles.of(ClientAbilityState.roster?.pathwayId)
                    tag.tick(client.cameraEntity?.yRot ?: client.player!!.yRot,
                        sin(ticks / 19f) * movement * (style?.tagSwing ?: 0f), ClientOptions.reducedMotion)
                }
                updateCaption(display.number(0f), state.maximum)
            }
        }
    }

    private fun updateCaption(current: Int, maximum: Int) {
        val fresh = current to maximum
        if (fresh == shown) return
        shown = fresh
        caption = Component.literal("$current / $maximum")
        currentText = current.toString()
        maximumText = "/$maximum"
    }

    fun dimensions(preview: Boolean = false): Pair<Int, Int>? {
        return when (part(preview)) {
            HudPart.FLASK -> {
                if (BottomHud.enabled) return 48 to 44
                val style = FlaskStyles.of(ClientAbilityState.roster?.pathwayId) ?: return null
                style.width to style.height
            }
            HudPart.BAR -> 182 to 20
            else -> null
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, delta: DeltaTracker) {
        val client = Minecraft.getInstance()
        if (client.gui.screen() is HudEditorScreen || client.player?.isSpectator != false ||
            client.gui.hud.isHidden() || ClientOptions.hud == HudMode.NEVER || caption == null) return
        val part = part() ?: return
        val box = HudPlacement.boxes(graphics.guiWidth(), graphics.guiHeight())[part] ?: return
        draw(graphics, box, delta.getGameTimeDeltaPartialTick(false))
    }

    fun draw(graphics: GuiGraphicsExtractor, box: HudBox, partial: Float, preview: Boolean = false) {
        val mode = if (preview && ClientOptions.hudSettings.spirituality == SpiritualityMode.OFF)
            SpiritualityMode.FLASK else ClientOptions.hudSettings.spirituality
        if (mode == SpiritualityMode.OFF) return
        val size = dimensions(preview) ?: return
        val exampleState = preview && (caption == null || ClientOptions.hudSettings.spirituality == SpiritualityMode.OFF)
        if (!exampleState) ClientAbilityState.spirituality?.let { updateCaption(display.number(partial), it.maximum) }
        val level = if (exampleState) .5f else display.fraction(partial)
        val trail = if (exampleState) level else display.trailFraction(partial)
        val time = ticks + partial
        val alpha = ClientOptions.opacity.coerceAtLeast(.85f)
        graphics.pose().pushMatrix()
        graphics.pose().translate(box.rect.x.toFloat(), box.rect.y.toFloat())
        graphics.pose().scale(box.scale, box.scale)
        if (mode == SpiritualityMode.FLASK) {
            val style = FlaskStyles.of(ClientAbilityState.roster?.pathwayId) ?: run { graphics.pose().popMatrix(); return }
            if (BottomHud.enabled) SpiritualityOrb.draw(graphics, style, ClientAbilityState.roster?.pathwayId,
                6, 11, level, trail, time, !exampleState && display.low, !exampleState && display.gainTicks > 0,
                ClientOptions.reducedMotion, alpha, movement, tag.angle(partial))
            else SpiritualityFlask.draw(graphics, style, (size.first - style.width) / 2, 0, level, trail, time,
                    !exampleState && display.low, !exampleState && display.gainTicks > 0,
                    ClientOptions.reducedMotion, alpha, movement, ClientOptions.hudSettings.flaskBacking)
        } else {
            drawBar(graphics, level, trail, time, alpha)
        }
        val screen = Minecraft.getInstance().gui.screen()
        val show = display.low || if (preview) ClientOptions.hudSettings.numbers != SpiritualityNumbers.NEVER
            else display.showNumber(ClientOptions.hudSettings.numbers, screen is AbstractContainerScreen<*> || screen is ChatScreen)
        if (show) {
            val font = Minecraft.getInstance().font
            graphics.nextStratum()
            if (mode == SpiritualityMode.FLASK) {
                if (BottomHud.enabled) {
                    val label = if (exampleState) "800/1600" else currentText + maximumText
                    val x = BottomPanel.orbLabelX(font.width(label),box.rect.x,graphics.guiWidth())
                    BottomHud.text(graphics,label,x,if(box.rect.y==0) 1 else 0)
                }
                else drawFlaskNumbers(graphics, if (exampleState) "800" else currentText,
                        if (exampleState) "/1600" else maximumText)
            } else {
                val text = caption ?: example
                graphics.text(font, text, (size.first - font.width(text)) / 2, 0, UiTheme.argb(UiTheme.TEXT, 1f), true)
            }
        }
        graphics.pose().popMatrix()
    }

    internal fun drawFlaskNumbers(graphics: GuiGraphicsExtractor, current: String, maximum: String) {
        val font = Minecraft.getInstance().font
        fun etched(text: String, y: Float, limit: Float) {
            val scale = minOf(limit, 27f / font.width(text).coerceAtLeast(1))
            graphics.pose().pushMatrix()
            graphics.pose().translate(31f - font.width(text) * scale / 2, y)
            graphics.pose().scale(scale, scale)
            for (dy in -1..1) for (dx in -1..1) if (dx != 0 || dy != 0)
                graphics.text(font, text, dx, dy, UiTheme.argb(0x263340, 1f), false)
            graphics.nextStratum()
            graphics.text(font, text, 0, 0, UiTheme.argb(0xF1F7FF, 1f), false)
            graphics.pose().popMatrix()
        }
        etched(current, 33f, .8f)
        etched(maximum, 41f, .65f)
    }

    private fun drawBar(graphics: GuiGraphicsExtractor, level: Float, trail: Float, time: Float, opacity: Float) {
        val alpha = opacity * if (display.low) .7f + (if (ClientOptions.reducedMotion) 0f else sin(time / 13f) * .025f) else 1f
        graphics.fill(0, 11, 182, 19, UiTheme.argb(0x58432D, alpha))
        graphics.fill(1, 12, 181, 18, UiTheme.argb(0xB99A61, alpha * .12f))
        val filled = (180 * level).toInt()
        val spent = (180 * trail).toInt()
        if (spent > filled) graphics.fill(1 + filled, 12, 1 + spent, 18, UiTheme.argb(0xE1C993, alpha * .28f))
        if (filled > 0) {
            graphics.fill(1, 12, 1 + filled, 18, UiTheme.argb(0xB99A61, alpha))
            graphics.fill(1, 12, 1 + filled, 13, UiTheme.argb(0xDDC58B, alpha))
            graphics.fill(1, 17, 1 + filled, 18, UiTheme.argb(0x78603E, alpha))
            val state = ClientAbilityState.spirituality
            val selected = ClientAbilityState.selected?.let(ClientAbilityState::entry)
            if (state != null && state.maximum > 0 && selected?.type == AbilityType.ACTIVE &&
                selected.cost > 0 && ClientAbilityState.canAfford(selected)) {
                val after = (180f * (state.current - selected.cost) / state.maximum).toInt().coerceIn(0, filled)
                if (after < filled) graphics.fill(1 + after, 13, 1 + filled, 17, UiTheme.argb(UiTheme.ACCENT_DEEP, alpha * .55f))
            }
        }
    }
}
