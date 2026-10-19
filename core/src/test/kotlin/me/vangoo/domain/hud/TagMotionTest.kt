package me.vangoo.domain.hud

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sin

class TagMotionTest {
    private fun turn(degreesPerTick: Float): TagMotion = TagMotion().also { motion ->
        motion.tick(0f)
        repeat(12) { motion.tick((it + 1) * degreesPerTick) }
    }

    @Test fun cameraTurnsMoveTheHangingEndOppositeTheViewAndFasterTurnsDeflectMore() {
        val slow = turn(2f).angle(1f)
        val fast = turn(8f).angle(1f)
        val left = turn(-8f).angle(1f)
        assertTrue(-sin(slow) < 0f)
        assertTrue(abs(fast) > abs(slow) * 2)
        assertEquals(-fast, left, .00001f)
        assertTrue(sin(-left) > 0f)
    }

    @Test fun stoppingTheCameraAllowsTheOrnamentToSettleInsteadOfContinuingToOscillate() {
        val motion = turn(8f)
        val start = abs(motion.angle(1f))
        repeat(60) { motion.tick(96f) }
        assertTrue(abs(motion.angle(1f)) < .00001f)
        assertTrue(start > .01f)
    }

    @Test fun interpolationIsContinuousAndWrappingYawDoesNotProduceAFullTurnImpulse() {
        val wrapped = TagMotion().also { it.tick(179f); it.tick(-179f) }
        val straight = TagMotion().also { it.tick(0f); it.tick(2f) }
        assertEquals(straight.angle(1f), wrapped.angle(1f), .00001f)
        assertEquals((wrapped.angle(0f) + wrapped.angle(1f)) / 2, wrapped.angle(.5f), .00001f)
        val before = wrapped.angle(1f)
        wrapped.tick(-179f)
        assertEquals(before, wrapped.angle(0f))
        repeat(200) { wrapped.tick(it * 70f) }
        assertTrue(abs(wrapped.angle(1f)) <= TagMotion.LIMIT)
    }

    @Test fun reducedMotionAndAChangedPlayerClearAllStoredMomentum() {
        val motion = turn(8f)
        motion.tick(100f, reducedMotion = true)
        assertEquals(0f, motion.angle(1f))
        motion.tick(100f)
        assertEquals(0f, motion.angle(1f))
        motion.reset()
        motion.tick(-160f)
        assertEquals(0f, motion.angle(1f))
    }

    @Test fun theAttachmentComesFromTheTopOfTheArtworkRatherThanItsBodyCentre() {
        val pixels = setOf(4 to 2, 5 to 2, 3 to 6, 8 to 9)
        assertEquals(TagAttachment(5f, 2.5f), TagAttachment.find(10, 12) { x, y -> x to y in pixels })
        assertThrows(IllegalStateException::class.java) { TagAttachment.find(10, 12) { _, _ -> false } }
    }
}
