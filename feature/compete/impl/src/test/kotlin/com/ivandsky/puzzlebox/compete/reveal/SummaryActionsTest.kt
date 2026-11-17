package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The duel summary's Share and Watch again pair: labelled, and Watch again wears its own icon. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SummaryActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `watch again shows its label and fires its callback`() {
        var watched = 0
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Actions(SUMMARY, onRematch = {}, onWatchAgain = { watched++ })
            }
        }

        composeRule.onNodeWithTag("reveal.watchAgain").assertTextEquals("Watch again").performClick()

        assertEquals(1, watched)
    }

    @Test
    fun `watch again wears a replay icon, not the retry arrow Play again uses`() {
        assertNotSame(PuzzleIcons.Retry, PuzzleIcons.WatchAgain)
    }

    private companion object {
        val SUMMARY = DuelSummary(
            me = "Ivan",
            opponent = "Sam",
            kind = VerdictKind.Win,
            myScore = 2,
            theirScore = 0,
            bestOf = "Best of 3 · Mon 9 Nov",
            tiles = SummaryTiles(puzzlesWon = 2, puzzles = 2, fastestMs = 58_000, totalMs = 116_000),
            rows = emptyList(),
            shareText = "",
        )
    }
}
