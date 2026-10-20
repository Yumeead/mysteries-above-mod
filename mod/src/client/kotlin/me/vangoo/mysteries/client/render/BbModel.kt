package me.vangoo.mysteries.client.render

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.animation.AnimationChannel
import net.minecraft.client.animation.AnimationDefinition
import net.minecraft.client.animation.Keyframe
import net.minecraft.client.animation.KeyframeAnimations
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.util.Mth

/**
 * A Blockbench model read as it is: the plugin's four creature models, drawn by vanilla's own model
 * parts and moved by vanilla's own keyframes.
 *
 * The file is the generator's output, untouched (`tools/bbmodel`), so a regenerated model is a copied
 * file and nothing else. Two things in it are beyond vanilla's model builder, and both are why this
 * reads the file instead of a `LayerDefinition`: every face carries **its own** UV rectangle rather than
 * a box-UV offset, and some faces are absent. A vanilla cube is built for the faces that exist and its
 * polygons are then re-made with the file's rectangles — the same `Polygon` constructor the cube itself
 * uses.
 *
 * ## Axes
 *
 * Blockbench is y-up, a model is y-down and mirrored in x — the two differ by a half-turn about z, which
 * the entity renderer undoes. So a point maps to `(-x, 24 - y, z)`, a bone's rest rotation to
 * `(-x, -y, z)`, and the faces swap names along the turned axes (west ↔ east, up ↔ down, the last two
 * with their texture turned round). An animation keyframe needs no conversion at all: Blockbench applies
 * it with x and y already negated, which is exactly what the half-turn asks for.
 */
class BbModel private constructor(val root: ModelPart, val animations: Map<String, Clip>) {

    /** One animation and how it ends: [LOOP] repeats, [ONCE] stops, [HOLD] keeps its last pose. */
    class Clip(val definition: AnimationDefinition, val end: String, val bones: Set<String>) {
        val seconds: Float get() = definition.lengthInSeconds()
    }

    companion object {
        const val LOOP = "loop"
        const val ONCE = "once"
        const val HOLD = "hold"

        fun load(resources: ResourceManager, id: Identifier): BbModel =
            resources.getResourceOrThrow(id).openAsReader().use(::parse)

        fun parse(source: java.io.Reader): BbModel {
            val json = JsonParser.parseReader(source).asJsonObject
            val resolution = json.getAsJsonObject("resolution")
            val reader = Reader(
                json.getAsJsonArray("elements").associateBy({ it.asJsonObject["uuid"].asString }, { it.asJsonObject }),
                resolution["width"].asFloat,
                resolution["height"].asFloat,
            )
            val root = ModelPart(emptyList(), reader.children(json.getAsJsonArray("outliner"), parentOrigin = null))
            val animations = json.getAsJsonArray("animations")?.associate { node ->
                val animation = node.asJsonObject
                animation["name"].asString to clip(animation)
            }.orEmpty()
            return BbModel(root, animations)
        }

        private fun clip(animation: JsonObject): Clip {
            val end = animation["loop"]?.asString ?: ONCE
            val builder = AnimationDefinition.Builder.withLength(animation["length"].asFloat)
            if (end == LOOP) builder.looping()
            val bones = mutableSetOf<String>()
            animation.getAsJsonObject("animators")?.entrySet()?.forEach { (_, value) ->
                val animator = value.asJsonObject
                if (animator["type"]?.asString != "bone") return@forEach
                val bone = animator["name"].asString
                animator.getAsJsonArray("keyframes").map { it.asJsonObject }.groupBy { it["channel"].asString }.forEach { (channel, frames) ->
                    val target = when (channel) {
                        "rotation" -> AnimationChannel.Targets.ROTATION
                        "position" -> AnimationChannel.Targets.POSITION
                        "scale" -> AnimationChannel.Targets.SCALE
                        else -> return@forEach
                    }
                    val keyframes = frames.sortedBy { it["time"].asFloat }.map { frame ->
                        val point = frame.getAsJsonArray("data_points")[0].asJsonObject
                        // ponytail: plain numbers only; a Molang expression reads as 0 until a model uses one.
                        val (x, y, z) = listOf("x", "y", "z").map { point[it]?.asString?.toFloatOrNull() ?: 0f }
                        val value = when (target) {
                            AnimationChannel.Targets.ROTATION -> KeyframeAnimations.degreeVec(x, y, z)
                            AnimationChannel.Targets.POSITION -> KeyframeAnimations.posVec(x, y, z)
                            else -> KeyframeAnimations.scaleVec(x.toDouble(), y.toDouble(), z.toDouble())
                        }
                        val smooth = frame["interpolation"]?.asString == "catmullrom"
                        Keyframe(
                            frame["time"].asFloat,
                            value,
                            if (smooth) AnimationChannel.Interpolations.CATMULLROM else AnimationChannel.Interpolations.LINEAR,
                        )
                    }
                    builder.addAnimation(bone, AnimationChannel(target, *keyframes.toTypedArray()))
                    bones += bone
                }
            }
            return Clip(builder.build(), end, bones)
        }
    }

    private class Reader(val elements: Map<String, JsonObject>, val textureWidth: Float, val textureHeight: Float) {

        /** The bones and loose cubes under one outliner node, as named model parts. */
        fun children(nodes: JsonArray, parentOrigin: FloatArray?): Map<String, ModelPart> {
            val parts = linkedMapOf<String, ModelPart>()
            nodes.filter { it.isJsonObject }.forEach { node ->
                val group = node.asJsonObject
                val origin = group.vector("origin")
                val cubes = mutableListOf<ModelPart.Cube>()
                val children = linkedMapOf<String, ModelPart>()
                group.getAsJsonArray("children").forEach { child ->
                    if (!child.isJsonPrimitive) return@forEach
                    val element = elements[child.asString] ?: return@forEach
                    val rotation = element.vector("rotation")
                    if (rotation.all { it == 0f }) {
                        cubes += cube(element, origin)
                    } else {
                        // A vanilla cube cannot turn on its own: a turned one gets a part of its own,
                        // pivoting where Blockbench pivots it.
                        val pivot = element.vector("origin")
                        val part = ModelPart(listOf(cube(element, pivot)), emptyMap())
                        part.setInitialPose(pose(pivot, origin, rotation))
                        part.resetPose()
                        children["${group["name"].asString}#${element["name"].asString}#${children.size}"] = part
                    }
                }
                children.putAll(children(group.getAsJsonArray("children"), origin))
                val part = ModelPart(cubes, children)
                part.setInitialPose(pose(origin, parentOrigin, group.vector("rotation")))
                part.resetPose()
                parts[group["name"].asString] = part
            }
            return parts
        }

        private fun pose(origin: FloatArray, parent: FloatArray?, rotation: FloatArray): PartPose {
            val (px, py, pz) = parent ?: floatArrayOf(0f, MODEL_FLOOR, 0f)
            return PartPose.offsetAndRotation(
                -(origin[0] - px), -(origin[1] - py), origin[2] - pz,
                -rotation[0] * Mth.DEG_TO_RAD, -rotation[1] * Mth.DEG_TO_RAD, rotation[2] * Mth.DEG_TO_RAD,
            )
        }

        private fun cube(element: JsonObject, pivot: FloatArray): ModelPart.Cube {
            val from = element.vector("from")
            val to = element.vector("to")
            val faces = element.getAsJsonObject("faces").entrySet()
                .filter { (_, face) -> face.asJsonObject["texture"]?.isJsonNull == false }
                .associate { (name, face) -> FACES.getValue(name) to face.asJsonObject.getAsJsonArray("uv").map { it.asFloat } }
            val cube = ModelPart.Cube(
                0, 0,
                pivot[0] - to[0], pivot[1] - to[1], from[2] - pivot[2],
                to[0] - from[0], to[1] - from[1], to[2] - from[2],
                0f, 0f, 0f, false, textureWidth, textureHeight, faces.keys,
            )
            for (i in cube.polygons.indices) {
                val polygon = cube.polygons[i]
                val normal = polygon.normal()
                val direction = Direction.getApproximateNearest(normal.x(), normal.y(), normal.z())
                val (u1, v1, u2, v2) = faces.getValue(direction)
                // Up and down are seen from the other side once the axes turn: the texture turns with them.
                val turned = direction.axis == Direction.Axis.Y
                cube.polygons[i] = if (turned) {
                    ModelPart.Polygon(polygon.vertices(), u2, v2, u1, v1, textureWidth, textureHeight, false, direction)
                } else {
                    ModelPart.Polygon(polygon.vertices(), u1, v1, u2, v2, textureWidth, textureHeight, false, direction)
                }
            }
            return cube
        }

        private fun JsonObject.vector(key: String): FloatArray =
            getAsJsonArray(key)?.map { it.asFloat }?.toFloatArray() ?: FloatArray(3)

        private companion object {
            /** Blockbench's floor sits 24 units below a model's origin, where vanilla puts the feet. */
            const val MODEL_FLOOR = 24f

            val FACES = mapOf(
                "north" to Direction.NORTH,
                "south" to Direction.SOUTH,
                "west" to Direction.EAST,
                "east" to Direction.WEST,
                "up" to Direction.DOWN,
                "down" to Direction.UP,
            )
        }
    }
}
