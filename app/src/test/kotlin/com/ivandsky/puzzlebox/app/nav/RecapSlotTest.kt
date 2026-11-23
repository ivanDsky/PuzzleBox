package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.home.RecapSlot
import com.ivandsky.puzzlebox.insights.api.InsightsRecapKey
import com.ivandsky.puzzlebox.insights.domain.recap.DueRecap
import com.ivandsky.puzzlebox.insights.flags.InsightsFlag
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Home's recap card gate (A2, A3): the year's flag, what is due, what the player dismissed, and the two taps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class RecapSlotTest {

    private val navigator = Navigator(puzzleBoxNavigationState())
    private val on = FakeReleaseFlags().enabled.value
    private val both = FakeReleaseFlags(InsightsFlag.YearRecap).enabled.value
    private val october = DueRecap("2026-10", "October 2026", yearly = false)
    private val year = DueRecap("2026", "2026", yearly = true)
    private val dismissedPeriods = mutableListOf<String>()

    private fun slot(
        due: List<DueRecap> = listOf(october),
        dismissed: Set<String> = emptySet(),
        enabled: Set<ReleaseFlagKey> = on,
        opened: Set<String> = emptySet(),
    ) = recapSlot(enabled, due, dismissed, opened, today = LocalDate.of(2026, 11, 2), navigator) { dismissedPeriods += it } as RecapSlot.Due?

    private fun loading(enabled: Set<ReleaseFlagKey> = on, today: LocalDate?) =
        recapSlot(enabled, due = null, dismissed = emptySet(), opened = emptySet(), today = today, navigator = navigator) {}

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
    fun `a year is never shown while the yearly recap is off`() {
        assertNull(slot(due = listOf(year), enabled = on))
        assertEquals("October 2026", slot(due = listOf(year, october), enabled = on)!!.label)
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

    @Test
    fun `a recap whose story was opened is watched and another is new`() {
        assertTrue(slot(opened = setOf("2026-10"))!!.opened)
        assertFalse(slot(opened = setOf("2026-09"))!!.opened)
    }

    @Test
    fun `the tile carries the period's count and each game's share`() {
        val due = october.copy(solved = 96, solvedByGame = listOf(GameId("zip") to 60, GameId("tango") to 36))

        val shown = slot(due = listOf(due))!!

        assertEquals(96, shown.solved)
        assertEquals(listOf(GameId("zip") to 60, GameId("tango") to 36), shown.solvedByGame)
    }

    @Test
    fun `while the due list loads inside a recap window the slot is a skeleton`() {
        for (day in listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3), LocalDate.of(2027, 1, 7))) {
            assertEquals(RecapSlot.Loading, loading(today = day))
        }
    }

    @Test
    fun `outside a recap window, or with no day known, a loading list reserves no space`() {
        for (day in listOf(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 8), null)) {
            assertNull(loading(today = day))
        }
    }

    @Test
    fun `until dismissals and openings have both loaded no tile shows`() {
        val today = LocalDate.of(2026, 11, 2)
        val gates = listOf(null to emptySet<String>(), emptySet<String>() to null, null to null)
        for ((dismissed, opened) in gates) {
            assertNull(recapSlot(on, listOf(october), dismissed, opened, today, navigator) {})
            assertNull(recapSlot(on, null, dismissed, opened, today, navigator) {})
        }
    }
}
