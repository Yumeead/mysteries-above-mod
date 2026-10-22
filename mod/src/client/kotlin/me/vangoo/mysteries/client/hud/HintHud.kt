package me.vangoo.mysteries.client.hud

import me.vangoo.domain.PathwayBranding
import me.vangoo.mysteries.ability.context.Fizzle
import me.vangoo.mysteries.client.ClientOptions
import me.vangoo.mysteries.client.beyonder.ClientAbilityState
import me.vangoo.mysteries.client.render.UiTheme
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.HintPayload
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A power that did not come: the fizzle at the hand, and — for those who asked — why.
 *
 * The fizzle is the answer the game gives by default: the sparks gather at the hand and gutter
 * out, and on an empty well the flask shakes. It is drawn here, by the caster's own client, for
 * two reasons: only this client knows where its hand is on screen, and nobody else should see a
 * power that never left it.
 *
 * The words are off by default and live under the crosshair for a couple of seconds — where the
 * eyes already are, not in the chat. They exist for learning (some refusals are not obvious: "the
 * target must be asleep") and for accessibility.
 */
object HintHud : HudElement {

    private var text: Component? = null
    private var ticksLeft = 0

    fun register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, modId("hint"), this)
    }

    fun accept(payload: HintPayload) {
        fizzle(payload.fizzle)
        if (payload.fizzle == Fizzle.EMPTY) SpiritualityHud.starve()
        if (ClientOptions.hints) {
            text = payload.message
            ticksLeft = SHOW_TICKS
        }
    }

    fun tick() {
        if (ticksLeft > 0 && --ticksLeft == 0) text = null
    }

    fun clear() {
        text = null
        ticksLeft = 0
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, delta: DeltaTracker) {
        val line = text ?: return
        val client = Minecraft.getInstance()
        if (client.gui.hud.isHidden()) return
        val fade = ((ticksLeft - delta.getGameTimeDeltaPartialTick(false)) / FADE_TICKS).coerceIn(0f, 1f)
        // The font reads an alpha this low as opaque: past it the line is simply gone.
        if (fade < MIN_ALPHA) return
        val font = client.font
        val x = (graphics.guiWidth() - font.width(line)) / 2
        graphics.text(font, line, x, graphics.guiHeight() / 2 + BELOW_CROSSHAIR, UiTheme.argb(UiTheme.MUTED, fade), true)
    }

    private fun fizzle(kind: Fizzle) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val level = client.level ?: return
        when (kind) {
            Fizzle.NONE -> return
            Fizzle.CLICK -> {
                level.playLocalSound(player.x, player.eyeY, player.z, SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS,
                    0.35f, 1.7f, false)
                return
            }
            Fizzle.SPARK, Fizzle.EMPTY -> Unit
        }

        val (hand, right, up) = handOf(player)
        val color = ClientAbilityState.roster?.pathwayId?.let(PathwayBranding::liquidOf) ?: return
        val mote = ModParticles.mote(color)
        // A ring of sparks around the hand, each pulled to the middle: the power gathers — and is not there.
        repeat(SPARKS) { i ->
            val angle = 2 * PI * i / SPARKS
            val out = right.scale(cos(angle)).add(up.scale(sin(angle)))
            val at = hand.add(out.scale(SPARK_RING))
            level.addParticle(mote, at.x, at.y, at.z, -out.x * SPARK_PULL, -out.y * SPARK_PULL, -out.z * SPARK_PULL)
        }
        // A candle going out — lower when the well is dry. No smoke: a step from the eyes the
        // vanilla puff is a black blot, not a wisp.
        level.playLocalSound(hand.x, hand.y, hand.z, SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS,
            0.8f, if (kind == Fizzle.EMPTY) 0.7f else 1.3f, false)
    }

    /**
     * Where the casting hand is, in the world, plus the screen's right and up there.
     *
     * The right is taken from the yaw alone: a cross product with the look would vanish when the
     * player looks straight up or down.
     */
    private fun handOf(player: LocalPlayer): Triple<Vec3, Vec3, Vec3> {
        val forward = player.getViewVector(1f)
        val yaw = Math.toRadians(player.yRot.toDouble())
        val right = Vec3(-cos(yaw), 0.0, -sin(yaw))
        val up = right.cross(forward)
        val side = if (player.mainArm == HumanoidArm.RIGHT) 1.0 else -1.0
        val hand = player.getEyePosition(1f)
            .add(forward.scale(HAND_FORWARD))
            .add(right.scale(HAND_SIDE * side))
            .subtract(up.scale(HAND_DOWN))
        return Triple(hand, right, up)
    }

    private const val SHOW_TICKS = 50
    private const val FADE_TICKS = 15f
    private const val MIN_ALPHA = 0.05f
    private const val BELOW_CROSSHAIR = 14

    private const val SPARKS = 7
    private const val SPARK_RING = 0.2
    private const val SPARK_PULL = 0.03

    /**
     * A step ahead of the eyes, low and to the side of the casting arm. Closer than this, a mote is
     * nearer than the camera can render sharply and swells into a blot.
     */
    private const val HAND_FORWARD = 1.1
    private const val HAND_SIDE = 0.4
    private const val HAND_DOWN = 0.35
}
