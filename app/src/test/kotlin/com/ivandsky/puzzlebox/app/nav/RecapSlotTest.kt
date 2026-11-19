package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.insights.api.InsightsRecapKey
import com.ivandsky.puzzlebox.insights.domain.recap.DueRecap
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home's recap card gate (A2, A3): the flag, what is due, what the player dismissed, and the two taps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class RecapSlotTest {

    private val navigator = Navigator(puzzleBoxNavigationState())
    private val on = FakeReleaseFlags(InsightsFlag.MonthlyRecap).enabled.value
    private val october = DueRecap("2026-10", "October 2026")
    private val dismissedPeriods = mutableListOf<String>()

    private fun slot(
        due: DueRecap? = october,
        dismissed: Set<String> = emptySet(),
        enabled: Set<ReleaseFlagKey> = on,
    ) = recapSlot(enabled, due, dismissed, navigator) { dismissedPeriods += it }

    @Test
    fun `with the flag off there is no card even when a recap is due`() {
        assertNull(slot(enabled = FakeReleaseFlags().enabled.value))
    }

    @Test
    fun `nothing due is no card`() {
        assertNull(slot(due = null))
    }

    @Test
    fun `a dismissed period shows no card`() {
        assertNull(slot(dismissed = setOf("2026-10")))
    }

    @Test
    fun `dismissing another period leaves this card`() {
        val shown = slot(dismissed = setOf("2026-09", "2025-10"))

        assertNotNull(shown)
        assertEquals("October 2026", shown!!.label)
    }

    @Test
    fun `opening pushes that period's recap as a layer page`() {
        slot()!!.onOpen()

        assertEquals(listOf(InsightsRecapKey("2026-10")), navigator.state.layer)
    }

    @Test
    fun `dismissing reports that period and opens nothing`() {
        slot()!!.onDismiss()

        assertEquals(listOf("2026-10"), dismissedPeriods)
        assertEquals(emptyList<Any>(), navigator.state.layer)
    }
}
