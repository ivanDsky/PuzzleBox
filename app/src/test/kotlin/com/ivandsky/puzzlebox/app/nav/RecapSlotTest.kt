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
    private val both = FakeReleaseFlags(InsightsFlag.MonthlyRecap, InsightsFlag.YearRecap).enabled.value
    private val october = DueRecap("2026-10", "October 2026", yearly = false)
    private val year = DueRecap("2026", "2026", yearly = true)
    private val dismissedPeriods = mutableListOf<String>()

    private fun slot(
        due: List<DueRecap> = listOf(october),
        dismissed: Set<String> = emptySet(),
        enabled: Set<ReleaseFlagKey> = on,
    ) = recapSlot(enabled, due, dismissed, navigator) { dismissedPeriods += it }

    @Test
    fun `with the flag off there is no card even when a recap is due`() {
        assertNull(slot(enabled = FakeReleaseFlags().enabled.value))
    }

    @Test
    fun `nothing due is no card`() {
        assertNull(slot(due = emptyList()))
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

    @Test
    fun `a year is never shown while only the monthly recap is on`() {
        assertNull(slot(due = listOf(year), enabled = on))
        assertEquals("October 2026", slot(due = listOf(year, october), enabled = on)!!.label)
    }

    @Test
    fun `a month is not shown while only the yearly recap is on`() {
        val onlyYear = FakeReleaseFlags(InsightsFlag.YearRecap).enabled.value

        assertNull(slot(enabled = onlyYear))
        assertEquals("2026", slot(due = listOf(year, october), enabled = onlyYear)!!.label)
    }

    @Test
    fun `the year is shown before the month, and the month once the year is dismissed`() {
        assertEquals("2026", slot(due = listOf(year, october), enabled = both)!!.label)
        assertEquals("October 2026", slot(due = listOf(year, october), dismissed = setOf("2026"), enabled = both)!!.label)
        assertNull(slot(due = listOf(year, october), dismissed = setOf("2026", "2026-10"), enabled = both))
    }

    @Test
    fun `opening the year pushes its recap and dismissing reports its id`() {
        val shown = slot(due = listOf(year), enabled = both)!!

        shown.onOpen()
        shown.onDismiss()

        assertEquals(listOf(InsightsRecapKey("2026")), navigator.state.layer)
        assertEquals(listOf("2026"), dismissedPeriods)
    }
}
