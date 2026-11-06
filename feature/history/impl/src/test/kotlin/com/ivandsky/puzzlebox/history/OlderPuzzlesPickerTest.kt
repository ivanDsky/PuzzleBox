package com.ivandsky.puzzlebox.history

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.history.HistoryTestSupport.ZIP
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * History's "Older puzzles" picker over one archive game (A12, A20, A22): only the range
 * is selectable, it opens on yesterday (clamped into the range), picking fetches nothing,
 * Cancel opens nothing, and the headline reads the selected day's number and status from
 * local data. Drives [HistoryRoute] over a real [HistoryViewModel].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class OlderPuzzlesPickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today: LocalDate = HistoryTestSupport.TODAY
    private val daily = FakeDailyRepository()
    private val opened = ArrayList<Pair<GameId, LocalDate>>()

    private fun openPicker(range: ClosedRange<LocalDate>, records: List<CompletionRecord> = emptyList()) {
        daily.setArchiveRange(ZIP, range)
        daily.setPuzzleNumbers(ZIP) { 590 + it.dayOfMonth }
        val viewModel = HistoryTestSupport.viewModel(daily, FakeCompletionRepository(records))
        composeRule.setContent {
            PuzzleBoxTheme {
                HistoryRoute(
                    key = HistoryKey(),
                    layout = HistoryLayout.SinglePane,
                    onBack = {},
                    onReplay = { game, day -> opened.add(game to day) },
                    viewModel = viewModel,
                )
            }
        }
        awaitTag("history.older")
        composeRule.onNodeWithTag("history.older").performClick()
        awaitTag("history.older.picker")
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** A picker day cell: its merged text is the full date ("Monday, November 2, 2026"). */
    private fun day(date: String) = composeRule.onNode(
        hasText(date, substring = true) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
    )

    @Test
    fun `the picker allows only the archive range, opens the picked day and fetches nothing`() {
        openPicker(LocalDate.parse("2026-11-01")..today)

        // It opens on yesterday, today still selectable.
        day("November 2, 2026").assertIsEnabled().assertIsSelected()
        day("November 3, 2026").assertIsEnabled()
        day("November 1, 2026").assertIsEnabled()
        day("November 4, 2026").assertIsNotEnabled()
        day("November 30, 2026").assertIsNotEnabled()

        // A semantic tap (TalkBack's): under Robolectric an injected touch does not select a day cell.
        day("November 1, 2026").performSemanticsAction(SemanticsActions.OnClick)
        day("November 1, 2026").assertIsSelected()
        composeRule.onNodeWithTag("history.older.open").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(ZIP to LocalDate.parse("2026-11-01")), opened)
        assertEquals(0, daily.dayCalls(ZIP, LocalDate.parse("2026-11-01")))
        composeRule.onNodeWithTag("history.older.picker").assertDoesNotExist()
    }

    @Test
    fun `cancel closes the picker without opening a day`() {
        openPicker(LocalDate.parse("2026-11-01")..today)

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("history.older.picker").assertDoesNotExist()
        assertEquals(emptyList<Pair<GameId, LocalDate>>(), opened)
    }

    @Test
    fun `the picker opens on the range's start when yesterday is before it`() {
        openPicker(today..today)

        day("November 3, 2026").assertIsSelected()
    }

    @Test
    fun `the headline reads the selected day's number and solved status`() {
        val yesterday = today.minusDays(1)
        openPicker(LocalDate.parse("2026-11-01")..today, records = listOf(HistoryTestSupport.record(yesterday)))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("history.older.headline").assertTextEquals("Zip #592 · Solved 1:35")
    }

    @Test
    fun `the headline follows the picked day - a day never stored needs a connection`() {
        openPicker(LocalDate.parse("2026-11-01")..today)

        day("November 1, 2026").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("history.older.headline").assertTextEquals("Zip #591 · Needs a connection the first time")
    }
}
