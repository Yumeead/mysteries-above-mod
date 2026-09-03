package me.vangoo.mysteries.client.hud

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.abilities.AbilityType
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.HudAnchor
import me.vangoo.mysteries.client.HudMode
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * A thin panel for the selected ability: icon, name, cost and cooldown.
 *
 * Spirituality is not drawn here — it lives in [SpiritualityHud] at the top centre, because it
 * is a resource watched constantly while the ability panel is only a momentary reference. A
 * side effect of the split matters: the panel no longer wakes up on spirituality regeneration,
 * so the AUTO mode really does fade after 5 seconds of quiet.
 *
 * It is drawn through `HudElementRegistry` — the official Fabric way on 26.2. A mixin into the
 * vanilla HUD is not needed here: the element queues up before the chat and lives by the same
 * rules as the vanilla bars.
 *
 * **The lines are built on the tick, not in the frame.** On 26.x the renderer is retained-mode:
 * [extractRenderState] is called every frame and only queues up what is ready. Composing a
 * [Component] there would mean allocating 60+ times a second; so the panel is rebuilt in
 * [tick], and even there only when its content actually changed. What is left in the frame is
 * arithmetic over numbers that have already been computed.
 *
 * The client decides nothing: every number came from the server, and the opacity and the mode
 * are about how the player sees them.
 */
object AbilityHud : HudElement {

    /** The panel assembled: the texts and the geometry, computed once. */
    private class Panel(
        val entry: AbilityRosterPayload.Entry?,
        val icon: ItemStack?,
        val name: Component,
        val price: Component?,
        val priceCritical: Boolean,
        val marker: Component?,
        val cooldownLabel: Component?,
        val accent: Int,
        val width: Int,
        val height: Int,
        val hairlineY: Int,
    )

    /**
     * Everything the panel's content depends on.
     *
     * Comparing the snapshot with the previous one answers two questions at once: whether to
     * rebuild the texts, and whether to consider that "something happened" — which is exactly
     * what the AUTO mode wakes up on.
     */
    private data class Signature(
        val pathwayId: String,
        val identity: String?,
        val remainingSeconds: Int,
        val toggled: Boolean,
        val sustaining: Boolean,
        /** Not the spirituality number itself: the panel only shows whether it covers the selection. */
        val affordable: Boolean,
        val showCooldownNumbers: Boolean,
    )

    private var signature: Signature? = null
    private var panel: Panel? = null

    /** How many ticks passed with no change at all. The AUTO mode fades once they add up to 5 seconds. */
    private var idleTicks = IDLE_TICKS

    private var fade = 0f
    private var previousFade = 0f

    fun register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, modId("ability_hud"), this)
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    // ── Tick ────────────────────────────────────────────────────────────────────

    private fun tick(client: Minecraft) {
        previousFade = fade

        // The content is watched always, even while the panel is not visible. Otherwise coming
        // back from a menu would look like a state change and wake AUTO for nothing: tapping
        // Escape would have been enough to make the panel flare up for five seconds every time.
        val fresh = signatureOf()
        if (fresh != signature) {
            signature = fresh
            panel = fresh?.let { build(client, it) }
            idleTicks = 0
        } else if (idleTicks < IDLE_TICKS) {
            idleTicks++
        }

        if (panel == null || !shouldShow(client)) {
            // Not a fade but a disappearance: an open screen or F1 takes the panel away at once.
            fade = 0f
            previousFade = 0f
            return
        }

        val awake = ClientOptions.hud == HudMode.ALWAYS || idleTicks < IDLE_TICKS
        fade = approach(fade, if (awake) 1f else 0f, 1f / FADE_TICKS)
    }

    private fun shouldShow(client: Minecraft): Boolean =
        ClientOptions.hud != HudMode.NEVER &&
            client.player != null &&
            client.gui.screen() == null &&
            !client.gui.hud.isHidden() &&
            ClientAbilityState.isBeyonder &&
            ClientAbilityState.entries.isNotEmpty()

    private fun signatureOf(): Signature? {
        val roster = ClientAbilityState.roster ?: return null
        val identity = ClientAbilityState.selected
        val entry = identity?.let(ClientAbilityState::entry)

        return Signature(
            pathwayId = roster.pathwayId,
            identity = identity,
            remainingSeconds = identity?.let(ClientAbilityState::remainingSeconds) ?: 0,
            toggled = identity?.let(ClientAbilityState::isToggled) ?: false,
            sustaining = identity?.let(ClientAbilityState::isSustaining) ?: false,
            affordable = entry?.let(ClientAbilityState::canAfford) ?: true,
            showCooldownNumbers = ClientOptions.showCooldownNumbers,
        )
    }

    private fun build(client: Minecraft, signature: Signature): Panel {
        val font = client.font
        val entry = signature.identity?.let(ClientAbilityState::entry)

        val name = entry?.name ?: Component.translatable("hud.$MOD_ID.no_selection")
        val price = entry?.let(::priceOf)
        val priceCritical = entry != null && entry.type == AbilityType.ACTIVE && !signature.affordable

        val marker = when {
            signature.toggled -> Component.literal("●")
            signature.sustaining -> Component.literal("◐")
            else -> null
        }

        val cooldownLabel = if (signature.showCooldownNumbers && signature.remainingSeconds > 0) {
            Component.literal(signature.remainingSeconds.toString())
        } else {
            null
        }

        // The width fits the longer of the two: the name, or the cost line with the status mark.
        val detailWidth = (price?.let { UiTheme.badgeWidth(font, it) } ?: 0) +
            (marker?.let { MARKER_GAP + font.width(it) } ?: 0)
        val content = (if (entry != null) ICON + GAP else 0) +
            maxOf(font.width(name), detailWidth)

        val abilityHeight = if (entry != null) ROW_HEIGHT else font.lineHeight
        val hairlineY = PADDING + abilityHeight + 2

        return Panel(
            entry = entry,
            icon = entry?.icon,
            name = name,
            price = price,
            priceCritical = priceCritical,
            marker = marker,
            cooldownLabel = cooldownLabel,
            accent = PathwayBranding.textOf(signature.pathwayId).packed,
            width = content + PADDING * 2,
            height = hairlineY + 1 + PADDING,
            hairlineY = hairlineY,
        )
    }

    /**
     * The ability's cost — one-off, upkeep, or both.
     *
     * The upkeep used to be shown only for toggles, while a power that goes out on its own (a
     * session with `upkeep`) is declared **active** — and the result was "✦ 0" where two dozen
     * points were in fact being spent every second. The ability type has nothing to do with it:
     * the question is simply which of the two prices are non-zero.
     */
    private fun priceOf(entry: AbilityRosterPayload.Entry): Component? {
        val parts = buildList {
            if (entry.cost > 0) add("✦ ${entry.cost}")
            if (entry.upkeep > 0) add("✦ ${entry.upkeep}/с")
        }
        if (parts.isNotEmpty()) return Component.literal(parts.joinToString(" · "))
        return if (entry.type == AbilityType.ACTIVE) Component.literal("✦ 0") else null
    }

    private fun approach(current: Float, target: Float, step: Float): Float =
        if (current < target) minOf(current + step, target) else maxOf(current - step, target)

    // ── Frame ───────────────────────────────────────────────────────────────────

    override fun extractRenderState(graphics: GuiGraphicsExtractor, delta: DeltaTracker) {
        val panel = panel ?: return

        val partial = delta.getGameTimeDeltaPartialTick(false)
        val alpha = (previousFade + (fade - previousFade) * partial) * ClientOptions.opacity
        if (alpha <= MIN_VISIBLE_ALPHA) return

        val scale = ClientOptions.hudScale
        graphics.pose().pushMatrix()
        graphics.pose().scale(scale, scale)

        val screenWidth = (graphics.guiWidth() / scale).toInt()
        val screenHeight = (graphics.guiHeight() / scale).toInt()
        val x = when (ClientOptions.anchor) {
            HudAnchor.LEFT_CENTER, HudAnchor.LEFT_TOP -> MARGIN
            HudAnchor.RIGHT_CENTER, HudAnchor.RIGHT_TOP -> screenWidth - MARGIN - panel.width
        }
        val y = when (ClientOptions.anchor) {
            HudAnchor.LEFT_CENTER, HudAnchor.RIGHT_CENTER -> (screenHeight - panel.height) / 2
            HudAnchor.LEFT_TOP, HudAnchor.RIGHT_TOP -> MARGIN
        }

        draw(graphics, panel, x, y, alpha)

        graphics.pose().popMatrix()
    }

    private fun draw(graphics: GuiGraphicsExtractor, panel: Panel, x: Int, y: Int, alpha: Float) {
        val font = Minecraft.getInstance().font
        val left = x + PADDING

        UiTheme.panel(graphics, x, y, panel.width, panel.height, alpha)
        panel.icon?.let { graphics.item(it, left, y + PADDING) }

        // Next comes everything that must lie on top of the icon. In retained-mode, items go in
        // a separate pass, so without a new stratum the cooldown veil would end up beneath them.
        graphics.nextStratum()

        val textLeft = if (panel.icon != null) left + ICON + GAP else left

        if (panel.entry != null && panel.icon != null) {
            val remaining = 1f - ClientAbilityState.cooldownProgress(panel.entry)
            UiTheme.cooldownVeil(graphics, left, y + PADDING, ICON, remaining, alpha)
            panel.cooldownLabel?.let {
                graphics.centeredText(font, it, left + ICON / 2, y + PADDING + 4, UiTheme.argb(UiTheme.TEXT, alpha))
            }
        }

        graphics.text(font, panel.name, textLeft, y + PADDING, UiTheme.argb(UiTheme.TEXT, alpha))

        var detailX = textLeft
        panel.price?.let {
            val color = if (panel.priceCritical) UiTheme.CRITICAL else UiTheme.MUTED
            UiTheme.badge(graphics, font, it, detailX, y + PADDING + DETAIL_OFFSET, color, alpha)
            detailX += UiTheme.badgeWidth(font, it) + MARKER_GAP
        }
        panel.marker?.let {
            graphics.text(font, it, detailX, y + PADDING + DETAIL_OFFSET + 2, UiTheme.argb(UiTheme.ACCENT, alpha))
        }

        UiTheme.hairline(graphics, left, y + panel.hairlineY, panel.width - PADDING * 2, panel.accent, alpha)
    }

    private const val PADDING = 4
    private const val MARGIN = 6
    private const val ICON = 16
    private const val GAP = 4
    private const val MARKER_GAP = 3

    /** The icon (16) plus the details line under it. */
    private const val ROW_HEIGHT = 20
    private const val DETAIL_OFFSET = 9

    /** 250 ms to appear and to fade; 5 s of quiet and AUTO hides the panel. */
    private const val FADE_TICKS = 5f
    private const val IDLE_TICKS = 100

    private const val MIN_VISIBLE_ALPHA = 0.01f
}
