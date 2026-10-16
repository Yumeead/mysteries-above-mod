package me.vangoo.mysteries.client.hud

import me.vangoo.domain.abilities.AbilityType
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.HudMode
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import kotlin.math.abs

/**
 * Spirituality: a vanilla-width bar right above the experience bar, the number beside it.
 *
 * It sits with health and hunger because it is read the same way — constantly, out of the corner
 * of the eye, while fighting. To make room, the vanilla rows above the experience bar (hearts,
 * armour, food, air, mount health, the level number, the held item's name, the action bar) are
 * lifted by [LIFT] while the bar is shown; with no experience bar (creative) the bar takes its
 * slot instead and nothing moves.
 *
 * The look is pixel and flat: a bronze frame, a three-step gold fill, a notch every tenth, a
 * sigil at each end. The one motion is a faint light running along the fill every four seconds
 * ("spirit flows"); `reducedMotion` stops it. The fill turns red when spirituality is critical.
 * The selected ability's price is shown in advance as a darker stretch at the end of the fill.
 *
 * It never fades and does not hide behind an open screen (the wheel draws it again on top, where
 * the cast cost is decided). It disappears on F1, in spectator mode, and when the player is not a
 * Beyonder. The fill catches up with a new number in ~200 ms; the number itself jumps at once.
 */
object SpiritualityHud : HudElement {

    private var shown: Pair<Int, Int>? = null
    private var caption: Component? = null
    private var critical = false

    /** The bar's drawn fraction on this tick and the previous one, interpolated by `partialTick`. */
    private var level = -1f
    private var previousLevel = -1f
    private var ticks = 0

    fun register() {
        // Before the level number, so the number lies on the bar the way vanilla's lies on its own.
        HudElementRegistry.attachElementBefore(VanillaHudElements.EXPERIENCE_LEVEL, modId("spirituality_hud"), this)
        for (id in LIFTED) {
            HudElementRegistry.replaceElement(id) { original ->
                HudElement { graphics, delta ->
                    val lift = lift()
                    if (lift == 0) {
                        original.extractRenderState(graphics, delta)
                    } else {
                        graphics.pose().pushMatrix()
                        graphics.pose().translate(0f, -lift.toFloat())
                        original.extractRenderState(graphics, delta)
                        graphics.pose().popMatrix()
                    }
                }
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    private fun tick(client: Minecraft) {
        ticks++
        val spirituality = ClientAbilityState.spirituality
        if (!shouldShow(client) || spirituality == null || spirituality.maximum <= 0) {
            shown = null
            caption = null
            level = -1f
            return
        }

        val target = spirituality.current.toFloat() / spirituality.maximum
        previousLevel = level
        level = when {
            // A first sight of the bar, or motion turned off: no catching up from nowhere.
            level < 0f || ClientOptions.reducedMotion -> target
            abs(target - level) < SNAP -> target
            else -> level + (target - level) * CATCH_UP
        }
        if (previousLevel < 0f) previousLevel = level

        val fresh = spirituality.current to spirituality.maximum
        if (fresh == shown) return
        shown = fresh
        critical = spirituality.isCritical()
        caption = Component.literal("${spirituality.current}")
            .append(Component.literal(" / ${spirituality.maximum}").withColor(UiTheme.MUTED))
    }

    private fun shouldShow(client: Minecraft): Boolean =
        ClientOptions.hud != HudMode.NEVER &&
            ClientOptions.showSpirituality &&
            client.player?.isSpectator == false &&
            !client.gui.hud.isHidden() &&
            ClientAbilityState.isBeyonder

    /** How far the vanilla rows move up: only while the bar stands above an experience bar. */
    private fun lift(): Int = if (caption != null && hasExperienceBar()) LIFT else 0

    private fun hasExperienceBar(): Boolean = Minecraft.getInstance().gameMode?.hasExperience() == true

    override fun extractRenderState(graphics: GuiGraphicsExtractor, delta: DeltaTracker) {
        val caption = caption ?: return
        val spirituality = ClientAbilityState.spirituality ?: return
        val font = Minecraft.getInstance().font
        val alpha = ClientOptions.opacity.coerceAtLeast(MIN_ALPHA)
        val partial = delta.getGameTimeDeltaPartialTick(false)

        val x = graphics.guiWidth() / 2 - WIDTH / 2
        val y = graphics.guiHeight() - EXPERIENCE_BOTTOM - if (hasExperienceBar()) LIFT else 0

        // Frame and track.
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, UiTheme.argb(FRAME, alpha))
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, UiTheme.argb(UiTheme.PANEL, alpha))

        val inner = WIDTH - 2
        val drawn = previousLevel + (level - previousLevel) * partial
        val filled = (inner * drawn.coerceIn(0f, 1f)).toInt()
        val tones = if (critical) CRITICAL_TONES else GOLD_TONES
        fillRows(graphics, x + 1, y + 1, filled, tones, alpha)

        // The stretch the selected ability will cost: what will be left is visible in advance.
        costOfSelected()?.let { cost ->
            val after = (inner * ((spirituality.current - cost).toFloat() / spirituality.maximum).coerceIn(0f, 1f)).toInt()
            if (after < filled) fillRows(graphics, x + 1 + after, y + 1, filled - after, SPENT_TONES, alpha)
        }

        // Spirit flows: a faint light runs along the fill.
        if (!ClientOptions.reducedMotion && filled > SHIMMER_WIDTH) {
            val phase = ((ticks + partial) % SHIMMER_PERIOD) / SHIMMER_PERIOD
            val head = (phase * (filled + SHIMMER_WIDTH)).toInt() - SHIMMER_WIDTH
            val from = (x + 1 + head).coerceAtLeast(x + 1)
            val to = (x + 1 + head + SHIMMER_WIDTH).coerceAtMost(x + 1 + filled)
            if (to > from) graphics.fill(from, y + 1, to, y + HEIGHT - 1, UiTheme.argb(0xFFFFFF, alpha * SHIMMER_ALPHA))
        }

        // A notch every tenth, so a share reads at a glance.
        val notch = UiTheme.argb(0x000000, alpha * NOTCH_ALPHA)
        for (i in 1 until NOTCHES) {
            val nx = x + 1 + inner * i / NOTCHES
            graphics.fill(nx, y + 1, nx + 1, y + HEIGHT - 1, notch)
        }

        sigil(graphics, x - SIGIL - SIGIL_GAP, y, alpha)
        sigil(graphics, x + WIDTH + SIGIL_GAP, y, alpha)

        val textX = x + WIDTH + SIGIL_GAP + SIGIL + TEXT_GAP
        graphics.text(font, caption, textX, y - 1, UiTheme.argb(if (critical) UiTheme.CRITICAL else UiTheme.TEXT, alpha))
    }

    /** The three-row stepped fill: light top edge, body, shadowed bottom edge. */
    private fun fillRows(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, tones: IntArray, alpha: Float) {
        if (width <= 0) return
        tones.forEachIndexed { row, rgb -> graphics.fill(x, y + row, x + width, y + row + 1, UiTheme.argb(rgb, alpha)) }
    }

    /** A five-pixel diamond eye, the height of the bar: gold rim, dark body, bright pupil. */
    private fun sigil(graphics: GuiGraphicsExtractor, x: Int, y: Int, alpha: Float) {
        val rim = UiTheme.argb(UiTheme.ACCENT, alpha)
        graphics.fill(x + 1, y + 1, x + 4, y + 4, UiTheme.argb(UiTheme.PANEL, alpha))
        graphics.fill(x + 2, y, x + 3, y + 1, rim)
        graphics.fill(x + 1, y + 1, x + 2, y + 2, rim)
        graphics.fill(x + 3, y + 1, x + 4, y + 2, rim)
        graphics.fill(x, y + 2, x + 1, y + 3, rim)
        graphics.fill(x + 4, y + 2, x + 5, y + 3, rim)
        graphics.fill(x + 1, y + 3, x + 2, y + 4, rim)
        graphics.fill(x + 3, y + 3, x + 4, y + 4, rim)
        graphics.fill(x + 2, y + 4, x + 3, y + 5, rim)
        graphics.fill(x + 2, y + 2, x + 3, y + 3, UiTheme.argb(PUPIL, alpha))
    }

    /** The price of what is selected now — or `null` if there is nothing to pay for or nothing to pay with. */
    private fun costOfSelected(): Int? {
        val entry = ClientAbilityState.selected?.let(ClientAbilityState::entry) ?: return null
        if (entry.type != AbilityType.ACTIVE || entry.cost <= 0) return null
        return entry.cost.takeIf { ClientAbilityState.canAfford(entry) }
    }

    /** The vanilla rows that stand above the experience bar. */
    private val LIFTED = listOf(
        VanillaHudElements.HEALTH_BAR,
        VanillaHudElements.ARMOR_BAR,
        VanillaHudElements.FOOD_BAR,
        VanillaHudElements.AIR_BAR,
        VanillaHudElements.MOUNT_HEALTH,
        VanillaHudElements.EXPERIENCE_LEVEL,
        VanillaHudElements.HELD_ITEM_TOOLTIP,
        VanillaHudElements.OVERLAY_MESSAGE,
    )

    /** The vanilla experience bar: 182 wide, its top 29 above the bottom edge. */
    private const val WIDTH = 182
    private const val HEIGHT = 5
    private const val EXPERIENCE_BOTTOM = 29

    /** The bar's height and a one-pixel gap. */
    private const val LIFT = HEIGHT + 1

    private const val SIGIL = 5
    private const val SIGIL_GAP = 2
    private const val TEXT_GAP = 3

    private const val FRAME = 0x3B2F16
    private const val PUPIL = 0xFFF2C4
    private val GOLD_TONES = intArrayOf(0xFFE7A0, 0xD9B256, 0x8F6F26)
    private val CRITICAL_TONES = intArrayOf(0xE59A86, 0xC46A55, 0x7E3A2E)
    private val SPENT_TONES = intArrayOf(0x8C7232, 0x6E5824, 0x4A3B18)

    private const val NOTCHES = 10
    private const val NOTCH_ALPHA = 0.35f
    private const val SHIMMER_PERIOD = 80f
    private const val SHIMMER_WIDTH = 6
    private const val SHIMMER_ALPHA = 0.28f

    /** The bar is read constantly: it never goes fainter than this, whatever the panel opacity. */
    private const val MIN_ALPHA = 0.9f

    /** Half the gap per tick: ~94 % of the way in four ticks, i.e. 200 ms. */
    private const val CATCH_UP = 0.5f
    private const val SNAP = 0.002f
}
