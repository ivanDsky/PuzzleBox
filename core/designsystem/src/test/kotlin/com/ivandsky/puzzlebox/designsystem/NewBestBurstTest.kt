package com.ivandsky.puzzlebox.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The new-best burst's particle field: a seeded, bounded, repeatable scatter. */
class NewBestBurstTest {

    @Test
    fun `a burst has 18 particles`() {
        assertEquals(18, burstParticles(seed = 1L).size)
        assertEquals(18, NewBestBurstDefaults.ParticleCount)
    }

    @Test
    fun `the same seed gives the same burst`() {
        assertEquals(burstParticles(seed = 7L), burstParticles(seed = 7L))
        assertNotEquals(burstParticles(seed = 7L), burstParticles(seed = 8L))
    }

    @Test
    fun `every particle stays within the travel radius`() {
        for (seed in 0L..20L) {
            for (particle in burstParticles(seed = seed)) {
                assertTrue(particle.distance in NewBestBurstDefaults.MinTravel..1f)
                assertTrue(particle.size in NewBestBurstDefaults.MinSize..1f)
                assertTrue(particle.angleRad in 0f..(2 * Math.PI).toFloat())
            }
        }
    }

    @Test
    fun `particles take their colors in turn`() {
        val colors = burstParticles(seed = 3L).map { it.colorIndex }
        assertEquals(listOf(0, 1, 2, 0, 1, 2), colors.take(6))
    }

    @Test
    fun `pulse and burst lengths are the motion tokens and the handoff`() {
        assertEquals(480, NewBestBurstDefaults.DurationMs)
        assertEquals(MotionTokens.PulseDuration, 360)
        assertEquals(MotionTokens.PulseScale, 1.12f)
    }

    @Test
    fun `the pulse rises to its scale mid-way and settles back to one`() {
        assertEquals(1f, pulseScale(0f), 1e-6f)
        assertEquals(MotionTokens.PulseScale, pulseScale(0.5f), 1e-6f)
        assertEquals(1f, pulseScale(1f), 1e-5f)
    }
}
