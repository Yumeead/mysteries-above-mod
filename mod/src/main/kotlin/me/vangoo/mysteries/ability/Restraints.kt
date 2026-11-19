package me.vangoo.mysteries.ability

import me.vangoo.mysteries.modId
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.entity.Relative
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Full restraint: the game itself refuses a bound body its own motion. Not a potion pushed to its highest level, and
 * not a teleport back to the spot every other tick — both of which the plugin had to settle for.
 *
 * While bound, a body:
 * - **does not walk, run, jump or swim of its own accord.** A player's own client sets the movement the keyboard asked
 *   for to nothing (`KeyboardInputMixin`), so there is no rubber-banding; a mob's mind does not run at all
 *   (`MobRestraintMixin`): no steps, no pathfinding, no blows, no techniques. Sneaking stays — crouching moves no-one.
 * - **is not carried off either**: its own way along the ground is held at zero every tick, so a blow does not knock
 *   it out of its bonds. Falling is left to gravity: chains hold, they do not hang anyone in the air.
 * - **uses nothing**: no item in either hand (food, bows, pearls), no block, no entity, no blow, no breaking of blocks.
 * - **calls on no power**: [AbilityExecutor] and the toggles refuse with [CastReport.Restrained].
 *
 * A player whose client does not know the mod (or lies) is set back where they were bound if they get away from it.
 *
 * One registry for every pathway: Brand of Restraint, the Verdict's Imprisonment, Psychic Piercing's stun (Justiciar),
 * the Gatekeeper's bind and the earth spirit's hold (Death), Telepathy (Visionary). Two holds on one body do not add
 * up: the later end wins.
 */
object Restraints {

    /** Synced to the bound player alone: their client must know to refuse its own keyboard. Mobs need no client half. */
    private val BOUND: AttachmentType<Boolean> by lazy {
        AttachmentRegistry.create(modId("restrained")) { it.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()) }
    }

    private class Hold(val body: LivingEntity, var until: Long, val anchor: Vec3)

    private val holds = HashMap<UUID, Hold>()

    fun install() {
        BOUND
        ServerTickEvents.END_SERVER_TICK.register(::enforce)
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, _ -> release(entity) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> release(handler.player) }
        // Both sides: the client refuses before anything is sent, the server whatever arrives anyway.
        UseItemCallback.EVENT.register { player, _, _ -> if (isRestrained(player)) InteractionResult.FAIL else InteractionResult.PASS }
        UseBlockCallback.EVENT.register { player, _, _, _ -> if (isRestrained(player)) InteractionResult.FAIL else InteractionResult.PASS }
        UseEntityCallback.EVENT.register { player, _, _, _, _ -> if (isRestrained(player)) InteractionResult.FAIL else InteractionResult.PASS }
        AttackEntityCallback.EVENT.register { player, _, _, _, _ -> if (isRestrained(player)) InteractionResult.FAIL else InteractionResult.PASS }
        AttackBlockCallback.EVENT.register { player, _, _, _, _ -> if (isRestrained(player)) InteractionResult.FAIL else InteractionResult.PASS }
    }

    /** Binds [body] for [ticks] from now, or longer if it is bound already. */
    fun hold(body: LivingEntity, ticks: Int) {
        val server = body.level().server ?: return
        val until = server.tickCount.toLong() + ticks
        val held = holds[body.uuid]
        if (held != null && held.body === body) {
            held.until = maxOf(held.until, until)
            return
        }
        holds[body.uuid] = Hold(body, until, body.position())
        body.setAttached(BOUND, true)
        // Whatever it was doing with its hands stops: a drawn bow, a meal, a raised shield.
        body.stopUsingItem()
        body.isSprinting = false
        if (body is Mob) body.navigation.stop()
    }

    /** Lets [body] go at once. */
    fun release(body: Entity) {
        holds.remove(body.uuid)
        body.removeAttached(BOUND)
    }

    /** Whether [entity] is bound now; on the client, true only for the local player's own bonds. */
    @JvmStatic
    fun isRestrained(entity: Entity): Boolean = entity.getAttached(BOUND) == true

    /**
     * Called by `MobRestraintMixin` before a mob's mind runs: `true` — it is bound, and its mind is skipped this tick.
     * What the last tick of thought had set going (a step forward, a jump) is stopped too, or the body would keep at it.
     */
    @JvmStatic
    fun holdsMind(mob: Mob): Boolean {
        if (!isRestrained(mob)) return false
        mob.xxa = 0f
        mob.yya = 0f
        mob.zza = 0f
        mob.setJumping(false)
        mob.navigation.stop()
        return true
    }

    private fun enforce(server: MinecraftServer) {
        if (holds.isEmpty()) return
        val now = server.tickCount.toLong()
        val done = ArrayList<LivingEntity>()
        for (hold in holds.values) {
            val body = hold.body
            if (body.isRemoved || !body.isAlive || now >= hold.until) {
                done += body
                continue
            }
            // Held along the ground; gravity keeps its own way.
            val motion = body.deltaMovement
            if (motion.x != 0.0 || motion.z != 0.0) body.setDeltaMovement(0.0, motion.y, 0.0)
            if (body is ServerPlayer) {
                body.isSprinting = false
                val dx = body.x - hold.anchor.x
                val dz = body.z - hold.anchor.z
                if (dx * dx + dz * dz > SLIP * SLIP) {
                    body.connection.teleport(PositionMoveRotation(Vec3(hold.anchor.x, body.y, hold.anchor.z), Vec3.ZERO, 0f, 0f), Relative.ROTATION)
                }
            }
        }
        done.forEach(::release)
    }

    /** How far a bound player may be found from where they were bound before they are set back. */
    private const val SLIP = 0.6
}
