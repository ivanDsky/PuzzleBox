package com.ivandsky.puzzlebox.designsystem

import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTokensTest {

    @Test
    fun `canonical patterns exit faster than they enter`() {
        for (pattern in listOf(SharedAxisTransition, ContainerTransform)) {
            assertTrue(
                "${pattern.name}: exit ($pattern.exitDurationMillis ms) must be faster than enter (${pattern.enterDurationMillis} ms)",
                pattern.exitDurationMillis < pattern.enterDurationMillis,
            )
        }
    }

    @Test
    fun `duration scale is ordered`() {
        assertTrue(MotionTokens.DurationShort < MotionTokens.DurationMedium)
        assertTrue(MotionTokens.DurationMedium < MotionTokens.DurationLong)
        assertTrue(MotionTokens.DurationLong < MotionTokens.DurationExpressive)
    }

    @Test
    fun `default spring is critically damped, momentum spring is underdamped`() {
        assertTrue(MotionTokens.SpringDampingMomentum > 0f)
        assertTrue(MotionTokens.SpringDampingMomentum < MotionTokens.SpringDampingDefault)
    }
}
