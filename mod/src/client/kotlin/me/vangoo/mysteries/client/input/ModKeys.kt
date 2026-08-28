package me.vangoo.mysteries.client.input

import com.mojang.blaze3d.platform.InputConstants
import me.vangoo.mysteries.MOD_ID
import me.vangoo.mysteries.client.net.sendToServer
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.ModChannels
import me.vangoo.mysteries.net.payload.CastRequestPayload
import me.vangoo.mysteries.net.payload.StatusRequestPayload
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW

/**
 * Клавіші мода.
 *
 * Одна клавіша, дві дії: **натиснув і відпустив — каст**, **затиснув — меню**. Так само
 * поводиться ванільний інвентар мобільної збірки й половина модів із колесом здібностей,
 * і причина та сама: у бою рука не має шукати другу клавішу, а меню в бою не потрібне.
 *
 * ## Чому не «каст на R, меню на клавішу X»
 *
 * Так і було, і саме з цього виросла вада: меню відкривалось **разом** із кастом. Не
 * через клавішу — через те, що серверний шлях касту слав `BEYONDER_STATUS`, а клієнт на
 * цей пакет відкриває екран. Клавіші тут лише половина виправлення; друга половина в
 * `AbilityRequests`, який більше не шле знімок статусу після кожного удару.
 *
 * ## Рішення ухвалюється на відпусканні, а не на натисканні
 *
 * Інакше «затиснув» неможливо відрізнити від «натиснув»: у мить натискання ще невідомо,
 * чим воно стане. Тому каст іде на відпусканні, якщо клавішу тримали менше [HOLD_TICKS]
 * тіків — чверть секунди, тобто менше, ніж людина утримує навмисно, і більше, ніж триває
 * звичайний клац.
 *
 * На 26.2 категорія клавіш — не рядок, а `KeyMapping.Category` зі своїм `Identifier`;
 * її підпис береться з ключа `key.category.<namespace>.<path>`.
 */
object ModKeys {

    private val CATEGORY = KeyMapping.Category.register(modId("abilities"))

    private val CAST: KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping("key.$MOD_ID.cast", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY),
    )

    /** Скільки тіків клавіша вже втиснута. Нуль — відпущена. */
    private var heldTicks = 0

    /** Меню вже відкрито цим утриманням: відпускання після нього не має кастувати. */
    private var opened = false

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register {
            // Чергу кліків треба вичерпувати, інакше вона копичиться й одного разу
            // вихлюпне пачку кастів. Саме рішення ухвалюється по [heldTicks], не по ній.
            @Suppress("ControlFlowWithEmptyBody")
            while (CAST.consumeClick()) { }

            if (CAST.isDown) {
                heldTicks++
                if (heldTicks >= HOLD_TICKS && !opened) {
                    opened = true
                    // Екран відкриває сервер у відповідь: тільки він знає ростер.
                    ModChannels.STATUS_REQUEST.sendToServer(StatusRequestPayload)
                }
                return@register
            }

            // Клавіша відпущена. Каст — лише якщо це був клац, а не утримання.
            if (heldTicks in 1 until HOLD_TICKS && !opened) {
                ModChannels.CAST_REQUEST.sendToServer(CastRequestPayload.INSTANCE)
            }
            heldTicks = 0
            opened = false
        }
    }

    /** Чверть секунди: довше за клац, коротше за навмисне утримання. */
    private const val HOLD_TICKS = 5
}
