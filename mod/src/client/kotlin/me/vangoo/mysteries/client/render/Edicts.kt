package me.vangoo.mysteries.client.render

import me.vangoo.domain.valueobjects.RgbColor
import me.vangoo.mysteries.effect.ModParticles
import me.vangoo.mysteries.net.payload.EdictKind
import me.vangoo.mysteries.net.payload.WorldEffectPayload
import me.vangoo.mysteries.net.payload.WorldEffectPayload.Edict
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft

/**
 * The client half of `pathways/justiciar/Edict.kt`. Bonds and the crown are [Bonds], the Verdict's sentences
 * [Verdicts]. What is left here is
 * Authority's aura (`SEAL`): who carries one and how old it is, for [Auras] to draw round the body, and the few
 * motes that rise off the shoulders while it burns.
 */
object Edicts {

    /** An aura on [entity], [age] ticks old, in the pathway's colour. */
    class Aura(val entity: Int, val rgb: Int, val ticks: Int) {
        var age = 0
            internal set

        /** How the fire hangs off the moving body: [Auras.follow] ticks it. */
        internal val motion = Auras.Motion()
    }

    private val auras = mutableListOf<Aura>()

    fun accept(payload: WorldEffectPayload.Edict) {
        when (payload.kind) {
            EdictKind.BONDS, EdictKind.CROWN -> return Bonds.accept(payload)
            EdictKind.EXILE, EdictKind.IMPRISON, EdictKind.CONFINE -> return Verdicts.accept(payload)
            EdictKind.SEAL -> Unit
        }
        auras.removeAll { it.entity == payload.entity }
        auras += Aura(payload.entity, payload.color and 0xFFFFFF, payload.ticks)
    }

    /** The aura [entity] carries, if any. */
    fun aura(entity: Int): Aura? = auras.firstOrNull { it.entity == entity }

    fun tick() {
        val level = Minecraft.getInstance().level
        auras.removeAll { aura ->
            aura.age++
            if (level != null) level.getEntity(aura.entity)?.let { body ->
                Auras.follow(aura, body.position())
                motes(level, body, aura)
            }
            aura.age >= aura.ticks
        }
        Bonds.tick()
        Verdicts.tick()
    }

    fun clear() {
        auras.clear()
        Bonds.clear()
        Verdicts.clear()
    }

    fun submit(context: LevelRenderContext) {
        Auras.submit(context, auras)
        Bonds.submit(context)
        Verdicts.submit(context)
    }

    /**
     * A mote off the head and shoulders now and then, more as the aura rises; a handful at each knock and at the
     * blow. Sparse on purpose: the aura is the body's own light, not a cloud round it.
     */
    private fun motes(level: net.minecraft.client.multiplayer.ClientLevel, body: net.minecraft.world.entity.Entity, aura: Aura) {
        val random = level.random
        val age = aura.age
        val knock = age < Edict.SEAL_TICKS && age % Edict.SEAL_KNOCK_TICKS == 1
        val blow = age == Edict.SEAL_TICKS + 1
        val count = when {
            blow -> BLOW_MOTES
            knock -> KNOCK_MOTES
            age < Edict.SEAL_TICKS && random.nextInt(MOTE_EVERY) < 1 + age / Edict.SEAL_KNOCK_TICKS -> 1
            else -> 0
        }
        val option = ModParticles.mote(RgbColor(aura.rgb))
        repeat(count) {
            val x = body.x + (random.nextDouble() - 0.5) * body.bbWidth * 1.4
            val y = body.y + body.bbHeight * (0.55 + random.nextDouble() * 0.5)
            val z = body.z + (random.nextDouble() - 0.5) * body.bbWidth * 1.4
            val lift = if (blow) 0.08 + random.nextDouble() * 0.06 else 0.02 + random.nextDouble() * 0.02
            level.addParticle(option, x, y, z, 0.0, lift, 0.0)
        }
    }

    private const val MOTE_EVERY = 8
    private const val KNOCK_MOTES = 5
    private const val BLOW_MOTES = 14
}
