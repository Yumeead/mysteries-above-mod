package me.vangoo.mysteries.client.render

import com.mojang.blaze3d.vertex.PoseStack
import me.vangoo.mysteries.modId
import me.vangoo.mysteries.net.payload.CreatureMovePayload
import net.minecraft.client.Minecraft
import net.minecraft.client.animation.KeyframeAnimation
import net.minecraft.client.model.EntityModel
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.AABB

/**
 * Draws a creature body with a model of its own, read from `assets/mysteries_above/bbmodel/<name>.bbmodel`.
 *
 * The body under it is a vanilla mob; only the picture changes. What the plugin's BetterModel played by
 * itself is played here from the state vanilla already sends: `idle` always, `walk` while moving, `spawn`
 * when the body first appears, `damage` on a hit, `death` while dying, `attack` on a swing. The rest —
 * the casts — are named by the server per technique ([CreatureMoves]).
 *
 * @param size the model's own scale on top of the body's — the siren was drawn small and shown at 1.6
 */
class BbModelRenderer<T : Mob>(context: EntityRendererProvider.Context, name: String, private val size: Float = 1f) :
    MobRenderer<T, BbModelRenderer.State, BbModelRenderer.BbEntityModel>(
        context,
        BbEntityModel(BbModel.load(context.resourceManager, modId("bbmodel/$name.bbmodel"))),
        SHADOW,
    ) {

    private val texture = modId("textures/entity/$name.png")

    class State : LivingEntityRenderState() {
        var move: String? = null
        var moveSeconds = 0f
        var hurtSeconds = -1f
    }

    override fun createRenderState(): State = State()

    override fun getTextureLocation(state: State): Identifier = texture

    override fun scale(state: State, poseStack: PoseStack) {
        poseStack.scale(size, size, size)
    }

    /** A model that knows how to die does not also topple over the vanilla way. */
    override fun getFlipDegrees(): Float = if (model.clips.containsKey(DEATH)) 0f else super.getFlipDegrees()

    /** Wings and robes reach past the body's box; culling by the box alone would pop them at the screen's edge. */
    override fun getBoundingBoxForCulling(entity: T): AABB = super.getBoundingBoxForCulling(entity).inflate(CULL_MARGIN * size)

    override fun extractRenderState(entity: T, state: State, partialTick: Float) {
        super.extractRenderState(entity, state, partialTick)
        // A blow with the body: vanilla's swing is the one cue every melee goal gives.
        if (entity.swinging && model.clips.containsKey(ATTACK)) CreatureMoves.start(entity.id, ATTACK, entity.tickCount - entity.swingTime)

        val now = entity.tickCount + partialTick
        val move = CreatureMoves.of(entity.id)
        val clip = move?.let { model.clips[it.name] }
        if (move != null && clip != null && (now - move.startTick) / TICKS <= clip.seconds) {
            state.move = move.name
            state.moveSeconds = (now - move.startTick) / TICKS
        } else {
            if (move != null) CreatureMoves.finish(entity.id)
            val spawn = model.clips[SPAWN]
            state.move = if (spawn != null && now / TICKS <= spawn.seconds) SPAWN else null
            state.moveSeconds = now / TICKS
        }
        state.hurtSeconds = if (entity.hurtTime > 0) (entity.hurtDuration - entity.hurtTime + partialTick) / TICKS else -1f
    }

    /**
     * The model and its layers: a base (`idle`, or `walk` while moving) and, over it, at most one
     * gesture — death, then a move, then a flinch. A layer above takes over every bone it animates and
     * leaves the rest to the one below, which is how BetterModel stacked them: the idle breathing still
     * shows in whatever a cast does not move.
     */
    class BbEntityModel(model: BbModel) : EntityModel<State>(model.root, RenderTypes::entityTranslucent) {

        val clips = model.animations
        private val baked: Map<String, KeyframeAnimation> = clips.mapValues { (_, clip) -> clip.definition.bake(root) }
        private val part = root.createPartLookup()
        // BetterModel's naming: a bone called `h_…`/`hi_…` follows where the head looks.
        private val lookers = clips.values.flatMap { it.bones }.distinct()
            .filter { it.startsWith("h_") || it.startsWith("hi_") }.mapNotNull { part.apply(it) }

        override fun setupAnim(state: State) {
            super.setupAnim(state)
            baked[IDLE]?.apply(millis(state.ageInTicks / TICKS), 1f)
            if (state.walkAnimationSpeed > WALKING) {
                over(WALK) { it.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, WALK_TIME, WALK_SPEED) }
            }
            when {
                state.deathTime > 0 && DEATH in baked -> over(DEATH) { it.apply(millis(state.deathTime / TICKS), 1f) }
                state.move != null -> over(state.move!!) { it.apply(millis(state.moveSeconds), 1f) }
                state.hurtSeconds >= 0 && state.hurtSeconds <= (clips[DAMAGE]?.seconds ?: 0f) ->
                    over(DAMAGE) { it.apply(millis(state.hurtSeconds), 1f) }
            }
            lookers.forEach { head ->
                head.yRot += state.yRot * Mth.DEG_TO_RAD
                head.xRot += state.xRot * Mth.DEG_TO_RAD
            }
        }

        /** Hands every bone [name] animates over to it: back to rest first, then its own pose. */
        private inline fun over(name: String, play: (KeyframeAnimation) -> Unit) {
            val animation = baked[name] ?: return
            clips.getValue(name).bones.forEach { part.apply(it)?.resetPose() }
            play(animation)
        }

        private fun millis(seconds: Float): Long = (seconds * 1000).toLong()
    }

    private companion object {
        const val SHADOW = 0.5f
        const val CULL_MARGIN = 1.5
        const val TICKS = 20f
        const val WALKING = 0.05f
        const val WALK_TIME = 2f
        const val WALK_SPEED = 2.5f

        const val IDLE = "idle"
        const val WALK = "walk"
        const val SPAWN = "spawn"
        const val ATTACK = "attack"
        const val DAMAGE = "damage"
        const val DEATH = "death"
    }
}

/**
 * The moves the server named, keyed by entity id: a creature's technique plays its own animation, the
 * plugin's `state{s=cast_bolt}` line. Only bodies with a model keep one — for the rest the packet is
 * dropped on arrival, the same no-op the plugin's line was on a mob without a model.
 */
object CreatureMoves {

    class Move(val name: String, val startTick: Int)

    private val playing = HashMap<Int, Move>()

    fun accept(payload: CreatureMovePayload) {
        val minecraft = Minecraft.getInstance()
        val entity = minecraft.level?.getEntity(payload.entity) ?: return
        if (minecraft.entityRenderDispatcher.getRenderer(entity) !is BbModelRenderer<*>) return
        playing[payload.entity] = Move(payload.move, entity.tickCount)
    }

    /** A move that began at [startTick], unless the one already playing began later. */
    fun start(entity: Int, name: String, startTick: Int) {
        val current = playing[entity]
        if (current == null || current.startTick < startTick) playing[entity] = Move(name, startTick)
    }

    fun of(entity: Int): Move? = playing[entity]

    fun finish(entity: Int) {
        playing.remove(entity)
    }

    fun clear() = playing.clear()
}
