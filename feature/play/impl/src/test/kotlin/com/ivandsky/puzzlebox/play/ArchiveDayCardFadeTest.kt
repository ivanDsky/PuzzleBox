package com.ivandsky.puzzlebox.play

import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Switching the day on the card fades the old day out, then the new one in: no frame draws both
 * days, and reduced motion swaps at once.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class ArchiveDayCardFadeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.parse("2026-11-05")
    private val dayA = today.minusDays(3)
    private val dayB = today.minusDays(5)

    private fun setAnimatorScale(scale: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @After
    fun resetScale() = setAnimatorScale(1f)

    /** Shows day A and returns the switch to day B; the clock is paused from the switch on. */
    private fun cardOnDayA(): () -> Unit {
        val row = LibraryRowModel(
            card = GameCardModel(
                game = GameId("zip"),
                name = "Zip",
                brand = GameBrand(AccentSlot.Blue, GameGlyphs.Zip),
                status = TodayStatus.NotStarted,
                rankOrder = RankOrder.TimeThenHints,
                puzzleNumber = 10,
                best = null,
            ),
            today = 10,
            progress = ArchiveProgress(today.minusDays(9)..today, 10, 0, 0),
            stats = GameStats(0, 0, 0, 0, null, null, null, RankOrder.TimeThenHints),
            rules = emptyList(),
        )
        var day by mutableStateOf(ArchiveDay(dayA, 7, PastDayStatus.Solved(95_000L), archived = false, openedOn = null))
        composeRule.setContent {
            PuzzleBoxTheme {
                ArchiveDayCard(row, today, day, offline = false, onPlay = {}, onRetry = {}, onBackToToday = {})
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(1_000L)
        return {
            day = ArchiveDay(dayB, 5, PastDayStatus.NeedsConnection, archived = false, openedOn = null)
            // The clock is stepped by hand, so flush the write before the next frame.
            Snapshot.sendApplyNotifications()
        }
    }

    private fun drawn(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun eyebrows(day: LocalDate) = drawn("library.day_card.day.$day")

    @Test
    fun `no frame of a day switch draws both days`() {
        val switchDay = cardOnDayA()
        switchDay()
        // Every frame over the whole switch (150 ms out, 300 ms in) and a little past it.
        repeat(40) { frame ->
            composeRule.mainClock.advanceTimeBy(16L)
            assertEquals("days drawn at frame $frame", 1, eyebrows(dayA) + eyebrows(dayB))
            assertEquals("actions drawn at frame $frame", 1, drawn("library.play"))
            assertEquals("back buttons drawn at frame $frame", 1, drawn("library.day_card.back"))
        }
    }

    @Test
    fun `the old day is still there halfway through its exit and the new one is not`() {
        val switchDay = cardOnDayA()
        switchDay()
        composeRule.mainClock.advanceTimeBy(75L)
        assertEquals(1, eyebrows(dayA))
        assertEquals(0, eyebrows(dayB))
    }

    @Test
    fun `a day switch settles on the new day only`() {
        val switchDay = cardOnDayA()
        switchDay()
        composeRule.mainClock.advanceTimeBy(600L)
        assertEquals(0, eyebrows(dayA))
        assertEquals(1, eyebrows(dayB))
        assertEquals(1, drawn("library.play"))
    }

    @Test
    fun `reduced motion swaps the day at once`() {
        setAnimatorScale(0f)
        val switchDay = cardOnDayA()
        switchDay()
        repeat(5) { frame ->
            composeRule.mainClock.advanceTimeByFrame()
            assertEquals("old eyebrow at frame $frame", 0, eyebrows(dayA))
            assertEquals("new eyebrow at frame $frame", 1, eyebrows(dayB))
            assertEquals("one action at frame $frame", 1, drawn("library.play"))
        }
    }
}
