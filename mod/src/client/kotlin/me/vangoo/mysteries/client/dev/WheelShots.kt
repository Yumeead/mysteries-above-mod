package me.vangoo.mysteries.client.dev

import me.vangoo.domain.valueobjects.AbilityIdentity
import me.vangoo.domain.valueobjects.Spirituality
import me.vangoo.mysteries.ability.runtime.AbilityServices
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.HudMode
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.input.ModKeys
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.client.render.WheelLayout
import me.vangoo.mysteries.client.render.WheelSkin
import me.vangoo.mysteries.client.screen.AbilityWheelScreen
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.AbilityActionPayload
import me.vangoo.mysteries.net.payload.AbilityRosterPayload
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import org.slf4j.LoggerFactory

/**
 * Dev-only: walks every wheel skin through the same states at GUI scales 2, 3 and 4, on a day
 * and a night sky, and saves a screenshot of each. Runs only when `MYSTERIES_WHEEL_SHOTS` is set:
 *
 *     MYSTERIES_WHEEL_SHOTS=1 ./gradlew :mod:runClient
 *
 * Needs the single-player world `New World` in `mod/run/saves`; shots land in `mod/run/screenshots`.
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
                mc.createWorldOpenFlows().openWorld("New World") { log.error("Could not open 'New World'") }
                plan()
            }
            return
        }
        if (mc.player == null) return
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
                    then(12) { mc -> spirituality(mc, share) }
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
        then(10) { mc ->
            ClientOptions.wheelSkin = originalSkin
            ClientOptions.hud = originalHud
            mc.options.guiScale().set(originalScale)
            command(mc, "gamemode creative @a")
            log.info("wheel shots done")
            mc.stop()
        }
    }

    private enum class State { NORMAL, HOVER, COOLDOWN, NO_SPIRITUALITY }

    private fun plan() {
        val originalSkin = ClientOptions.wheelSkin
        val originalScale = Minecraft.getInstance().options.guiScale().get()
        // `MYSTERIES_WHEEL_SHOTS=hud` shoots the HUD instead; anything else compares the wheel skins.
        if (System.getenv("MYSTERIES_WHEEL_SHOTS") == "hud") return planHud(originalSkin, originalScale)
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
}
