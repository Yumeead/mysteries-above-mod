package me.vangoo.mysteries.gametest

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.Sheets
import net.minecraft.client.renderer.block.BlockModelRenderState
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.RandomSource
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.IronBarsBlock
import net.minecraft.world.phys.Vec3

/**
 * Step 0 of the Justiciar effects plan (artifact 3MmcvvuFaZxwwZzWxdRpUv, §8): can the pathway's iron be
 * vanilla iron bars, drawn two ways, with and without a shader pack?
 *
 * 1. Imprisonment's cell: eight `block_display`s of bars round a villager's 1×1×2 cell, dropping from
 *    above in 3 ticks by vanilla transformation interpolation. No client code at all.
 * 2. A boundary's bars: the bars' block model submitted by the client itself in `COLLECT_SUBMITS`, the
 *    way `Precinct` would draw them, in columns 1, 2 and 3 high: the ratchet's three steps side by side.
 * 3. Two square bonds on the villager, hot and cold: `lightning()` and `textBackground()` lit by the
 *    place, the two ways `Edict` would draw its lines.
 *
 * The answer, on 16.11.2026: yes to all three, with Iris 1.11.4 and Complementary Reimagined r5.7.1 too.
 *
 * The screenshots land in `mod/build/run/clientGameTest/screenshots`. For the shader pack run, see
 * `.claude/memory/light-shaft-beam.md` ("Iris — тимчасово").
 */
@Suppress("UnstableApiUsage")
class JusticiarProbeClientGameTest : FabricClientGameTest {

    override fun runTest(context: ClientGameTestContext) {
        if (skipped()) return
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val server = world.server
            server.runCommand("gamerule doDaylightCycle false")
            server.runCommand("weather clear")
            server.runCommand("time set noon")
            server.runCommand("gamemode creative @a")
            server.runCommand("fill ~-8 ~-1 ~-4 ~8 ~-1 ~24 minecraft:stone_bricks")
            // The console stands on the spawn block's corner, so `~` is the grid itself.
            server.runCommand("tp @a ~0.5 ~ ~0.5 0 10")
            server.runCommand("summon minecraft:villager ~0.5 ~ ~4.5 {NoAI:1b,Rotation:[180f,0f]}")
            context.waitTicks(20)
            context.runOnClient<Throwable> { Bars.show(it.player!!.blockPosition().offset(0, 0, BARS_AHEAD)) }

            // Two panes a side, each on the cell's edge rather than in its own block, held above the head.
            for (y in 0..1) {
                cell(server, "~ ~$y ~3.5", along = "east:\"true\",west:\"true\"")
                cell(server, "~ ~$y ~4.5", along = "east:\"true\",west:\"true\"")
                cell(server, "~-0.5 ~$y ~4", along = "north:\"true\",south:\"true\"")
                cell(server, "~0.5 ~$y ~4", along = "north:\"true\",south:\"true\"")
            }
            context.waitTicks(10)
            context.takeScreenshot("justiciar-probe-cell-held")
            // The drop must reach the client after the spawn did: changed in the same tick, there is
            // nothing to interpolate from and the cell simply stands where it ends.
            server.runCommand("execute as @e[tag=$TAG] run data merge entity @s {transformation:${transform(0f)},start_interpolation:0,interpolation_duration:$DROP_TICKS}")
            for (tick in 1..DROP_TICKS + 1) {
                context.waitTicks(1)
                context.takeScreenshot("justiciar-probe-cell-falling-$tick")
            }
            context.waitTicks(2)
            context.takeScreenshot("justiciar-probe-cell-landed")

            // Up to the boundary's bars, two blocks off them.
            server.runCommand("tp @a ~0.5 ~ ~5.5 0 15")
            context.waitTicks(10)
            context.takeScreenshot("justiciar-probe-bars-close")

            server.runCommand("time set midnight")
            context.waitTicks(10)
            context.takeScreenshot("justiciar-probe-bars-night")
            // Back to the cell: the gold bond is light and keeps its colour, the iron one darkens with the place.
            server.runCommand("tp @a ~0.5 ~ ~0.5 0 10")
            context.waitTicks(10)
            context.takeScreenshot("justiciar-probe-cell-night")
            server.runCommand("kill @e[tag=$TAG]")
            context.runOnClient<Throwable> { Bars.show(null) }
        }
    }

    private fun cell(server: TestServerContext, at: String, along: String) =
        server.runCommand("summon minecraft:block_display $at {block_state:{Name:\"minecraft:iron_bars\",Properties:{$along}},transformation:${transform(DROP_FROM)},Tags:[\"$TAG\"]}")

    private fun transform(lift: Float) =
        "{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[1f,1f,1f],translation:[0f,${lift}f,0f]}"

    /**
     * A row of bars drawn by the client, columns 1, 2 and 3 high, three of each. A submitted model is
     * not culled against its neighbours the way placed blocks are, so the faces that meet a neighbouring
     * bar are left out here: drawn, they show as a thin seam along every joint.
     */
    private object Bars {
        private var origin: BlockPos? = null
        private var registered = false
        private val parts by lazy {
            val state = Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true)
            mutableListOf<BlockStateModelPart>().also {
                Minecraft.getInstance().modelManager.blockStateModelSet.get(state).collectParts(RandomSource.create(0), it)
            }
        }

        fun show(at: BlockPos?) {
            origin = at
            if (registered) return
            registered = true
            LevelRenderEvents.COLLECT_SUBMITS.register(::submit)
        }

        private fun height(column: Int) = if (column in 0 until COLUMNS) column / 3 + 1 else 0

        private fun submit(context: LevelRenderContext) {
            val at = origin ?: return
            val level = Minecraft.getInstance().level ?: return
            val camera = context.levelState().cameraRenderState.pos
            val poses = context.poseStack()
            for (column in 0 until COLUMNS) for (y in 0 until height(column)) {
                val hidden = buildSet {
                    if (y + 1 < height(column)) add(Direction.UP)
                    if (y > 0) add(Direction.DOWN)
                    if (y < height(column + 1)) add(Direction.EAST)
                    if (y < height(column - 1)) add(Direction.WEST)
                }
                val shown = parts.map { part ->
                    object : BlockStateModelPart by part {
                        override fun getQuads(side: Direction?) = if (side in hidden) emptyList() else part.getQuads(side)
                    }
                }
                val pos = at.offset(column - COLUMNS / 2, y, 0)
                poses.pushPose()
                poses.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z)
                context.submitNodeCollector().submitBlockModel(
                    poses, Sheets.cutoutBlockItemSheet(), shown, BlockModelRenderState.EMPTY_TINTS,
                    LightCoordsUtil.getLightCoords(level, pos), OverlayTexture.NO_OVERLAY, 0,
                )
                poses.popPose()
            }

            // Two square bonds on the villager: hot gold as light, cold iron as plain translucency lit by
            // the place, the two ways the pathway's lines are drawn.
            val centre = Vec3.atBottomCenterOf(at.offset(0, 0, -BARS_AHEAD + CELL_AHEAD))
            val chest = centre.add(0.0, 1.35, 0.0)
            val waist = centre.add(0.0, 0.8, 0.0)
            val light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(waist))
            context.submitNodeCollector().submitCustomGeometry(poses, RenderTypes.lightning()) { pose, buffer -> ring(pose, buffer, camera, chest, GOLD, null) }
            context.submitNodeCollector().submitCustomGeometry(poses, RenderTypes.textBackground()) { pose, buffer -> ring(pose, buffer, camera, waist, IRON, light) }
        }

        private fun ring(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, at: Vec3, argb: Int, light: Int?) {
            val corners = listOf(Vec3(-RING, 0.0, -RING), Vec3(RING, 0.0, -RING), Vec3(RING, 0.0, RING), Vec3(-RING, 0.0, RING)).map(at::add)
            for (i in corners.indices) band(pose, buffer, camera, corners[i], corners[(i + 1) % corners.size], argb, light)
        }

        /** One side of a ring: a band two pixels tall, wound both ways so it shows from either side. */
        private fun band(pose: PoseStack.Pose, buffer: VertexConsumer, camera: Vec3, from: Vec3, to: Vec3, argb: Int, light: Int?) {
            val up = Vec3(0.0, BAND, 0.0)
            val quad = listOf(from, to, to.add(up), from.add(up))
            for (corners in listOf(quad, quad.reversed())) for (p in corners) {
                val vertex = buffer.addVertex(pose, (p.x - camera.x).toFloat(), (p.y - camera.y).toFloat(), (p.z - camera.z).toFloat()).setColor(argb)
                if (light != null) vertex.setLight(light)
            }
        }

        private const val COLUMNS = 9
        private const val RING = 0.42
        private const val BAND = 2.0 / 16
        private const val GOLD = 0xFFEBC38E.toInt()
        private const val IRON = 0xFFB4B9C1.toInt()
    }

    private companion object {
        const val TAG = "justiciar_probe"
        const val DROP_TICKS = 3
        const val DROP_FROM = 3f
        const val BARS_AHEAD = 7
        const val CELL_AHEAD = 4
    }
}
