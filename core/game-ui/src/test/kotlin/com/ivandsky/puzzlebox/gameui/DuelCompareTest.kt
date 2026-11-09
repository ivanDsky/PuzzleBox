package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Which figure each side shows: by rank order and state, and never a time for a hidden result. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DuelCompareTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun side(state: DuelSideState, name: String = "Sam") = DuelSide(name, null, state)

    private fun setContent(you: DuelSide, them: DuelSide, order: RankOrder = RankOrder.TimeThenHints) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) { DuelCompare(you = you, them = them, rankOrder = order) }
        }
    }

    private val clockText = Regex("""\d+:\d\d""")

    private fun anyClockText() = SemanticsMatcher("shows a m:ss time") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { clockText.containsMatchIn(it.text) } == true
    }

    @Test
    fun timeFirstShowsTheTimeAsTheHeadline() {
        setContent(side(DuelSideState.Time(58_000, 1), "Ann"), side(DuelSideState.Sealed))
        composeRule.onNodeWithTag("duel.compare.you.figure", useUnmergedTree = true).assertTextEquals(formatDuration(58_000))
    }

    @Test
    fun hintsFirstShowsTheHintCountAsTheHeadline() {
        setContent(
            side(DuelSideState.Time(58_000, 3), "Ann"),
            side(DuelSideState.Sealed),
            RankOrder.HintsThenTime,
        )
        composeRule.onNodeWithTag("duel.compare.you.figure", useUnmergedTree = true).assertTextContains("3", substring = true)
        composeRule.onNodeWithTag("duel.compare.you")
            .assertTextContains(formatDuration(58_000), substring = true)
    }

    @Test
    fun wholeSecondsDecideTheDisplayedTime() {
        setContent(side(DuelSideState.Time(42_300, 0), "Ann"), side(DuelSideState.Time(42_800, 0)))
        composeRule.onNodeWithTag("duel.compare.you.figure", useUnmergedTree = true).assertTextEquals("0:42")
        composeRule.onNodeWithTag("duel.compare.them.figure", useUnmergedTree = true).assertTextEquals("0:42")
    }

    @Test
    fun sealedNotPlayedAndGaveUpSidesShowNoTime() {
        val hidden = listOf(DuelSideState.Sealed, DuelSideState.NotPlayed, DuelSideState.GaveUp)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Column {
                    for (order in RankOrder.entries) {
                        for (state in hidden) {
                            DuelCompare(you = side(state, "Ann"), them = side(state), rankOrder = order)
                        }
                    }
                }
            }
        }
        composeRule.onAllNodes(anyClockText()).assertCountEquals(0)
    }
}
