package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapMotionTest {

    @Test
    fun `count hero and odometer carry the roll timing and the lap table`() {
        assertEquals(1000, RecapMotion.CountHeroDuration)
        assertEquals(200, RecapMotion.CountHeroDelay)
        assertEquals(60, RecapMotion.OdometerStagger)
        assertEquals(listOf(2, 1, 1), RecapMotion.OdometerLaps)
        assertEquals(0, RecapMotion.OdometerLapsBeyond)
    }

    @Test
    fun `deck tokens hold the deal, throw, stack and spring values`() {
        assertEquals(0.7f, RecapMotion.DeckSpringDamping, 0f)
        assertEquals(600f, RecapMotion.DeckSpringStiffness, 0f)
        assertEquals(420, RecapMotion.DealDuration)
        assertEquals(60.dp, RecapMotion.DealRise)
        assertEquals(-4f, RecapMotion.DealTilt, 0f)
        assertEquals(0.9f, RecapMotion.DealScaleFrom, 0f)
        assertEquals(180, RecapMotion.ThrowDuration)
        assertEquals(-1.25f, RecapMotion.ThrowTravel, 0f)
        assertEquals(-10f, RecapMotion.ThrowTilt, 0f)
        assertEquals(280, RecapMotion.ThrowBackDuration)
        assertEquals(14.dp, RecapMotion.DeckStackStep)
        assertEquals(0.045f, RecapMotion.DeckStackScaleStep, 0f)
        assertEquals(3, RecapMotion.DeckStackVisible)
        assertEquals(380, RecapMotion.DeckStackPromote)
        assertEquals(380, RecapMotion.DeckStackHoldIn)
        assertEquals(280, RecapMotion.DeckStackHoldOut)
    }

    @Test
    fun `sheen is one and four tenths of the solve sweep`() {
        assertEquals(672, RecapMotion.SheenDuration)
        assertEquals(0.6f, RecapMotion.SheenAlpha, 0f)
    }

    @Test
    fun `kinetic and cascade tokens hold the contract values`() {
        assertEquals(1.12f, RecapMotion.KineticRise, 0f)
        assertEquals(620, RecapMotion.KineticStaggerBudget)
        assertEquals(8, RecapMotion.KineticStaggerMin)
        assertEquals(26, RecapMotion.KineticStaggerMax)
        assertEquals(24, RecapMotion.CascadeRowStep)
        assertEquals(480, RecapMotion.CascadeRowCap)
        assertEquals(0.6f, RecapMotion.CascadeFromScale, 0f)
        assertEquals(90f, RecapMotion.CascadeFromRotationX, 0f)
    }

    @Test
    fun `sweep lights a run over 400 plus 120 a day, at most 1500, on an ease out cubic`() {
        assertEquals(400, RecapMotion.SweepRunBase)
        assertEquals(120, RecapMotion.SweepRunPerDay)
        assertEquals(1500, RecapMotion.SweepRunMax)
        assertEquals(8, RecapMotion.SweepFewDays)
        assertEquals(0f, RecapMotion.EasingSweep.transform(0f), 0f)
        assertEquals(1f, RecapMotion.EasingSweep.transform(1f), 0f)
        assertTrue(RecapMotion.EasingSweep.transform(0.5f) > 0.8f)
    }

    @Test
    fun `slam, sticker, ring and peel tokens hold the contract values`() {
        assertEquals(560, RecapMotion.SlamClimb)
        assertEquals(80, RecapMotion.SlamHold)
        assertEquals(420, RecapMotion.SlamPeel)
        assertEquals(300, RecapMotion.SlamDrop)
        assertEquals(0.35f, RecapMotion.SlamOvershoot, 0f)
        assertEquals(480, RecapMotion.SlamLandingPause)
        assertEquals(6.dp, RecapMotion.SlamRecoil)
        assertEquals((-3).dp, RecapMotion.SlamRecoilBack)
        assertEquals(1.9f, RecapMotion.StickerFromScale, 0f)
        assertEquals(34.dp, RecapMotion.StickerDrop)
        assertEquals(-18f, RecapMotion.StickerTilt, 0f)
        assertEquals(110, RecapMotion.StickerStepMax)
        assertEquals(1200, RecapMotion.StickerStepBudget)
        assertEquals(2.0f, RecapMotion.RingScale, 0f)
        assertEquals(560, RecapMotion.RingDuration)
        assertEquals(0.7f, RecapMotion.RingAlphaFrom, 0f)
        assertEquals(140, RecapMotion.RingStagger)
        assertEquals(5.dp, RecapMotion.PeelLift)
        assertEquals(5f, RecapMotion.PeelTilt, 0f)
        assertEquals(1.08f, RecapMotion.PeelScale, 0f)
        assertEquals(240, RecapMotion.PeelDuration)
        assertEquals(120, RecapMotion.PeelCurl)
    }
}
