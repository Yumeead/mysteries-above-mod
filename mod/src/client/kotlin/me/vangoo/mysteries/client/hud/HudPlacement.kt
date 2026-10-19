package me.vangoo.mysteries.client.hud

import me.vangoo.domain.hud.HudLayout
import me.vangoo.domain.hud.HudPart
import me.vangoo.domain.hud.HudPosition
import me.vangoo.domain.hud.HudRect
import me.vangoo.domain.hud.HudSettings
import me.vangoo.domain.hud.SpiritualityMode
import me.vangoo.domain.hud.BottomPanel
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.HudAnchor
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.player.Player
import kotlin.math.roundToInt

data class HudBox(val rect: HudRect, val scale: Float)

object HudPlacement {
    fun obstacles(width: Int, height: Int): List<HudRect> {
        if (!BottomHud.enabled) return listOf(vanilla(width, height))
        val client = Minecraft.getInstance()
        val c = width / 2
        val top = maxOf(HudStatusBarHeightRegistry.getHeight(VanillaHudElements.ARMOR_BAR),
            HudStatusBarHeightRegistry.getHeight(VanillaHudElements.AIR_BAR)) + 9
        return buildList {
            add(HudRect(c-91, height-top, 182, top))
            val left = client.player?.mainArm == HumanoidArm.LEFT
            if (client.player?.offhandItem?.isEmpty == false) add(HudRect(c + if (left) 99 else -119, height-21,20,20))
            add(HudRect(c + if (left) -113 else 97, height-20,18,18))
            panelBoxes(width,height,true,emptyMap()).filterKeys { it == HudPart.BAR }
                .values.forEach { add(it.rect) }
        }
    }

    fun vanilla(screenWidth: Int, screenHeight: Int): HudRect {
        val client = Minecraft.getInstance()
        val top = if (client.cameraEntity is Player && client.gameMode?.canHurtPlayer() == true) maxOf(74,
            HudStatusBarHeightRegistry.getHeight(VanillaHudElements.ARMOR_BAR) + 12,
            HudStatusBarHeightRegistry.getHeight(VanillaHudElements.AIR_BAR) + 12,
            HudStatusBarHeightRegistry.getHeight(VanillaHudElements.MOUNT_HEALTH) + 12) else 32
        // Reserve both side attachments even while the attack indicator is temporarily full.
        return HudRect(screenWidth / 2 - 122, (screenHeight - top).coerceAtLeast(0), 244, minOf(top, screenHeight))
    }

    fun boxes(screenWidth: Int, screenHeight: Int, preview: Boolean = false,
              overrides: Map<HudPart, HudPosition> = emptyMap(),
              scaleOverrides: Map<HudPart, Float> = emptyMap()): Map<HudPart, HudBox> {
        if (BottomHud.enabled) return panelBoxes(screenWidth, screenHeight, preview, overrides)
        val sizes = linkedMapOf<HudPart, Pair<Int, Int>>()
        SpiritualityHud.part(preview)?.let { part -> SpiritualityHud.dimensions(preview)?.let { sizes[part] = it } }
        AbilityHud.dimensions(preview)?.let { sizes[HudPart.ABILITY] = it }
        MarionetteHud.dimensions(screenWidth, screenHeight, preview)?.let { sizes[HudPart.MARIONETTES] = it }
        val saved = ClientOptions.hudSettings.positions + overrides
        val boxes = sizes.mapValues { (part, size) ->
            val partScale = scaleOverrides[part]?.let(HudSettings::validScale) ?: ClientOptions.hudSettings.scale(part)
            val scale = minOf(ClientOptions.hudScale * partScale, (screenWidth - 8).coerceAtLeast(1).toFloat() / size.first,
                (screenHeight - 8).coerceAtLeast(1).toFloat() / size.second)
            HudBox(HudRect(0, 0, (size.first * scale).roundToInt(), (size.second * scale).roundToInt()), scale)
        }.toMutableMap()
        val occupied = mutableListOf(vanilla(screenWidth, screenHeight))
        for ((part, box) in boxes.toMap()) saved[part]?.let {
            val rect = it.resolve(box.rect.width, box.rect.height, screenWidth, screenHeight)
            boxes[part] = box.copy(rect = rect)
            occupied += rect
        }
        for ((part, box) in boxes.toMap()) {
            if (part in saved) continue
            val w = box.rect.width
            val h = box.rect.height
            val default = when (part) {
                HudPart.FLASK -> {
                    val leftHanded = Minecraft.getInstance().player?.mainArm == HumanoidArm.LEFT
                    val x = if (leftHanded) screenWidth / 2 - 122 - w - 4 else screenWidth / 2 + 126
                    if (x < 0 || x + w > screenWidth) HudRect(if (leftHanded) 4 else screenWidth - w - 4,
                        vanilla(screenWidth, screenHeight).y - h - 4, w, h)
                    else HudRect(x, screenHeight - h - 4, w, h)
                }
                HudPart.BAR -> HudRect((screenWidth - w) / 2, vanilla(screenWidth, screenHeight).y - h - 4, w, h)
                HudPart.ABILITY -> {
                    val right = ClientOptions.anchor in listOf(HudAnchor.RIGHT_CENTER, HudAnchor.RIGHT_TOP)
                    val top = ClientOptions.anchor in listOf(HudAnchor.LEFT_TOP, HudAnchor.RIGHT_TOP)
                    HudRect(if (right) screenWidth - w - 6 else 6, if (top) 6 else (screenHeight - h) / 2, w, h)
                }
                HudPart.MARIONETTES -> HudRect((screenWidth - w) / 2,
                    vanilla(screenWidth, screenHeight).y - h - 8 -
                        (if (ClientOptions.hudSettings.spirituality == SpiritualityMode.BAR) 24 else 0), w, h)
                HudPart.AUXILIARY -> BottomPanel.auxiliary(screenWidth, screenHeight)
            }
            val rect = HudLayout.avoid(default, screenWidth, screenHeight, occupied)
            boxes[part] = box.copy(rect = rect)
            occupied += rect
        }
        return boxes
    }

    private fun panelBoxes(width: Int, height: Int, preview: Boolean,
                           overrides: Map<HudPart, HudPosition>): Map<HudPart, HudBox> {
        val client = Minecraft.getInstance()
        val rise = (HudStatusBarHeightRegistry.getHeight(VanillaHudElements.ARMOR_BAR) - 49).coerceAtLeast(0)
        val armor = (client.player?.armorValue ?: 0) > 0 && client.gameMode?.canHurtPlayer() == true
        val saved = ClientOptions.hudSettings.panelPositions + overrides
        val rects = linkedMapOf<HudPart, HudRect>()
        SpiritualityHud.part(preview)?.let { part ->
            rects[part] = if (part == HudPart.FLASK) BottomPanel.orb(width, height, rise, armor)
                else HudRect(width / 2 - 91, height - maxOf(
                    HudStatusBarHeightRegistry.getHeight(VanillaHudElements.ARMOR_BAR),
                    HudStatusBarHeightRegistry.getHeight(VanillaHudElements.AIR_BAR)) - 20, 182, 20).clamped(width, height)
        }
        if (preview || me.vangoo.mysteries.client.beyonder.ClientAbilityState.isBeyonder) {
            rects[HudPart.ABILITY] = BottomPanel.ability(width, height, client.player?.mainArm == HumanoidArm.LEFT)
            rects[HudPart.AUXILIARY] = BottomPanel.auxiliary(width, height)
        }
        MarionetteHud.dimensions(width, height, preview)?.let { (w,h) ->
            val top = rects.values.minOfOrNull { it.y } ?: height - 70
            rects[HudPart.MARIONETTES] = HudRect((width-w)/2, top-h-8, w, h).clamped(width,height)
        }
        return rects.mapValues { (part, default) ->
            val rect = if (part == HudPart.BAR) default
                else saved[part]?.resolve(default.width,default.height,width,height) ?: default
            HudBox(rect, 1f)
        }
    }
}
