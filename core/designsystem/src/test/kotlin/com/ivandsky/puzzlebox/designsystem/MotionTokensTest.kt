package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.FloatSpringSpec
import kotlin.math.abs
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

    @Test
    fun `stamp lands from above full size on a springier curve than momentum`() {
        assertTrue(MotionTokens.StampFromScale > 1f)
        assertTrue(MotionTokens.StampSpringDamping < MotionTokens.SpringDampingMomentum)
    }

    @Test
    fun `sweep outline settles before the band finishes crossing`() {
        assertTrue(MotionTokens.SweepOutlineDuration < MotionTokens.SweepDuration)
        assertTrue(MotionTokens.SweepBandFraction in 0f..1f)
        assertTrue(MotionTokens.SweepAlpha in 0f..1f)
    }

    @Test
    fun `reduced-motion fade is feedback-short`() {
        assertTrue(MotionTokens.ReducedFadeDuration <= MotionTokens.DurationShort)
    }

    @Test
    fun `navigation backs are quicker than their pushes`() {
        assertTrue(MotionTokens.NavFullSlidePop < MotionTokens.NavFullSlideEnter)
        assertTrue(MotionTokens.MixAxisPop < MotionTokens.MixAxisPush)
        assertTrue(MotionTokens.LiftExit < MotionTokens.LiftEnter)
        assertTrue(MotionTokens.NavTabExit < MotionTokens.NavTabEnter)
    }

    @Test
    fun `navigation fractions and scrims are partial`() {
        for (fraction in listOf(
            MotionTokens.NavParallaxFraction,
            MotionTokens.NavScrimAlpha,
            MotionTokens.LiftNavFraction,
            MotionTokens.LiftScrimAlpha,
            MotionTokens.MixLiftCardFade,
        )) {
            assertTrue("$fraction in (0, 1)", fraction > 0f && fraction < 1f)
        }
    }

    @Test
    fun `trace retract and home are faster than grow-in`() {
        assertTrue(MotionTokens.Trace.RetractDuration < MotionTokens.Trace.GrowDuration)
        assertTrue(MotionTokens.Trace.HomeDuration < MotionTokens.Trace.GrowDuration)
        assertTrue(MotionTokens.Trace.PopDuration <= MotionTokens.DurationMedium)
    }

    @Test
    fun `trace tip stays inside one cell`() {
        with(MotionTokens.Trace) {
            assertTrue(TipDeadCells < TipMaxCells)
            assertTrue(TipMaxCells < 1f)
        }
    }

    @Test
    fun `trace head is wider than the line and a cell pops subtler than the pulse`() {
        assertTrue(MotionTokens.Trace.HeadScale > 1f)
        assertTrue(MotionTokens.Trace.PopScale > 1f)
        assertTrue(MotionTokens.Trace.PopScale < MotionTokens.PulseScale)
        assertTrue(MotionTokens.Trace.PopRiseFraction in 0f..1f)
    }

    @Test
    fun `a shake launched from rest swings out about its kick first, then settles back`() {
        val spring = FloatSpringSpec(MotionTokens.ShakeSpring.dampingRatio, MotionTokens.ShakeSpring.stiffness)
        val kick = MotionTokens.ShakeKick.value
        val velocity = kick * MotionTokens.ShakeVelocityPerPx
        val swing = (0..SHAKE_SAMPLE_MS).map { ms -> spring.getValueFromNanos(ms * 1_000_000L, 0f, 0f, velocity) }
        val peak = swing.max()
        assertTrue("first swing $peak vs kick $kick", abs(peak - kick) <= kick * 0.1f)
        assertTrue("settled at ${swing.last()}", abs(swing.last()) < kick * 0.05f)
    }

    private companion object {
        const val SHAKE_SAMPLE_MS = 600
    }
}
