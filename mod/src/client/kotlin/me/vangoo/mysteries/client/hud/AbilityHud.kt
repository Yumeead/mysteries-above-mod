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
 * Тонка панель обраної здібності: іконка, назва, ціна й перепочинок.
 *
 * Духовність тут не малюється — вона живе в [SpiritualityHud] угорі по центру, бо це
 * ресурс, за яким стежать постійно, а панель здібності лише довідка на мить. Побічний
 * наслідок розділення важливий: панель більше не прокидається на регенерацію духовності,
 * тож режим АВТО справді згасає за 5 секунд спокою.
 *
 * Малюється через `HudElementRegistry` — офіційний шлях Fabric на 26.2. Міксин у
 * ванільний HUD тут не потрібен: елемент стає в чергу перед чатом і живе за тими
 * самими правилами, що й ванільні бари.
 *
 * **Рядки будуються в такті, а не в кадрі.** На 26.x рендер retained-mode:
 * [extractRenderState] кличеться щокадру й лише складає готове в чергу. Формувати там
 * [Component] означало б алокацію 60+ разів на секунду; тому панель перебудовується
 * в [tick], і навіть там — лише коли її вміст справді змінився. У кадрі лишається
 * арифметика над числами, які й так уже пораховані.
 *
 * Клієнт нічого не вирішує: усі числа прийшли з сервера, а прозорість і режим — це
 * про те, як гравець їх бачить.
 */
object AbilityHud : HudElement {

    /** Панель у зібраному вигляді: тексти й геометрія, пораховані один раз. */
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
     * Усе, від чого залежить вміст панелі.
     *
     * Порівняння знімка з попереднім відповідає одразу на два питання: чи перебудовувати
     * тексти й чи вважати, що «щось сталось» — а саме на це прокидається режим АВТО.
     */
    private data class Signature(
        val pathwayId: String,
        val identity: String?,
        val remainingSeconds: Int,
        val toggled: Boolean,
        val sustaining: Boolean,
        /** Не саме число духовності: панель показує лише, чи вистачає на обране. */
        val affordable: Boolean,
        val showCooldownNumbers: Boolean,
    )

    private var signature: Signature? = null
    private var panel: Panel? = null

    /** Скільки тіків минуло без жодної зміни. Режим АВТО згасає, коли їх набралось 5 секунд. */
    private var idleTicks = IDLE_TICKS

    private var fade = 0f
    private var previousFade = 0f

    fun register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, modId("ability_hud"), this)
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    // ── Такт ────────────────────────────────────────────────────────────────────

    private fun tick(client: Minecraft) {
        previousFade = fade

        // Вміст стежиться завжди, навіть поки панель не видно. Інакше повернення з
        // меню виглядало б як зміна стану й будило б АВТО ні за що: досить було
        // потикати Escape, щоб панель щоразу спалахувала на п'ять секунд.
        val fresh = signatureOf()
        if (fresh != signature) {
            signature = fresh
            panel = fresh?.let { build(client, it) }
            idleTicks = 0
        } else if (idleTicks < IDLE_TICKS) {
            idleTicks++
        }

        if (panel == null || !shouldShow(client)) {
            // Не згасання, а зникнення: відкритий екран чи F1 забирають панель одразу.
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

        // Ширина під довше з двох: назва або рядок ціни зі знаком стану.
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
     * Ціна здібності — разова, утримання, або обидві.
     *
     * Утримання показувалось лише для тоглів, а сила, що гасне сама (сесія з `upkeep`),
     * оголошена **активною** — і виходило «✦ 0» там, де насправді щосекунди списується
     * два десятки. Тип здібності тут узагалі ні до чого: питання просто в тому, які з
     * двох цін ненульові.
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

    // ── Кадр ────────────────────────────────────────────────────────────────────

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

        // Далі — усе, що мусить лягти поверх іконки. Предмети в retained-mode їдуть
        // окремим проходом, тож тінь перепочинку без нового страту опинилась би під ними.
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

    /** Іконка (16) плюс рядок деталей під нею. */
    private const val ROW_HEIGHT = 20
    private const val DETAIL_OFFSET = 9

    /** 250 мс проявлення й згасання; 5 с спокою — і АВТО ховає панель. */
    private const val FADE_TICKS = 5f
    private const val IDLE_TICKS = 100

    private const val MIN_VISIBLE_ALPHA = 0.01f
}
