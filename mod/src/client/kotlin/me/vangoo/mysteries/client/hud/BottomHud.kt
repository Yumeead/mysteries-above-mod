package me.vangoo.mysteries.client.hud

import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.hud.*
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.HudMode
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.client.screen.HudEditorScreen
import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.mixin.client.rendering.HudAccessor
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.player.Player
import kotlin.math.ceil
import kotlin.math.sin

/** The approved skin surrounds vanilla extraction, so its items, effects and blink logic remain native. */
object BottomHud : HudElement {
    val enabled: Boolean get() = ClientOptions.hudSettings.interfaceStyle == InterfaceStyle.MOD
    private var scope: Identifier? = null
    private val blank = modId("bottom_panel/blank")
    private val skin = listOf("hotbar", "offhand_left", "offhand_right", "heart_empty", "heart_full",
        "heart_half_height", "heart_half_left", "heart_empty_blinking", "heart_full_blinking",
        "heart_half_height_blinking", "heart_half_left_blinking", "food_empty", "food_full", "food_half",
        "experience_background", "experience_progress").associateWith { modId("bottom_panel/$it") }
    private val healthDisplay = ResourceNumberDisplay()
    private val foodDisplay = ResourceNumberDisplay()
    private var healthText = ""
    private var foodText = ""
    private var tick = 0
    private var owner: java.util.UUID? = null

    private val symbols = mapOf(
        "sanity" to listOf("...##..##...", "..########..", ".###.##.###.", ".##..##..##.",
            "###.####.###", "##..#..#..##", "###.####.###", ".##..##..##.", ".###.##.###.",
            "..########..", "....####....", ".....##....."),
        "potion" to listOf("....####....", "....#..#....", "....#..#....", "...##..##...",
            "..##....##..", "..#......#..", ".##......##.", ".#..####..#.", ".#..####..#.",
            ".#..####..#.", "..#......#..", "...######..."),
        "rune" to listOf("...#...", "..#.#..", ".#...#.", "#..#..#", ".#...#.", "..#.#..", "...#..."))

    fun register() {
        for (id in listOf(VanillaHudElements.HOTBAR, VanillaHudElements.HEALTH_BAR,
            VanillaHudElements.FOOD_BAR, VanillaHudElements.INFO_BAR)) {
            HudElementRegistry.replaceElement(id) { original -> HudElement { graphics, delta ->
                if (!enabled) original.extractRenderState(graphics, delta)
                else {
                    val player = Minecraft.getInstance().cameraEntity as? Player
                    val bar = player != null && Minecraft.getInstance().gameMode?.canHurtPlayer() == true &&
                        (id == VanillaHudElements.HEALTH_BAR && healthBar(player) ||
                            id == VanillaHudElements.FOOD_BAR && ClientOptions.hudSettings.hunger == HungerStyle.BAR)
                    if (bar) drawResourceBar(graphics, checkNotNull(player), id == VanillaHudElements.HEALTH_BAR)
                    else {
                        val previous = scope
                        scope = id
                        try { original.extractRenderState(graphics, delta) } finally { scope = previous }
                    }
                    if (id == VanillaHudElements.HOTBAR) drawSelection(graphics)
                }
            } }
        }
        HudStatusBarHeightRegistry.addLeft(VanillaHudElements.HEALTH_BAR) { player ->
            if (enabled && healthBar(player)) 10 else {
                val displayed = (Minecraft.getInstance().gui.hud as HudAccessor).`fabric$getRenderHealthValue`()
                val maximum = maxOf(player.maxHealth, ceil(player.health), displayed.toFloat())
                val rows = ceil((maximum + ceil(player.absorptionAmount)) / 20f).toInt().coerceAtLeast(1)
                10 + (rows - 1) * maxOf(10 - (rows - 2), 3)
            }
        }
        for (id in listOf(VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.OVERLAY_MESSAGE)) {
            HudElementRegistry.replaceElement(id) { original -> HudElement { graphics, delta ->
                val rise = if (enabled && ClientAbilityState.isBeyonder &&
                    ClientOptions.hudSettings.spirituality != SpiritualityMode.OFF) labelRise(graphics) else 0
                graphics.pose().pushMatrix()
                graphics.pose().translate(0f, -rise.toFloat())
                try { original.extractRenderState(graphics, delta) } finally { graphics.pose().popMatrix() }
            } }
        }
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, modId("bottom_panel"), this)
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val player = client.player
            if (owner != player?.uuid) {
                owner = player?.uuid
                healthDisplay.reset(); foodDisplay.reset()
            }
            if (player == null || client.isPaused) return@register
            tick++
            healthDisplay.tick(ceil(player.health).toInt(), ceil(player.maxHealth).toInt())
            foodDisplay.tick(player.foodData.foodLevel, 20)
            healthText = "${ceil(player.health).toInt()}/${ceil(player.maxHealth).toInt()}"
            foodText = "${player.foodData.foodLevel}/20"
        }
    }

    @JvmStatic fun sprite(id: Identifier): Identifier {
        if (scope == null || id.namespace != "minecraft") return id
        val name = when (id.path) {
            "hud/hotbar" -> "hotbar"
            "hud/hotbar_selection" -> return blank
            "hud/hotbar_offhand_left" -> "offhand_left"
            "hud/hotbar_offhand_right" -> "offhand_right"
            "hud/heart/container" -> "heart_empty"
            "hud/heart/container_blinking" -> "heart_empty_blinking"
            "hud/heart/full" -> "heart_full"
            "hud/heart/full_blinking" -> "heart_full_blinking"
            "hud/heart/half", "hud/heart/half_blinking" -> "heart_half_" +
                ClientOptions.hudSettings.halfHeart.name.lowercase() + if (id.path.endsWith("blinking")) "_blinking" else ""
            "hud/food_empty" -> "food_empty"
            "hud/food_full" -> "food_full"
            "hud/food_half" -> "food_half"
            "hud/experience_bar_background" -> "experience_background"
            "hud/experience_bar_progress" -> "experience_progress"
            else -> return id
        }
        return skin.getValue(name)
    }

    fun healthBar(player: Player): Boolean = BottomPanel.healthBar(ClientOptions.hudSettings, player.maxHealth)

    @JvmStatic fun chatRise(height: Int): Int {
        val client = Minecraft.getInstance()
        val player = client.player ?: return 0
        if (!enabled || client.gui.hud.isHidden() || player.isSpectator) return 0
        val width = client.window.guiScaledWidth
        val c = width / 2
        val occupied = mutableListOf<HudRect>()
        if (client.gameMode?.canHurtPlayer() == true) {
            val armor = HudStatusBarHeightRegistry.getHeight(VanillaHudElements.ARMOR_BAR)
            val air = HudStatusBarHeightRegistry.getHeight(VanillaHudElements.AIR_BAR)
            occupied += HudRect(c-91,height-armor+(if (player.armorValue>0) 0 else 10),81,9)
            occupied += HudRect(c+10,height-air+(if (player.isUnderWater || player.airSupply<player.maxAirSupply) 0 else 10),81,9)
        }
        if (ClientOptions.hud != HudMode.NEVER && ClientAbilityState.isBeyonder) {
            val boxes = HudPlacement.boxes(width,height)
            if ((ClientAbilityState.spirituality?.maximum ?: 0)>0) SpiritualityHud.part()?.let { part ->
                boxes[part]?.let { occupied += it.rect }
            }
            boxes[HudPart.MARIONETTES]?.let { occupied += it.rect }
            boxes[HudPart.AUXILIARY]?.let { occupied += it.rect }
            boxes[HudPart.ABILITY]?.let { slot ->
                val name = ClientAbilityState.selected?.let(ClientAbilityState::entry)?.name
                    ?: Component.translatable("hud.mysteries_above.no_selection")
                val textHeight = ceil(client.font.split(name,143).size * 5.4f).toInt()
                occupied += BottomPanel.abilityLabel(width,height,slot.rect,textHeight)
            }
        }
        val chatRight = net.minecraft.client.gui.components.ChatComponent.getWidth(client.options.chatWidth().get())+8
        return BottomPanel.chatRise(height,chatRight,occupied)
    }

    private fun drawSelection(g: GuiGraphicsExtractor) {
        val player = Minecraft.getInstance().player ?: return
        if (player.isSpectator) return
        corners(g, g.guiWidth() / 2 - 91 + player.inventory.selectedSlot * 20, g.guiHeight() - 22, 22, 22, 0xDFC28B)
    }

    private fun drawResourceBar(g: GuiGraphicsExtractor, player: Player, health: Boolean) {
        if (!health && (Minecraft.getInstance().gui.hud as HudAccessor).`fabric$callGetRiddenEntity`() != null) return
        val x = g.guiWidth() / 2 + if (health) -91 else 9
        val y = g.guiHeight() - 40
        val maximum = if (health) player.maxHealth else 20f
        val value = if (health) player.health else player.foodData.foodLevel.toFloat()
        val color = if (!health) 0xBD783D else when {
            player.hasEffect(MobEffects.POISON) -> 0x6E9F36
            player.hasEffect(MobEffects.WITHER) -> 0x655C70
            player.ticksFrozen > 0 -> 0x78C5E3
            else -> 0xB51C38
        }
        frame(g, x, y, 82, 9)
        val filled = (78 * value / maximum.coerceAtLeast(1f)).toInt().coerceIn(0, 78)
        if (filled > 0) {
            g.fill(x + 2, y + 2, x + 2 + filled, y + 7, opaque(color))
            g.fill(x + 2, y + 2, x + 2 + filled, y + 3, opaque(if (health) 0xE33D51 else 0xE1A261))
            g.fill(x + 2, y + 6, x + 2 + filled, y + 7, opaque(if (health) 0x741027 else 0x754122))
            if (health && player.absorptionAmount > 0) g.fill(x + 2, y + 6,
                x + 2 + (78 * player.absorptionAmount / (maximum + player.absorptionAmount)).toInt(), y + 7, opaque(0xDFC28B))
        }
        val segments = ceil(maximum / 2).toInt().coerceIn(1, 78)
        for (n in 1 until segments) {
            val xx = x + 2 + 78 * n / segments
            g.fill(xx, y + 2, xx + 1, y + 7, opaque(0x171A1C))
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, delta: DeltaTracker) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        if (!enabled || player.isSpectator || client.gui.hud.isHidden() || client.gui.screen() is HudEditorScreen) return
        val c = graphics.guiWidth() / 2
        val h = graphics.guiHeight()
        if (client.gameMode?.canHurtPlayer() == true) {
            val healthY = h - HudStatusBarHeightRegistry.getHeight(VanillaHudElements.ARMOR_BAR) +
                if (player.armorValue > 0) -9 else 1
            if (healthDisplay.visible(ClientOptions.hudSettings.healthNumbers, player.health <= 4))
                text(graphics, healthText, c - 27 - client.font.width(healthText) / 2, healthY)
            val air = HudStatusBarHeightRegistry.getHeight(VanillaHudElements.AIR_BAR)
            val airVisible = player.isUnderWater || player.airSupply < player.maxAirSupply
            if ((client.gui.hud as HudAccessor).`fabric$callGetRiddenEntity`() == null &&
                foodDisplay.visible(ClientOptions.hudSettings.hungerNumbers, player.foodData.foodLevel <= 6))
                text(graphics, foodText, c + 72 - client.font.width(foodText) / 2,
                    h - (if (airVisible) air else HudStatusBarHeightRegistry.getHeight(VanillaHudElements.FOOD_BAR)) - 9, 0xE1A261)
            if (player.health <= 4) warning(graphics, c - 5, h - 40)
        }
        if (ClientOptions.hud == HudMode.NEVER || !ClientAbilityState.isBeyonder) return
        val boxes = HudPlacement.boxes(graphics.guiWidth(), h)
        boxes[HudPart.ABILITY]?.let { drawAbility(graphics, it) }
        boxes[HudPart.AUXILIARY]?.let { drawAuxiliary(graphics, it) }
    }

    fun drawAbility(g: GuiGraphicsExtractor, box: HudBox, preview: Boolean = false) {
        val x = box.rect.x; val y = box.rect.y
        frame(g, x, y, 20, 20)
        val entry = ClientAbilityState.selected?.let(ClientAbilityState::entry)
        val font = Minecraft.getInstance().font
        val name = entry?.name ?: Component.translatable("hud.mysteries_above.no_selection")
        val lines = font.split(name, 143)
        val labelHeight = ceil(lines.size * 9 * .6f).toInt()
        val label = BottomPanel.abilityLabel(g.guiWidth(),g.guiHeight(),box.rect,labelHeight)
        g.pose().pushMatrix()
        g.pose().translate(label.x.toFloat(),label.y.toFloat())
        g.pose().scale(.6f, .6f)
        for ((index, line) in lines.withIndex())
            g.text(font, line, 0, index * 9, opaque(UiTheme.TEXT), true)
        g.pose().popMatrix()
        if (entry == null) { symbol(g, "rune", x + 7, y + 6, 0x65533A); return }
        g.item(entry.icon, x + 2, y + 2)
        g.nextStratum()
        corners(g, x, y, 20, 20, accent())
        val remaining = ClientAbilityState.remainingSeconds(entry.identity)
        if (remaining > 0) {
            UiTheme.cooldownVeil(g, x + 2, y + 2, 16, 1f - ClientAbilityState.cooldownProgress(entry), 1f)
            val label = remaining.toString()
            text(g, label, x + 10 - Minecraft.getInstance().font.width(label) / 2, y + 6)
        }
    }

    fun drawAuxiliary(g: GuiGraphicsExtractor, box: HudBox, preview: Boolean = false) {
        val client = Minecraft.getInstance()
        val values = ClientAbilityState.auxiliary.asList()
        val captions = listOf("${ClientAbilityState.sanityLoss.scale}%", ClientAbilityState.mastery.toString())
        for (index in 0..1) {
            val x = box.rect.x; val y = box.rect.y + index * 22
            val color = listOf(0xD58D9C, 0x94C987)[index]
            symbol(g, listOf("sanity", "potion")[index], x, y + 4, color)
            val title = Component.translatable("hud.mysteries_above.aux.$index")
            val labelScale = minOf(.6f, 46f / client.font.width(title).coerceAtLeast(1))
            g.pose().pushMatrix()
            g.pose().translate((x + 16).toFloat(), y.toFloat())
            g.pose().scale(labelScale, labelScale)
            g.text(client.font, title, 0, 0, opaque(0xD8C6A0), false)
            g.pose().popMatrix()
            frame(g, x + 16, y + 8, 46, 12)
            val width = 42 * values[index] / 10000
            if (width > 0) {
                g.fill(x + 18, y + 10, x + 18 + width, y + 18, opaque(color))
                g.fill(x + 18, y + 10, x + 18 + width, y + 11, opaque(0xDFE8CD))
                g.fill(x + 18, y + 17, x + 18 + width, y + 18, opaque(0x4D5444))
            }
            g.nextStratum()
            g.pose().pushMatrix()
            g.pose().translate(x + 39f - client.font.width(captions[index]) * .25f, y + 12f)
            g.pose().scale(.5f, .5f)
            text(g, captions[index], 0, 0)
            g.pose().popMatrix()
            if (client.gui.screen() != null && !preview) {
                val mx = (client.mouseHandler.xpos() * g.guiWidth() / client.window.screenWidth).toInt()
                val my = (client.mouseHandler.ypos() * g.guiHeight() / client.window.screenHeight).toInt()
                if (mx in x until x + 62 && my in y until y + 20) g.setTooltipForNextFrame(
                    Component.translatable("hud.mysteries_above.aux.$index.tooltip", captions[index]), mx, my)
            }
        }
    }

    private fun labelRise(g: GuiGraphicsExtractor): Int {
        val boxes = HudPlacement.boxes(g.guiWidth(), g.guiHeight())
        val top = boxes.filter { (part, box) ->
            (part == SpiritualityHud.part() || part == HudPart.MARIONETTES) &&
                box.rect.x < g.guiWidth()/2+91 && box.rect.right > g.guiWidth()/2-91 &&
                box.rect.bottom > g.guiHeight()-100
        }
            .values.minOfOrNull { it.rect.y } ?: return 0
        return (g.guiHeight() - top - 47).coerceIn(0,(g.guiHeight()-80).coerceAtLeast(0))
    }

    fun frame(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int, glassOpacity: Float = .84f) {
        g.outline(x, y, width, height, opaque(0x090C10))
        g.outline(x + 1, y + 1, width - 2, height - 2, opaque(0xB99A61))
        g.fill(x + 2, y + 2, x + width - 2, y + height - 2, UiTheme.argb(0x0C1218, glassOpacity))
        g.fill(x + 1, y + 1, x + width - 1, y + 2, opaque(0xDFC28B))
        g.fill(x + width - 2, y + 2, x + width - 1, y + height - 1, opaque(0x65533A))
        g.fill(x + 2, y + height - 2, x + width - 1, y + height - 1, opaque(0x65533A))
    }

    private fun corners(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
        for ((xx, yy, sx, sy) in listOf(listOf(x,y,1,1), listOf(x+w-1,y,-1,1), listOf(x,y+h-1,1,-1), listOf(x+w-1,y+h-1,-1,-1)))
            for (n in 0..2) { g.fill(xx+n*sx,yy,xx+n*sx+1,yy+1,opaque(color)); g.fill(xx,yy+n*sy,xx+1,yy+n*sy+1,opaque(color)) }
    }

    private fun symbol(g: GuiGraphicsExtractor, key: String, x: Int, y: Int, color: Int) {
        for ((dy, row) in symbols.getValue(key).withIndex()) for ((dx, value) in row.withIndex())
            if (value == '#') g.fill(x+dx,y+dy,x+dx+1,y+dy+1,opaque(color))
    }

    private fun warning(g: GuiGraphicsExtractor, x: Int, y: Int) {
        val alpha = if (ClientOptions.reducedMotion) 1f else .75f + sin(tick / 4f) * .25f
        for (yy in 0..8) { val a = kotlin.math.abs(4-yy); g.fill(x+a,y+yy,x+9-a,y+yy+1,opaque(0x090C10)) }
        text(g, "!", x+3,y+1,0xF0D89C,alpha)
    }

    fun text(g: GuiGraphicsExtractor, value: String, x: Int, y: Int, color: Int = 0xEDE8DC, alpha: Float = 1f) {
        val font = Minecraft.getInstance().font
        for ((dx,dy) in listOf(-1 to 0,1 to 0,0 to -1,0 to 1)) g.text(font,value,x+dx,y+dy,UiTheme.argb(0x090C10,alpha),false)
        g.text(font,value,x,y,UiTheme.argb(color,alpha),false)
    }

    private fun accent() = ClientAbilityState.roster?.pathwayId?.let { PathwayBranding.liquidOf(it).packed } ?: 0xDFC28B
    private fun opaque(rgb: Int) = UiTheme.argb(rgb, 1f)
}
