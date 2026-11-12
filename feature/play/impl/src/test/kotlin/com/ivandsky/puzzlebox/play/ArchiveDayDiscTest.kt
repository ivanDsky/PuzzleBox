package com.ivandsky.puzzlebox.play

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ArchiveDay
import com.ivandsky.puzzlebox.domain.PastDayStatus
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A disc carries a small dot when its day has a saved LinkedIn board (spec A25), whatever the day's own state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class ArchiveDayDiscTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val date = LocalDate.parse("2026-10-14")

    private fun show(status: PastDayStatus?, saved: Boolean) {
        val disc = archiveDiscFor(compact = false)
        composeRule.setContent {
            PuzzleBoxTheme {
                ArchiveDayDisc(
                    date = date,
                    day = status?.let { ArchiveDay(date, 211, it, archived = false, openedOn = null) },
                    saved = saved,
                    today = false,
                    selected = false,
                    offline = false,
                    accent = Color.Blue,
                    cell = archiveCellFor(disc),
                    disc = disc,
                    reduced = true,
                    onClick = {},
                )
            }
        }
    }

    private fun dot() = composeRule.onNodeWithTag("library.calendar.day.$date.saved_board", useUnmergedTree = true)

    @Test
    fun `a saved day shows the dot`() {
        show(PastDayStatus.Solved(41_000L), saved = true)
        dot().assertExists()
    }

    @Test
    fun `a day with no saved board shows no dot`() {
        show(PastDayStatus.Solved(41_000L), saved = false)
        dot().assertDoesNotExist()
    }

    @Test
    fun `an unfinished day shows the dot`() {
        show(PastDayStatus.InProgress(62_000L), saved = true)
        dot().assertExists()
    }

    @Test
    fun `a day never opened shows the dot`() {
        show(PastDayStatus.NeedsConnection, saved = true)
        dot().assertExists()
    }

    @Test
    fun `an outside day takes no dot`() {
        show(null, saved = true)
        dot().assertDoesNotExist()
    }
}
