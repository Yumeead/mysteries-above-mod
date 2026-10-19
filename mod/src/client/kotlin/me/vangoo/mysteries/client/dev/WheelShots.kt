package me.vangoo.mysteries.client.dev

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.PathwayBranding
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.domain.hud.HudPart
import me.vangoo.domain.hud.HudEdge
import me.vangoo.domain.hud.HudRect
import me.vangoo.domain.hud.HudPosition
import me.vangoo.domain.hud.SpiritualityMode
import me.vangoo.domain.hud.SpiritualityNumbers
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.client.AbilityGroup
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.HudMode
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.input.ModKeys
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.client.render.WheelLayout
import me.vangoo.mysteries.client.render.WheelSkin
import me.vangoo.mysteries.client.screen.AbilityBookScreen
import me.vangoo.mysteries.client.screen.AbilityWheelScreen
import me.vangoo.mysteries.client.screen.HudSettingsScreen
import me.vangoo.mysteries.client.screen.HudEditorScreen
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.AbilityActionPayload
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.AttackIndicatorStatus
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.MouseButtonInfo
import net.minecraft.client.input.KeyEvent
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory

/**
 * Dev-only: walks every wheel skin through the same states at GUI scales 2, 3 and 4, on a day
 * and a night sky, and saves a screenshot of each. Runs only when `MYSTERIES_WHEEL_SHOTS` is set:
 *
 *     MYSTERIES_WHEEL_SHOTS=1 ./gradlew :mod:runClient
 *
 * Uses `New World` in `mod/run/saves`, or the copy named by `MYSTERIES_SHOTS_WORLD`.
 * Shots land in `mod/run/screenshots`.
 * The states are set on the integrated server directly — this is a camera, not a test of the casts.
 */
object WheelShots {

    private val log = LoggerFactory.getLogger("mysteries_above/wheel-shots")
    private val steps = ArrayDeque<Pair<Int, (Minecraft) -> Unit>>()
    private var wait = 0
    private var started = false

    fun install() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    private fun tick(mc: Minecraft) {
        if (!started) {
            if (mc.gui.screen() is TitleScreen) {
                started = true
                mc.window.setWindowed(1920, 1080)
                val world = System.getenv("MYSTERIES_SHOTS_WORLD") ?: "New World"
                mc.createWorldOpenFlows().openWorld(world) { log.error("Could not open '{}'", world) }
                plan()
            }
            return
        }
        if (mc.player == null) return
        if (System.getenv("MYSTERIES_WHEEL_SHOTS") == "bottom-panel" &&
            mc.gui.screen() is net.minecraft.client.gui.screens.LevelLoadingScreen) return
        if (System.getenv("MYSTERIES_WHEEL_SHOTS") == "bottom-panel" &&
            mc.gui.screen() is net.minecraft.client.gui.screens.PauseScreen) {
            mc.gui.setScreen(null)
            return
        }
        if (wait > 0) { wait--; return }
        val (delay, action) = steps.removeFirstOrNull() ?: return
        action(mc)
        wait = delay
    }

    private fun then(delay: Int, action: (Minecraft) -> Unit) = steps.addLast(delay to action)

    private fun server(mc: Minecraft, body: () -> Unit) {
        mc.singleplayerServer!!.execute(body)
    }

    private fun command(mc: Minecraft, line: String) = server(mc) {
        val server = mc.singleplayerServer!!
        server.commands.performPrefixedCommand(server.createCommandSourceStack(), line)
    }

    private fun page(): List<AbilityRosterPayload.Entry> = ModKeys.visible(ClientOptions.groups[0]).take(WheelLayout.PAGE_SIZE)

    private fun spirituality(mc: Minecraft, share: Float) = server(mc) {
        val id = mc.player!!.uuid
        val beyonder = AbilityServices.beyonders.of(id) ?: return@server
        val max = beyonder.maxSpirituality
        beyonder.setSpirituality(Spirituality.of((max * share).toInt(), max))
        AbilityServices.statePump.pushNow(id)
    }

    private fun cooldowns(mc: Minecraft, on: Boolean) {
        val shown = page()
        server(mc) {
            val id = mc.player!!.uuid
            shown.forEachIndexed { index, entry ->
                val identity = AbilityIdentity(entry.identity)
                if (on && index % 3 == 1 && entry.cooldownSeconds > 0) {
                    AbilityServices.cooldowns.set(id, identity, maxOf(1, entry.cooldownSeconds * (index / 3 + 1) / 4))
                } else {
                    AbilityServices.cooldowns.clear(id, identity)
                }
            }
            AbilityServices.statePump.pushNow(id)
        }
    }

    private fun shot(mc: Minecraft, name: String) =
        Screenshot.grab(mc.gameDirectory, "$name.png", mc.gameRenderer.mainRenderTarget(), 1) { log.info("shot {}", name) }

    /** Survival with levels and armour, so every vanilla row the bar lifts is on screen. */
    private fun planHud(originalSkin: WheelSkin, originalScale: Int) {
        val originalHud = ClientOptions.hud
        val originalGroup = ClientOptions.activeGroup
        val originalSpirit = ClientOptions.showSpirituality
        val originalMotion = ClientOptions.reducedMotion
        then(100) { mc ->
            command(mc, "mysteries become Sun 5 @a")
            command(mc, "weather clear")
            command(mc, "gamemode survival @a")
            command(mc, "experience set @a 30 levels")
            command(mc, "item replace entity @a armor.chest with minecraft:iron_chestplate")
            command(mc, "item replace entity @a weapon.mainhand with minecraft:golden_sword")
            ClientOptions.activeGroup = 0
            ClientOptions.wheelSkin = WheelSkin.ATMOSPHERIC
            ClientOptions.hud = HudMode.ALWAYS
            ClientOptions.showSpirituality = true
            ClientOptions.reducedMotion = false
        }
        then(40) { mc ->
            spirituality(mc, 1f)
            page().firstOrNull()?.let { ModChannels.ABILITY_ACTION.sendToServer(AbilityActionPayload(AbilityActionPayload.Action.SELECT, it.identity, null)) }
        }
        for (scale in listOf(2, 3, 4)) {
            then(40) { mc -> mc.options.guiScale().set(scale); mc.resizeGui() }
            for (night in listOf(false, true)) {
                val bg = if (night) "night" else "day"
                then(20) { mc ->
                    command(mc, if (night) "time set midnight" else "time set noon")
                    mc.player!!.setXRot(if (night) -35f else 30f)
                }
                for ((label, share) in listOf("full" to 1f, "spent" to 0.6f, "critical" to 0.05f)) {
                    then(24) { mc -> spirituality(mc, share) }
                    then(2) { mc -> shot(mc, "bar_gs${scale}_${bg}_$label") }
                }
                run {
                    then(4) { mc -> spirituality(mc, 0.01f) }
                    then(16) { mc ->
                        val screen = AbilityWheelScreen(mc.options.keySaveHotbarActivator)
                        mc.gui.setScreen(screen)
                        screen.mouseMoved(screen.width / 2.0, screen.height / 2.0)
                        spirituality(mc, 0f)
                    }
                    then(2) { mc -> shot(mc, "drained_gs${scale}_${bg}_wheel") }
                    then(6) { mc ->
                        mc.gui.setScreen(null)
                        spirituality(mc, 0f)
                    }
                    then(2) { mc -> shot(mc, "drained_gs${scale}_${bg}_hud") }
                }
            }
        }
        then(30) { mc ->
            mc.options.guiScale().set(3)
            mc.resizeGui()
            ClientOptions.hud = HudMode.AUTO
            command(mc, "time set noon")
            command(mc, "experience set @a 0 levels")
            spirituality(mc, .6f)
        }
        then(120) { mc -> shot(mc, "bar_auto_xp_zero") }
        then(20) { mc -> command(mc, "gamemode creative @a") }
        then(4) { mc -> shot(mc, "bar_auto_creative") }
        then(30) { mc ->
            command(mc, "gamemode survival @a")
            spirituality(mc, .3f)
        }
        repeat(10) { frame ->
            then(3) { mc -> shot(mc, "bar_recovery_$frame") }
        }
        then(20) { mc -> command(mc, "gamemode spectator @a") }
        then(4) { mc -> shot(mc, "bar_spectator_hidden") }
        then(10) { mc ->
            ClientOptions.wheelSkin = originalSkin
            ClientOptions.hud = originalHud
            ClientOptions.activeGroup = originalGroup
            ClientOptions.showSpirituality = originalSpirit
            ClientOptions.reducedMotion = originalMotion
            mc.options.guiScale().set(originalScale)
            command(mc, "gamemode creative @a")
            log.info("wheel shots done")
            mc.stop()
        }
    }


    /** Opens the grimoire on [page]; with [captureRow] it also clicks that row of the keys list. */
    private fun openBook(mc: Minecraft, page: AbilityBookScreen.Page, captureRow: Int = -1) {
        val screen = AbilityBookScreen(page)
        mc.gui.setScreen(screen)
        screen.mouseMoved(0.0, 0.0)
        if (captureRow >= 0) {
            // The same geometry the screen computes: cover, spine, header, first row.
            val bw = minOf(screen.width - 12, 560)
            val bh = minOf(screen.height - 10, 330)
            val x = (screen.width - bw) / 2 + 12 + 40
            val y = (screen.height - bh) / 2 + 60 + 6 + 18 * captureRow + 9
            screen.mouseClicked(MouseButtonEvent(x.toDouble(), y.toDouble(), MouseButtonInfo(0, 0)), false)
        }
    }

    /**
     * The grimoire: every section at GUI 2, 3 and 4 on a day and a night sky, plus the edge
     * states — a key clash, a capture in progress, a long group name, other pathways' emblems.
     */
    private fun planBook(originalScale: Int) {
        val savedGroups = ClientOptions.groups.map { AbilityGroup(it.name, it.abilities?.toMutableList(), LinkedHashMap(it.hotkeys)) }
        val savedActive = ClientOptions.activeGroup
        then(100) { mc ->
            command(mc, "mysteries become Sun 5 @a")
            command(mc, "weather clear")
            command(mc, "mysteries sanity 47 @a")
            command(mc, "mysteries mastery 63.5 @a")
        }
        then(40) { mc ->
            spirituality(mc, 0.62f)
            cooldowns(mc, true)
            val all = ClientAbilityState.entries.filter { it.type != me.vangoo.domain.abilities.AbilityType.PERMANENT_PASSIVE }
            ClientOptions.editGroups {
                while (ClientOptions.groups.size > 1) ClientOptions.groups.removeAt(1)
                val fight = AbilityGroup("Бойовий набір для довгих нічних полювань", all.take(6).map { it.identity }.toMutableList())
                // E is vanilla's inventory: a deliberate clash to show.
                all.getOrNull(0)?.let { fight.bind("key.keyboard.e", it.identity) }
                all.getOrNull(1)?.let { fight.bind("key.keyboard.c", it.identity) }
                all.getOrNull(2)?.let { fight.bind("key.mouse.4", it.identity) }
                ClientOptions.groups += fight
                ClientOptions.groups += AbilityGroup("Подорож", all.drop(6).take(3).map { it.identity }.toMutableList())
                ClientOptions.activeGroup = 1
            }
            all.getOrNull(3)?.let { ModChannels.ABILITY_ACTION.sendToServer(AbilityActionPayload(AbilityActionPayload.Action.SELECT, it.identity, null)) }
        }
        for (scale in listOf(2, 3, 4)) {
            then(20) { mc -> mc.options.guiScale().set(scale); mc.resizeGui() }
            for (night in listOf(false, true)) {
                val bg = if (night) "night" else "day"
                then(20) { mc ->
                    command(mc, if (night) "time set midnight" else "time set noon")
                    mc.player!!.setXRot(if (night) -35f else 10f)
                }
                for (page in AbilityBookScreen.Page.entries) {
                    then(8) { mc -> openBook(mc, page) }
                    then(2) { mc -> shot(mc, "book_gs${scale}_${bg}_${page.name.lowercase()}") }
                }
                if (!night) {
                    then(8) { mc -> openBook(mc, AbilityBookScreen.Page.KEYS, captureRow = 1) }
                    then(2) { mc -> shot(mc, "book_gs${scale}_${bg}_keys_capture") }
                    then(4) { mc -> ClientOptions.activeGroup = 0; openBook(mc, AbilityBookScreen.Page.GROUPS) }
                    then(2) { mc -> shot(mc, "book_gs${scale}_${bg}_groups_all"); ClientOptions.activeGroup = 1 }
                    if (scale == 4) {
                        then(8) { mc ->
                            openBook(mc, AbilityBookScreen.Page.STATUS)
                            val screen = mc.gui.screen()!!
                            repeat(20) { check(screen.mouseScrolled(screen.width.toDouble(), screen.height / 2.0, 0.0, -1.0)) }
                        }
                        then(2) { mc -> shot(mc, "book_gs4_status_scrolled") }
                        then(8) { mc ->
                            openBook(mc, AbilityBookScreen.Page.LIBRARY)
                            val screen = mc.gui.screen()!!
                            val bw = minOf(screen.width - 12, 560)
                            val bh = minOf(screen.height - 10, 330)
                            val lw = (bw - 26) * 9 / 20
                            val filterW = (lw - 13) / 4
                            val x = (screen.width - bw) / 2 + 12 + 5 + 3 * (filterW + 1) + filterW / 2
                            val y = (screen.height - bh) / 2 + 60 + 33
                            screen.mouseClicked(MouseButtonEvent(x.toDouble(), y.toDouble(), MouseButtonInfo(0, 0)), false)
                        }
                        then(2) { mc -> shot(mc, "book_gs4_library_passive_filter") }
                        then(8) { mc ->
                            val search = mc.gui.screen()!!.children().filterIsInstance<EditBox>().single()
                            search.value = "фізичні"
                        }
                        then(2) { mc -> shot(mc, "book_gs4_library_search") }
                    }
                }
            }
            then(2) { mc -> mc.gui.setScreen(null) }
        }
        then(20) { mc -> mc.window.setWindowed(960, 720); mc.options.guiScale().set(3); mc.resizeGui() }
        for (page in AbilityBookScreen.Page.entries) {
            then(8) { mc -> openBook(mc, page) }
            then(2) { mc -> shot(mc, "book_compact_${page.name.lowercase()}") }
        }
        // Other pathways at GUI 3: emblems, modes and a bigger roster.
        then(20) { mc -> mc.window.setWindowed(1920, 1080); mc.options.guiScale().set(3); mc.resizeGui(); command(mc, "time set noon") }
        for (pathway in PathwayBranding.names.sorted().filter { it != "Sun" }) {
            then(40) { mc -> mc.gui.setScreen(null); command(mc, "mysteries become $pathway 5 @a") }
            then(20) { mc -> spirituality(mc, 0.9f); openBook(mc, AbilityBookScreen.Page.STATUS) }
            then(2) { mc -> shot(mc, "book_path_${pathway.lowercase()}_status") }
            then(8) { _ ->
                ClientAbilityState.entries.firstOrNull { it.modes.isNotEmpty() }?.let {
                    ModChannels.ABILITY_ACTION.sendToServer(AbilityActionPayload(AbilityActionPayload.Action.SELECT, it.identity, null))
                }
            }
            then(8) { mc -> openBook(mc, AbilityBookScreen.Page.LIBRARY) }
            then(2) { mc -> shot(mc, "book_path_${pathway.lowercase()}_library") }
        }
        then(10) { mc ->
            mc.gui.setScreen(null)
            ClientOptions.editGroups {
                ClientOptions.groups.clear()
                ClientOptions.groups += savedGroups
                ClientOptions.activeGroup = savedActive
            }
            mc.options.guiScale().set(originalScale)
            log.info("wheel shots done")
            mc.stop()
        }
    }

    private enum class State { NORMAL, HOVER, COOLDOWN, NO_SPIRITUALITY }

    private fun planHudResize(originalScale: Int) {
        val saved = ClientOptions.hudSettings
        fun drag(screen: HudEditorScreen, x: Double, y: Double, dx: Double, dy: Double) {
            val start = MouseButtonEvent(x, y, MouseButtonInfo(0, 0))
            val end = MouseButtonEvent(x + dx, y + dy, MouseButtonInfo(0, 0))
            check(screen.mouseClicked(start, false))
            check(screen.mouseDragged(end, dx, dy))
            check(screen.mouseReleased(end))
        }
        then(10) { mc ->
            mc.options.guiScale().set(3)
            mc.resizeGui()
            mc.options.pauseOnLostFocus = false
            mc.gui.setScreen(HudEditorScreen(AbilityBookScreen()))
        }
        for (mode in listOf(SpiritualityMode.FLASK, SpiritualityMode.BAR)) {
            then(3) { mc ->
                val screen = mc.gui.screen() as HudEditorScreen
                ClientOptions.hudSettings = saved.copy(spirituality = mode, positions = emptyMap(), scales = emptyMap())
                val parts = screen.boxes().keys.toList()
                for (part in parts) {
                    for (edge in HudEdge.entries) {
                        ClientOptions.hudSettings = ClientOptions.hudSettings.copy(positions = emptyMap(), scales = emptyMap())
                        val initial = screen.boxes().getValue(part).rect
                        val centered = HudRect((screen.width - initial.width) / 2, (screen.height - initial.height) / 2,
                            initial.width, initial.height)
                        ClientOptions.hudSettings = ClientOptions.hudSettings.copy(positions = mapOf(
                            part to HudPosition.of(centered, screen.width, screen.height)))
                        val before = screen.boxes().getValue(part)
                        val x = before.rect.x + (edge.horizontal + 1) * before.rect.width / 2.0
                        val y = before.rect.y + (edge.vertical + 1) * before.rect.height / 2.0
                        drag(screen, x, y, edge.horizontal * before.rect.width * .2, edge.vertical * before.rect.height * .2)
                        val larger = screen.boxes().getValue(part)
                        check(larger.scale > before.scale) { "Did not enlarge $part from $edge" }
                        check(ClientOptions.hudSettings.scales.keys == setOf(part)) { "Resized another HUD part" }
                        val written = ClientOptions.hudSettings
                        ClientOptions.load()
                        check(ClientOptions.hudSettings == written) { "HUD size did not survive JSON reload" }
                        val rx = larger.rect.x + (edge.horizontal + 1) * larger.rect.width / 2.0
                        val ry = larger.rect.y + (edge.vertical + 1) * larger.rect.height / 2.0
                        drag(screen, rx, ry, -edge.horizontal * before.rect.width * .3, -edge.vertical * before.rect.height * .3)
                        check(screen.boxes().getValue(part).scale < before.scale) { "Did not shrink $part from $edge" }
                    }
                }
                log.info("HUD resize: all edges, corners and JSON reload passed for {}", mode)
            }
        }
        then(3) { mc ->
            val screen = mc.gui.screen() as HudEditorScreen
            click(mc, "Скинути цей елемент")
            check(ClientOptions.hudSettings.scales.isEmpty())
            val box = screen.boxes().getValue(HudPart.ABILITY).rect
            drag(screen, box.x + box.width / 2.0, box.y + box.height / 2.0, 30.0, -20.0)
            check(ClientOptions.hudSettings.scales.isEmpty()) { "Moving changed the size" }
            ClientOptions.hudSettings = ClientOptions.hudSettings.copy(scales = mapOf(HudPart.ABILITY to 1.5f))
        }
        then(3) { mc -> shot(mc, "hud_resize_editor") }
        then(3) { mc -> mc.window.setWindowed(960, 720); mc.options.guiScale().set(3); mc.resizeGui() }
        then(3) { mc ->
            val screen = mc.gui.screen() as HudEditorScreen
            check(screen.boxes().values.all { it.rect.x >= 0 && it.rect.y >= 0 && it.rect.right <= screen.width && it.rect.bottom <= screen.height })
            shot(mc, "hud_resize_narrow")
            click(mc, "Скинути все")
            check(ClientOptions.hudSettings.positions.isEmpty() && ClientOptions.hudSettings.scales.isEmpty())
            screen.onClose()
            mc.gui.setScreen(HudEditorScreen(AbilityBookScreen()))
            check(ClientOptions.hudSettings.scales.isEmpty())
        }
        then(5) { mc ->
            mc.gui.setScreen(null)
            ClientOptions.hudSettings = saved
            mc.options.guiScale().set(originalScale)
            log.info("HUD resize checks passed")
            mc.stop()
        }
    }

    private fun click(mc: Minecraft, caption: String) {
        val screen = mc.gui.screen()!!
        val button = screen.children().filterIsInstance<AbstractWidget>().first { it.message.string == caption }
        val event = MouseButtonEvent(button.x + button.width / 2.0, button.y + button.height / 2.0, MouseButtonInfo(0, 0))
        screen.mouseClicked(event, false)
        screen.mouseReleased(event)
    }

    /** End-to-end HUD checks; run against a copied world in an isolated game directory. */
    private fun planSpirituality(originalScale: Int) {
        val savedSettings = ClientOptions.hudSettings
        val savedHud = ClientOptions.hud
        val client = Minecraft.getInstance()
        val savedHand = client.options.mainHand().get()
        then(100) { mc ->
            ClientOptions.hud = HudMode.ALWAYS
            ClientOptions.hudSettings = savedSettings.copy(spirituality = SpiritualityMode.FLASK,
                numbers = SpiritualityNumbers.ALWAYS, positions = emptyMap())
            mc.options.pauseOnLostFocus = false
            mc.options.attackIndicator().set(AttackIndicatorStatus.HOTBAR)
            mc.player!!.setXRot(12f)
            command(mc, "gamemode survival @a")
            command(mc, "time set noon")
            command(mc, "weather clear")
        }
        for (scale in listOf(2, 3, 4)) {
            then(10) { mc -> mc.options.guiScale().set(scale); mc.resizeGui() }
            for (pathway in listOf("Fool", "Mother", "Justiciar")) {
                then(30) { mc -> command(mc, "mysteries become $pathway 5 @a") }
                for ((label, share) in listOf("full" to 1f, "third" to .33f, "low" to .1f, "minimum" to .005f, "empty" to 0f)) {
                    then(24) { mc -> spirituality(mc, share) }
                    then(2) { mc -> shot(mc, "flask_${pathway.lowercase()}_gs${scale}_$label") }
                }
            }
        }
        then(15) { mc ->
            mc.options.mainHand().set(HumanoidArm.LEFT)
            mc.options.broadcastOptions()
            mc.player!!.swing(net.minecraft.world.InteractionHand.MAIN_HAND)
        }
        then(2) { mc -> shot(mc, "flask_left_handed") }
        then(15) { mc ->
            mc.options.mainHand().set(savedHand)
            mc.options.broadcastOptions()
            mc.window.setWindowed(960, 720)
            mc.options.guiScale().set(3)
            mc.resizeGui()
        }
        then(2) { mc -> shot(mc, "flask_narrow") }
        then(75) { mc ->
            mc.window.setWindowed(1920, 1080)
            mc.options.guiScale().set(3)
            mc.resizeGui()
            ClientOptions.hudSettings = ClientOptions.hudSettings.copy(numbers = SpiritualityNumbers.ON_CHANGE)
            spirituality(mc, 1f)
        }
        then(2) { mc -> shot(mc, "flask_on_change_expired") }
        then(8) { mc -> mc.gui.setScreen(ChatScreen("", false)) }
        then(2) { mc -> shot(mc, "flask_chat_number") }
        then(8) { mc -> mc.gui.setScreen(InventoryScreen(mc.player!!)) }
        then(2) { mc -> shot(mc, "flask_inventory_number") }
        then(8) { mc -> mc.gui.setScreen(AbilityBookScreen(AbilityBookScreen.Page.STATUS)) }
        then(2) { mc -> shot(mc, "flask_book_gear") }
        then(8) { mc -> click(mc, "Налаштування"); check(mc.gui.screen() is HudSettingsScreen) }
        then(2) { mc -> shot(mc, "flask_settings") }
        then(8) { mc ->
            click(mc, "Смуга")
            click(mc, "Завжди")
            check(ClientOptions.hudSettings.spirituality == SpiritualityMode.BAR)
        }
        then(2) { mc -> shot(mc, "flask_settings_bar") }
        then(8) { mc -> mc.gui.setScreen(null) }
        then(2) { mc -> shot(mc, "flask_fixed_bar") }
        then(8) { mc ->
            mc.gui.setScreen(AbilityBookScreen())
            click(mc, "Налаштування")
            click(mc, "Ніколи")
            check(ClientOptions.hudSettings.numbers == SpiritualityNumbers.NEVER)
            click(mc, "Флакон")
        }
        then(2) { mc -> shot(mc, "flask_settings_never") }
        then(8) { mc -> click(mc, "Перемістити елементи"); check(mc.gui.screen() is HudEditorScreen) }
        then(2) { mc -> shot(mc, "flask_editor") }
        then(8) { mc ->
            val screen = mc.gui.screen() as HudEditorScreen
            val box = screen.boxes().getValue(HudPart.FLASK).rect
            val event = MouseButtonEvent(box.x + box.width / 2.0, box.y + box.height / 2.0, MouseButtonInfo(0, 0))
            screen.mouseClicked(event, false)
            val moved = MouseButtonEvent(event.x() - 90, event.y() - 65, MouseButtonInfo(0, 0))
            screen.mouseDragged(moved, -90.0, -65.0)
            screen.mouseReleased(moved)
            check(HudPart.FLASK in ClientOptions.hudSettings.positions)
            val written = ClientOptions.hudSettings
            ClientOptions.load()
            check(ClientOptions.hudSettings == written) { "HUD positions did not survive JSON reload" }
            Files.copy(mc.gameDirectory.toPath().resolve("config/mysteries_above_client.json"),
                mc.gameDirectory.toPath().resolve("hud-dragged-config.json"), StandardCopyOption.REPLACE_EXISTING)
            val beforeKey = screen.boxes().getValue(HudPart.FLASK).rect.x
            screen.keyPressed(KeyEvent(GLFW.GLFW_KEY_LEFT, 0, 0))
            check(screen.boxes().getValue(HudPart.FLASK).rect.x == beforeKey - 1)
            log.info("HUD check: drag and config round trip passed")
        }
        then(2) { mc -> shot(mc, "flask_editor_moved") }
        then(12) { mc -> mc.window.setWindowed(960, 720); mc.options.guiScale().set(3); mc.resizeGui() }
        then(2) { mc ->
            val screen = mc.gui.screen() as HudEditorScreen
            check(screen.boxes().values.all { it.rect.x >= 0 && it.rect.y >= 0 && it.rect.right <= screen.width && it.rect.bottom <= screen.height })
            shot(mc, "flask_editor_narrow")
        }
        then(12) { mc -> mc.window.setWindowed(1920, 1080); mc.options.guiScale().set(3); mc.resizeGui() }
        then(8) { mc ->
            val screen = mc.gui.screen() as HudEditorScreen
            val flask = screen.boxes().getValue(HudPart.FLASK).rect
            val ability = screen.boxes().getValue(HudPart.ABILITY).rect
            ClientOptions.hudSettings = ClientOptions.hudSettings.copy(positions = ClientOptions.hudSettings.positions +
                (HudPart.ABILITY to HudPosition.of(ability, screen.width, screen.height)))
            val start = MouseButtonEvent(flask.x + flask.width / 2.0, flask.y + flask.height / 2.0, MouseButtonInfo(0, 0))
            screen.mouseClicked(start, false)
            val target = MouseButtonEvent(ability.x + flask.width / 2.0, ability.y + flask.height / 2.0, MouseButtonInfo(0, 0))
            screen.mouseDragged(target, target.x() - start.x(), target.y() - start.y())
            screen.mouseReleased(target)
            check(screen.boxes().getValue(HudPart.FLASK).rect.overlaps(screen.boxes().getValue(HudPart.ABILITY).rect))
        }
        then(2) { mc -> shot(mc, "flask_editor_overlap") }
        then(8) { mc -> click(mc, "Скинути цей елемент"); check(HudPart.FLASK !in ClientOptions.hudSettings.positions) }
        then(8) { mc -> click(mc, "Скинути все"); check(ClientOptions.hudSettings.positions.isEmpty()) }
        then(8) { mc -> click(mc, "Готово"); click(mc, "Вимкнено"); click(mc, "Перемістити елементи") }
        then(2) { mc ->
            check((mc.gui.screen() as HudEditorScreen).boxes().keys == setOf(HudPart.FLASK, HudPart.ABILITY, HudPart.MARIONETTES))
            shot(mc, "flask_editor_off")
            command(mc, "mysteries forget @a")
        }
        then(20) { mc -> mc.gui.setScreen(AbilityBookScreen(AbilityBookScreen.Page.STATUS)) }
        then(2) { mc -> check(mc.gui.screen() is AbilityBookScreen); shot(mc, "flask_book_no_pathway") }
        then(8) { mc -> click(mc, "Налаштування"); click(mc, "Флакон"); click(mc, "Перемістити елементи") }
        then(2) { mc ->
            check((mc.gui.screen() as HudEditorScreen).boxes().size == 3)
            shot(mc, "flask_editor_no_pathway")
        }
        then(10) { mc ->
            mc.gui.setScreen(null)
            ClientOptions.hudSettings = savedSettings
            ClientOptions.hud = savedHud
            mc.options.guiScale().set(originalScale)
            log.info("spirituality HUD checks passed")
            mc.stop()
        }
    }

    private fun plan() {
        val originalSkin = ClientOptions.wheelSkin
        val originalScale = Minecraft.getInstance().options.guiScale().get()
        if (System.getenv("MYSTERIES_WHEEL_SHOTS") == "bottom-panel") return planBottomPanel(originalScale)
        if (System.getenv("MYSTERIES_WHEEL_SHOTS") == "flask-review") return planFlaskReview(originalScale)
        if (System.getenv("MYSTERIES_WHEEL_SHOTS") == "hud-resize") return planHudResize(originalScale)
        if (System.getenv("MYSTERIES_WHEEL_SHOTS") == "spirituality-reload") {
            then(20) { mc ->
                val settings = ClientOptions.hudSettings
                check(settings.spirituality == SpiritualityMode.FLASK && settings.numbers == SpiritualityNumbers.NEVER &&
                    HudPart.FLASK in settings.positions) { "HUD settings did not survive a client restart" }
                mc.gui.setScreen(HudEditorScreen(AbilityBookScreen()))
            }
            then(2) { mc -> shot(mc, "flask_persisted_after_restart") }
            then(8) { mc ->
                val screen = mc.gui.screen() as HudEditorScreen
                val ability = screen.boxes().getValue(HudPart.ABILITY).rect
                val flask = screen.boxes().getValue(HudPart.FLASK).rect
                ClientOptions.hudSettings = ClientOptions.hudSettings.copy(positions = ClientOptions.hudSettings.positions +
                    (HudPart.ABILITY to HudPosition.of(ability, screen.width, screen.height)))
                val start = MouseButtonEvent(flask.x + flask.width / 2.0, flask.y + flask.height / 2.0, MouseButtonInfo(0, 0))
                screen.mouseClicked(start, false)
                val target = MouseButtonEvent(ability.x + flask.width / 2.0, ability.y + flask.height / 2.0, MouseButtonInfo(0, 0))
                screen.mouseDragged(target, target.x() - start.x(), target.y() - start.y())
                screen.mouseReleased(target)
                check(screen.boxes().getValue(HudPart.FLASK).rect.overlaps(screen.boxes().getValue(HudPart.ABILITY).rect))
            }
            then(2) { mc -> shot(mc, "flask_editor_overlap") }
            then(5) { mc -> log.info("HUD check: settings survived a client restart"); mc.stop() }
            return
        }
        if (System.getenv("MYSTERIES_WHEEL_SHOTS") == "spirituality") return planSpirituality(originalScale)
        // `MYSTERIES_WHEEL_SHOTS=hud` shoots the HUD instead; anything else compares the wheel skins.
        if (System.getenv("MYSTERIES_WHEEL_SHOTS") == "hud") return planHud(originalSkin, originalScale)
        if (System.getenv("MYSTERIES_WHEEL_SHOTS") == "book") return planBook(originalScale)
        val skins = WheelSkin.entries
        val states = State.entries
        then(100) { mc ->
            command(mc, "mysteries become Sun 5 @a")
            command(mc, "weather clear")
            ClientOptions.activeGroup = 0
        }
        then(40) { mc ->
            spirituality(mc, 1f)
            page().firstOrNull()?.let { ModChannels.ABILITY_ACTION.sendToServer(AbilityActionPayload(AbilityActionPayload.Action.SELECT, it.identity, null)) }
        }
        for (scale in listOf(2, 3, 4)) {
            then(10) { mc -> mc.options.guiScale().set(scale); mc.resizeGui() }
            for (night in listOf(false, true)) {
                then(20) { mc ->
                    command(mc, if (night) "time set midnight" else "time set noon")
                    mc.player!!.setXRot(-35f)
                }
                for (skin in skins) for (state in states) {
                    then(6) { mc ->
                        ClientOptions.wheelSkin = skin
                        cooldowns(mc, state == State.COOLDOWN)
                        spirituality(mc, if (state == State.NO_SPIRITUALITY) 0.02f else 1f)
                    }
                    then(16) { mc ->
                        val screen = AbilityWheelScreen(mc.options.keySaveHotbarActivator)
                        mc.gui.setScreen(screen)
                        val shown = page()
                        val target = if (state == State.HOVER) shown.indices.firstOrNull { it >= 2 && shown[it].modes.isEmpty() } ?: 2 else -1
                        val scaleUi = ClientOptions.hudScale
                        if (target >= 0) {
                            val (dx, dy) = WheelLayout.offset(target, shown.size)
                            screen.mouseMoved(screen.width / 2 + dx * scaleUi.toDouble(), screen.height / 2 + dy * scaleUi.toDouble())
                        } else {
                            screen.mouseMoved(screen.width / 2.0, screen.height / 2.0)
                        }
                    }
                    then(2) { mc ->
                        val name = "wheel_${skin.name.lowercase()}_gs${scale}_${if (night) "night" else "day"}_${state.name.lowercase()}.png"
                        Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1) { log.info("shot {}", name) }
                    }
                    then(2) { mc -> mc.gui.setScreen(null) }
                }
            }
        }
        then(10) { mc ->
            ClientOptions.wheelSkin = originalSkin
            mc.options.guiScale().set(originalScale)
            log.info("wheel shots done")
            mc.stop()
        }
    }

    /** Exercises the approved panel only in the isolated copy selected by the test launcher. */
    private fun planBottomPanel(originalScale: Int) {
        val saved = ClientOptions.hudSettings
        val savedHud = ClientOptions.hud
        val savedMotion = ClientOptions.reducedMotion
        val savedHand = Minecraft.getInstance().options.mainHand().get()
        val settings = me.vangoo.domain.hud.HudSettings()
        then(100) { mc ->
            command(mc,"gamemode creative @a")
            command(mc,"forceload add -16 -16 16 16")
        }
        then(90) { mc ->
            ClientOptions.hudSettings = settings
            ClientOptions.hud = HudMode.ALWAYS
            ClientOptions.reducedMotion = false
            mc.options.pauseOnLostFocus = false
            mc.options.attackIndicator().set(AttackIndicatorStatus.HOTBAR)
            command(mc,"mysteries become Fool 5 @a")
            command(mc,"gamemode survival @a")
            command(mc,"weather clear")
            command(mc,"time set noon")
            command(mc,"fill -8 199 -8 8 199 8 minecraft:grass_block")
            command(mc,"tp @a 0 200 0 0 -15")
            command(mc,"experience set @a 27 levels")
            server(mc) {
                val player=mc.singleplayerServer!!.playerList.getPlayer(mc.player!!.uuid)!!
                val items=listOf(net.minecraft.world.item.Items.GOLDEN_SWORD,net.minecraft.world.item.Items.GOLDEN_PICKAXE,
                    net.minecraft.world.item.Items.GOLDEN_AXE,net.minecraft.world.item.Items.GOLDEN_SHOVEL,
                    net.minecraft.world.item.Items.GOLDEN_HOE,net.minecraft.world.item.Items.GOLDEN_APPLE,
                    net.minecraft.world.item.Items.GOLDEN_CARROT,net.minecraft.world.item.Items.GOLD_INGOT,
                    net.minecraft.world.item.Items.RAW_GOLD)
                items.forEachIndexed { index,item -> player.inventory.setItem(index,net.minecraft.world.item.ItemStack(item)) }
                for (slot in listOf(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,
                    net.minecraft.world.entity.EquipmentSlot.OFFHAND)) player.setItemSlot(slot,net.minecraft.world.item.ItemStack.EMPTY)
                player.health=player.maxHealth
                player.foodData.foodLevel=20
                player.abilities.invulnerable=true
                player.onUpdateAbilities()
            }
        }
        then(30) { mc ->
            spirituality(mc,1f)
            server(mc) { check(mc.singleplayerServer!!.overworld().getBlockState(BlockPos(0,199,0)).block == Blocks.GRASS_BLOCK) }
            val entry=checkNotNull(ClientAbilityState.entries.filter { it.type==me.vangoo.domain.abilities.AbilityType.ACTIVE }
                .maxByOrNull { it.name.string.length })
            server(mc) {
                AbilityServices.selection.select(mc.player!!.uuid,AbilityIdentity(entry.identity))
                AbilityServices.statePump.pushNow(mc.player!!.uuid)
            }
        }
        for (scale in listOf(2,3,4)) {
            then(25) { mc -> mc.options.guiScale().set(scale);mc.resizeGui() }
            then(2) { mc -> shot(mc,"hud04_gs${scale}_empty_offhand") }
        }
        repeat(20) { frame ->
            then(5) { mc -> shot(mc,"hud04_liquid_live_${frame.toString().padStart(2,'0')}") }
        }
        then(5) { ClientOptions.reducedMotion=true }
        then(10) { mc -> shot(mc,"hud04_liquid_still_0") }
        then(5) { mc -> shot(mc,"hud04_liquid_still_1") }
        then(5) { ClientOptions.reducedMotion=false }
        for ((sanity, mastery) in listOf(37 to 42.37,0 to 100.0,0 to 0.0)) {
            then(25) { mc ->
                command(mc,"mysteries sanity $sanity @a")
                command(mc,"mysteries mastery $mastery @a")
            }
            then(2) { mc ->
                val values=ClientAbilityState.auxiliary
                check(values.sanityLoss==sanity*100 && values.potionDigestion==Math.round(mastery*100).toInt()) {
                    "HUD indicators differ from the server: $values"
                }
                shot(mc,"hud04_aux_${sanity}_${mastery.toInt()}")
            }
        }
        then(30) { mc -> server(mc) {
            val beyonder=AbilityServices.beyonders.of(mc.player!!.uuid)!!
            beyonder.increaseSanityLoss(21)
            beyonder.setMastery(me.vangoo.domain.valueobjects.Mastery.of(63.5))
            beyonder.setSpirituality(Spirituality.of(beyonder.maxSpirituality,beyonder.maxSpirituality))
        } }
        then(2) { mc ->
            check(ClientAbilityState.auxiliary==me.vangoo.domain.hud.AuxiliaryValues(2100,6350)) {
                "Passive changes did not reach the HUD at full spirituality"
            }
            shot(mc,"hud04_aux_passive")
        }
        then(25) { mc ->
            command(mc,"mysteries become Fool 0 @a")
            ClientOptions.hudSettings=settings.copy(numbers=SpiritualityNumbers.ALWAYS)
        }
        then(25) { mc -> spirituality(mc,1f) }
        then(2) { mc -> shot(mc,"hud04_caption_14000") }
        then(25) { mc ->
            command(mc,"mysteries become Fool 5 @a")
            ClientOptions.hudSettings=settings
        }
        for (pathway in listOf("Mother","Justiciar","WheelOfFortune","WhiteTower")) {
            then(25) { mc -> command(mc,"mysteries become $pathway 5 @a") }
            then(25) { mc -> spirituality(mc,1f) }
            then(2) { mc -> shot(mc,"hud04_full_${pathway.lowercase()}") }
        }
        for ((label,level) in listOf("full" to 1f,"third" to .33f,"low" to .1f,"minimum" to .005f,"empty" to 0f)) {
            then(3) { mc -> mc.gui.setScreen(FlaskReviewScreen(level,false,false,true,orb=true)) }
            then(2) { mc -> shot(mc,"hud04_all_orbs_$label") }
        }
        then(2) { mc -> mc.gui.setScreen(null) }
        then(25) { mc -> command(mc,"mysteries become Fool 5 @a") }
        then(30) { mc ->
            mc.options.guiScale().set(3);mc.resizeGui()
            command(mc,"item replace entity @a weapon.offhand with minecraft:golden_apple")
            command(mc,"item replace entity @a armor.chest with minecraft:iron_chestplate")
            spirituality(mc,.1f)
        }
        then(2) { mc -> shot(mc,"hud04_low_spirit_armor") }
        then(25) { mc -> server(mc) {
            val player=mc.singleplayerServer!!.playerList.getPlayer(mc.player!!.uuid)!!
            player.health=3f;player.foodData.foodLevel=3
        } }
        then(2) { mc -> shot(mc,"hud04_low_health_hunger") }
        then(25) { mc ->
            server(mc) {
                val player=mc.singleplayerServer!!.playerList.getPlayer(mc.player!!.uuid)!!
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)!!.baseValue=40.0
                player.health=31f;player.foodData.foodLevel=20
            }
            spirituality(mc,1f)
        }
        then(2) { mc -> shot(mc,"hud04_health40") }
        then(25) { mc ->
            ClientOptions.hudSettings=ClientOptions.hudSettings.copy(health=me.vangoo.domain.hud.HealthStyle.BAR,
                hunger=me.vangoo.domain.hud.HungerStyle.BAR)
        }
        then(2) { mc -> shot(mc,"hud04_bars") }
        then(35) { mc ->
            ClientOptions.hudSettings=settings
            command(mc,"fill -2 200 -2 2 204 2 minecraft:water")
            server(mc) { mc.singleplayerServer!!.playerList.getPlayer(mc.player!!.uuid)!!.airSupply=120 }
        }
        then(2) { mc ->
            check(mc.player!!.isUnderWater) { "Underwater scene did not load" }
            shot(mc,"hud04_underwater")
        }
        then(30) { mc ->
            command(mc,"fill -2 200 -2 2 204 2 minecraft:air")
            mc.options.mainHand().set(HumanoidArm.LEFT);mc.options.broadcastOptions()
            mc.player!!.swing(net.minecraft.world.InteractionHand.MAIN_HAND)
            ClientAbilityState.entries.firstOrNull { it.cooldownSeconds > 0 }?.let { entry ->
                server(mc) {
                    AbilityServices.selection.select(mc.player!!.uuid,AbilityIdentity(entry.identity))
                    AbilityServices.cooldowns.set(mc.player!!.uuid,AbilityIdentity(entry.identity),160)
                    AbilityServices.statePump.pushNow(mc.player!!.uuid)
                }
            }
        }
        then(2) { mc -> shot(mc,"hud04_left_hand") }
        then(25) { mc -> mc.window.setWindowed(1280,720);mc.options.guiScale().set(4);mc.resizeGui() }
        then(2) { mc -> shot(mc,"hud04_narrow_requested4") }
        then(12) { mc -> mc.gui.setScreen(HudSettingsScreen(AbilityBookScreen())) }
        then(2) { mc -> shot(mc,"hud04_settings_narrow") }
        then(10) { mc ->
            val screen=mc.gui.screen() as HudSettingsScreen
            check(screen.mouseScrolled(screen.width/2.0,screen.height/2.0,0.0,-8.0))
        }
        then(2) { mc -> shot(mc,"hud04_settings_scrolled") }
        then(10) { mc -> mc.gui.setScreen(HudEditorScreen(HudSettingsScreen(AbilityBookScreen()))) }
        then(2) { mc ->
            val editor=mc.gui.screen() as HudEditorScreen
            check(HudPart.FLASK in editor.boxes())
            check(HudPart.AUXILIARY in editor.boxes())
            shot(mc,"hud04_editor")
        }
        then(10) { mc ->
            val editor=mc.gui.screen() as HudEditorScreen
            val box=editor.boxes().getValue(HudPart.FLASK).rect
            val legacy=ClientOptions.hudSettings.positions
            val scales=ClientOptions.hudSettings.scales
            val start=MouseButtonEvent(box.x+box.width/2.0,box.y+box.height/2.0,MouseButtonInfo(0,0))
            val moved=MouseButtonEvent(16+box.width/2.0,54+box.height/2.0,MouseButtonInfo(0,0))
            check(editor.mouseClicked(start,false))
            check(editor.mouseDragged(moved,moved.x()-start.x(),moved.y()-start.y()))
            check(editor.mouseReleased(moved))
            check(editor.boxes().getValue(HudPart.FLASK).rect!=box)
            val beforeKey=editor.boxes().getValue(HudPart.FLASK).rect
            check(editor.keyPressed(KeyEvent(GLFW.GLFW_KEY_LEFT,0,0)))
            val placed=editor.boxes().getValue(HudPart.FLASK)
            check(placed.rect.x==beforeKey.x-1 && placed.rect.y==beforeKey.y && placed.scale==1f)
            val written=ClientOptions.hudSettings
            check(HudPart.FLASK in written.panelPositions && written.positions==legacy && written.scales==scales)
            ClientOptions.load()
            check(ClientOptions.hudSettings==written) { "Mod orb position did not survive JSON reload" }
            mc.gui.setScreen(HudEditorScreen(HudSettingsScreen(AbilityBookScreen())))
            check((mc.gui.screen() as HudEditorScreen).boxes().getValue(HudPart.FLASK)==placed)
        }
        then(2) { mc -> shot(mc,"hud04_editor_orb_moved") }
        then(5) { mc -> mc.gui.setScreen(null) }
        then(2) { mc ->
            val width=mc.window.guiScaledWidth;val height=mc.window.guiScaledHeight
            val expected=ClientOptions.hudSettings.panelPositions.getValue(HudPart.FLASK).resolve(48,44,width,height)
            check(me.vangoo.mysteries.client.hud.HudPlacement.boxes(width,height).getValue(HudPart.FLASK).rect==expected)
            shot(mc,"hud04_orb_moved")
        }
        then(10) { mc ->
            mc.gui.setScreen(HudEditorScreen(HudSettingsScreen(AbilityBookScreen())))
            val editor=mc.gui.screen() as HudEditorScreen
            val moved=ClientOptions.hudSettings
            ClientOptions.hudSettings=moved.copy(panelPositions=moved.panelPositions+
                (HudPart.FLASK to HudPosition(me.vangoo.domain.hud.HorizontalAnchor.RIGHT,
                    me.vangoo.domain.hud.VerticalAnchor.TOP,10000,-10000)))
            val edge=editor.boxes().getValue(HudPart.FLASK).rect
            check(edge.x==editor.width-48 && edge.y==0)
            check(me.vangoo.mysteries.client.hud.BottomHud.chatRise(editor.height)<=editor.height-80)
            ClientOptions.hudSettings=moved
            val box=editor.boxes().getValue(HudPart.FLASK).rect
            val select=MouseButtonEvent(box.x+box.width/2.0,box.y+box.height/2.0,MouseButtonInfo(0,0))
            editor.mouseClicked(select,false);editor.mouseReleased(select)
            click(mc,"Скинути цей елемент")
            check(HudPart.FLASK !in ClientOptions.hudSettings.panelPositions)
            check(editor.boxes().getValue(HudPart.FLASK).rect!=box)
            log.info("Mod orb check passed: dragging, keyboard, reload, reopened editor, screen bounds and reset")
        }
        then(15) { mc ->
            mc.gui.setScreen(null)
            ClientOptions.hudSettings=ClientOptions.hudSettings.copy(interfaceStyle=me.vangoo.domain.hud.InterfaceStyle.VANILLA)
        }
        then(2) { mc -> shot(mc,"hud04_vanilla_style") }
        then(15) { mc ->
            ClientOptions.hudSettings=settings.copy(spirituality=SpiritualityMode.OFF)
        }
        then(2) { mc -> shot(mc,"hud04_spirit_off") }
        then(15) { mc -> ClientOptions.hudSettings=settings.copy(spirituality=SpiritualityMode.BAR) }
        then(2) { mc -> shot(mc,"hud04_legacy_bar") }
        then(25) { mc ->
            ClientOptions.hudSettings=settings.copy(numbers=SpiritualityNumbers.ALWAYS)
            mc.options.guiScale().set(3);mc.resizeGui()
            command(mc,"fill -8 200 -8 8 204 8 minecraft:air")
            mc.player!!.yRot=0f
            spirituality(mc,0f)
        }
        then(2) { mc -> shot(mc,"hud04_motion_empty") }
        for (direction in listOf(1,-1)) {
            repeat(8) { then(0) { mc -> mc.player!!.yRot+=20f*direction } }
            then(1) { mc -> shot(mc,if(direction>0) "hud04_motion_turn_right" else "hud04_motion_turn_left") }
        }
        then(45) { }
        then(2) { mc -> shot(mc,"hud04_motion_settled") }
        then(2) { mc -> spirituality(mc,.5f) }
        then(5) { mc -> shot(mc,"hud04_motion_gaining") }
        then(25) { }
        then(2) { mc -> spirituality(mc,.1f) }
        then(4) { mc -> shot(mc,"hud04_motion_spending") }
        then(25) { }
        then(2) { mc -> shot(mc,"hud04_motion_low") }
        then(10) { mc ->
            val position=HudPosition.of(HudRect(270,90,20,20),mc.window.guiScaledWidth,mc.window.guiScaledHeight)
            val orbPosition=HudPosition.of(HudRect(16,54,48,44),mc.window.guiScaledWidth,mc.window.guiScaledHeight)
            val expected=settings.copy(panelPositions=mapOf(HudPart.ABILITY to position,HudPart.FLASK to orbPosition),
                halfHeart=me.vangoo.domain.hud.HalfHeartStyle.LEFT,
                healthNumbers=SpiritualityNumbers.NEVER,hungerNumbers=SpiritualityNumbers.ALWAYS)
            ClientOptions.hudSettings=expected;ClientOptions.load()
            check(ClientOptions.hudSettings==expected) { "Panel settings failed to round-trip" }
            ClientOptions.hudSettings=saved;ClientOptions.hud=savedHud
            mc.options.mainHand().set(savedHand);mc.options.broadcastOptions()
            ClientOptions.reducedMotion=savedMotion
            mc.options.guiScale().set(originalScale)
            log.info("Bottom panel check passed: native sprites, empty offhand, both hands, resources, settings and persistence")
            mc.stop()
        }
    }

    private fun planFlaskReview(originalScale: Int) {
        val saved = ClientOptions.hudSettings
        val savedHud = ClientOptions.hud
        then(80) { mc ->
            ClientOptions.hud = HudMode.ALWAYS
            ClientOptions.hudSettings = saved.copy(spirituality = SpiritualityMode.FLASK,
                numbers = SpiritualityNumbers.ALWAYS, flaskBacking = false, positions = emptyMap(), scales = emptyMap())
            mc.options.pauseOnLostFocus = false
            command(mc, "gamemode creative @a")
            command(mc, "weather clear")
            command(mc, "time set noon")
            command(mc, "mysteries become Door 5 @a")
            command(mc, "forceload add -48 -32 96 48")
            command(mc, "tp @a 0 200 0 0 -80")
        }
        then(80) { mc ->
            command(mc, "fill -16 199 -16 16 199 16 minecraft:stone")
            command(mc, "fill -40 180 24 40 220 24 minecraft:snow_block")
            command(mc, "fill -10 199 16 10 199 23 minecraft:snow_block")
            command(mc, "fill 52 188 -12 76 212 12 minecraft:deepslate hollow")
            command(mc, "fill 58 198 -6 70 198 8 minecraft:deepslate")
            command(mc, "setblock 64 199 8 minecraft:deepslate")
            command(mc, "setblock 64 200 8 minecraft:redstone_torch")
        }
        then(3) { mc -> server(mc) {
            val world = mc.singleplayerServer!!.overworld()
            check(world.getBlockState(BlockPos(0, 205, 24)).block == Blocks.SNOW_BLOCK) { "Snow review wall was not built" }
            check(world.getBlockState(BlockPos(64, 205, 12)).block == Blocks.DEEPSLATE) { "Cave review room was not built" }
        } }
        then(8) { mc ->
            mc.options.guiScale().set(4); mc.resizeGui()
            if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle()
        }
        for ((label, share) in listOf("third" to .33f, "full" to 1f)) {
            then(5) { mc -> mc.gui.setScreen(FlaskReviewScreen(share, false, showNumbers = false, referenceBackground = true)) }
            then(3) { mc -> shot(mc, "fluid_art_gs4_$label") }
        }
        then(4) { mc ->
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle()
            mc.gui.setScreen(null)
        }
        for (background in listOf("sky", "snow", "cave")) {
            then(45) { mc ->
                mc.gui.setScreen(null)
                mc.options.guiScale().set(3)
                mc.resizeGui()
                val position = when (background) {
                    "cave" -> "64 200 0"
                    "snow" -> "0 200 21"
                    else -> "0 200 -24"
                }
                command(mc, "tp @a $position 0 ${if (background == "sky") -80 else 0}")
                spirituality(mc, 1f)
                ClientOptions.hudSettings = ClientOptions.hudSettings.copy(flaskBacking = true)
            }
            then(8) { mc -> shot(mc, "review_door_${background}_shadow") }
            then(4) { ClientOptions.hudSettings = ClientOptions.hudSettings.copy(flaskBacking = false) }
            then(8) { mc -> shot(mc, "review_door_${background}_none") }
            then(4) { ClientOptions.hudSettings = ClientOptions.hudSettings.copy(flaskBacking = false) }
            for (scale in listOf(2, 3, 4)) {
                then(8) { mc ->
                    mc.options.guiScale().set(scale); mc.resizeGui()
                    if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle()
                }
                for ((label, share) in listOf("full" to 1f, "third" to .33f, "low" to .1f, "minimum" to .005f, "empty" to 0f)) {
                    then(5) { mc -> mc.gui.setScreen(FlaskReviewScreen(share, false)) }
                    then(3) { mc -> shot(mc, "review_all_${background}_gs${scale}_$label") }
                }
                then(5) { mc -> mc.gui.setScreen(FlaskReviewScreen(1f, true)) }
                then(3) { mc -> shot(mc, "review_all_${background}_gs${scale}_shadow") }
            }
            then(4) { mc ->
                if (mc.gui.hud.isHidden()) mc.gui.hud.toggle()
                mc.gui.setScreen(null)
            }
        }
        then(10) { mc ->
            mc.options.guiScale().set(3); mc.resizeGui()
            mc.gui.setScreen(HudSettingsScreen(AbilityBookScreen()))
        }
        then(3) { mc -> shot(mc, "review_settings_none") }
        then(4) { mc ->
            click(mc, "М’яка тінь")
            check(ClientOptions.hudSettings.flaskBacking)
            ClientOptions.load()
            check(ClientOptions.hudSettings.flaskBacking) { "Flask backing did not survive reload" }
        }
        then(3) { mc -> shot(mc, "review_settings_shadow") }
        then(4) { mc ->
            click(mc, "Немає")
            check(!ClientOptions.hudSettings.flaskBacking)
            ClientOptions.load()
            check(!ClientOptions.hudSettings.flaskBacking) { "Disabled flask backing did not survive reload" }
        }
        then(5) { mc ->
            mc.gui.setScreen(null)
            ClientOptions.hudSettings = saved
            ClientOptions.hud = savedHud
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle()
            mc.options.guiScale().set(originalScale)
            log.info("Flask review passed: 22 pathways, 5 levels, 3 backgrounds, GUI 2/3/4, backing persistence")
            mc.stop()
        }
    }
}
