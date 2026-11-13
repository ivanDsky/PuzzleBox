package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ArchiveDay
import com.ivandsky.puzzlebox.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.PastDayStatus
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The day card's action label follows the day: solved says "See result", unfinished "Keep going". */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class ArchiveDayCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.parse("2026-11-05")
    private var plays = 0

    private fun show(todayStatus: TodayStatus, past: PastDayStatus? = null) {
        val row = LibraryRowModel(
            card = GameCardModel(
                game = GameId("zip"),
                name = "Zip",
                brand = GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
                status = todayStatus,
                rankOrder = RankOrder.TimeThenHints,
                puzzleNumber = 10,
                best = null,
            ),
            today = 10,
            progress = ArchiveProgress(today.minusDays(9)..today, 10, 0, 0),
            stats = GameStats(0, 0, 0, 0, null, null, null, RankOrder.TimeThenHints),
            rules = emptyList(),
        )
        val day = past?.let { ArchiveDay(today.minusDays(3), 7, it, archived = false, openedOn = null) }
        composeRule.setContent {
            PuzzleBoxTheme {
                ArchiveDayCard(row, today, day, offline = false, onPlay = { plays++ }, onRetry = {}, onBackToToday = {})
            }
        }
    }

    @Test
    fun `solved today says See result and taps through`() {
        show(TodayStatus.Solved(95_000L))
        composeRule.onNodeWithTag("library.play").assertTextEquals("See result")
        composeRule.onNodeWithTag("library.play").performClick()
        assertEquals(1, plays)
    }

    @Test
    fun `solved past day says See result`() {
        show(TodayStatus.NotStarted, PastDayStatus.Solved(95_000L))
        composeRule.onNodeWithTag("library.play").assertTextEquals("See result")
    }

    @Test
    fun `in-progress day keeps Keep going`() {
        show(TodayStatus.InProgress(42_000L))
        composeRule.onNodeWithTag("library.play").assertTextEquals("Keep going")
    }
}
