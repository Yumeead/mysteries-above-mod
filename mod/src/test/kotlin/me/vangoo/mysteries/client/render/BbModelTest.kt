package me.vangoo.mysteries.client.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The four shipped models read through the same parser the renderer uses. A bone an animation names
 * but the model lacks would bake to nothing and the animation would silently not move it.
 */
class BbModelTest {

    private fun model(name: String): BbModel =
        javaClass.getResourceAsStream("/assets/mysteries_above/bbmodel/$name.bbmodel")!!.reader().use(BbModel::parse)

    @Test
    fun everyAnimatedBoneExists() {
        for (name in listOf("sea_condor", "tyrant_siren_5", "fool_ancient_wraith_5", "fool_thousand_faced_hunter_6")) {
            val model = model(name)
            val part = model.root.createPartLookup()
            assertTrue("idle" in model.animations, "$name: no idle")
            model.animations.forEach { (animation, clip) ->
                clip.bones.forEach { bone -> assertNotNull(part.apply(bone), "$name/$animation: no bone $bone") }
            }
        }
    }

    /** Blockbench's y-up origin 9.5 above the floor is 24 − 9.5 below a model's origin, x mirrored. */
    @Test
    fun bonesLandWhereTheModelPutsThem() {
        val wing = model("sea_condor").root.createPartLookup().apply("wing_left")!!
        val body = model("sea_condor").root.getChild("body")
        assertEquals(24f - 9.5f, body.y, 1e-4f)
        // wing_left sits at x = 3, y = 12.5, z = -2 in Blockbench, i.e. (-3, -(12.5 - 9.5), -2) from the body.
        assertEquals(-3f, wing.x, 1e-4f)
        assertEquals(-3f, wing.y, 1e-4f)
        assertEquals(-2f, wing.z, 1e-4f)
    }
}
