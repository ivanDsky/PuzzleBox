package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ArchiveDay
import com.ivandsky.puzzlebox.domain.ArchiveProgress
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.PastDayStatus
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameCardModel
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.ArchiveDayCard
import com.ivandsky.puzzlebox.play.LibraryRowModel
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * On the narrowest supported phone, an archive day whose LinkedIn session ended shows the whole "From the archive"
 * pill and the whole chip label: the two compete for the top row's width, and an ellipsis in either hides which one
 * the day is or what the chip offers. The date beside them sheds its weekday and year before it would be cut.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
class ArchiveDayCardLinkedInChipTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.parse("2026-11-05")

    private fun show(status: LinkedInDayStatus, reconnect: Boolean) {
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
        val day = ArchiveDay(today.minusDays(3), 7, PastDayStatus.Solved(41_000L), archived = true, openedOn = null)
        composeRule.setContent {
            PuzzleBoxTheme {
                Box(Modifier.padding(Spacing.lg)) {
                    ArchiveDayCard(
                        row, today, day, offline = false, onPlay = {}, onRetry = {}, onBackToToday = {},
                        linkedIn = {
                            LinkedInSyncChip(status, onResume = {}, onReconnect = if (reconnect) ({}) else null, onRetry = {})
                        },
                    )
                }
            }
        }
    }

    private fun SemanticsNodeInteraction.isEllipsized(): Boolean {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single().isLineEllipsized(0)
    }

    private fun textNode(text: String) = composeRule.onNodeWithText(text, useUnmergedTree = true)

    @Test
    fun theArchivePillAndTheSignInLabelBothReadInFull() {
        show(LinkedInDayStatus.SessionExpired, reconnect = true)
        textNode("From the archive").assertExists()
        assertFalse(textNode("From the archive").isEllipsized())
        assertFalse(textNode("Sign in").isEllipsized())
        // The day itself gives way, but down to a shorter date, not to an ellipsis.
        assertFalse(composeRule.onNode(hasText("NOV 2", substring = true), useUnmergedTree = true).isEllipsized())
    }

    @Test
    fun theArchivePillAndTheSessionEndedLabelBothReadInFull() {
        show(LinkedInDayStatus.SessionExpired, reconnect = false)
        assertFalse(textNode("From the archive").isEllipsized())
        assertFalse(textNode("Session ended").isEllipsized())
    }
}
