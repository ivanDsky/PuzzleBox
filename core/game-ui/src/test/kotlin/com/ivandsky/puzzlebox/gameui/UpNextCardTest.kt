package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class UpNextCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun model(id: String, status: TodayStatus? = TodayStatus.InProgress(192_000L), line: String? = null) = GameCardModel(
        game = GameId(id),
        name = "Game",
        brand = GameBrand.Neutral,
        status = status,
        rankOrder = RankOrder.TimeThenHints,
        puzzleNumber = 7,
        line = line,
    )

    private fun showPair(tall: Boolean) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                // Side by side: a 356dp tablet pair stacked would overflow the test screen.
                Row {
                    UpNextCard(model("a"), tall = tall, onClick = {}, modifier = Modifier.weight(1f))
                    UpNextCard(model("b", line = "6×6 · #7"), tall = tall, onClick = {}, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    private fun height(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().let { it.bottom - it.top }

    @Test
    fun `phone card is the same height with and without a line`() {
        showPair(tall = false)
        assertEquals(height("card.next.a"), height("card.next.b"))
        assertEquals(height("card.line.a"), height("card.line.b"))
    }

    @Test
    fun `tablet card is the same height with and without a line`() {
        showPair(tall = true)
        assertEquals(height("card.next.a"), height("card.next.b"))
    }

    @Test
    fun `loading card shows the skeleton mark and keeps its height`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Column(Modifier.width(380.dp)) {
                    UpNextCard(model("a"), tall = false, onClick = {})
                    UpNextCard(model("b", status = null), tall = false, onClick = {})
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag(statusMarkTag(null), useUnmergedTree = true).assertExists()
        assertEquals(height("card.next.a"), height("card.next.b"))
    }

    @Test
    fun `whole card is one click target`() {
        var clicks = 0
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) { UpNextCard(model("a"), tall = false, onClick = { clicks++ }) }
        }
        composeRule.onNodeWithTag("card.next.a").assertHasClickAction().performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun `panel takes the hero's height in every kind`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Column(Modifier.width(380.dp)) {
                    UpNextCard(model("a"), tall = false, onClick = {})
                    NoNextPanel(NoNextKind.Loading, emptyList(), tall = false)
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(500)
        val panel = height("card.panel")
        assertTrue(panel > 0.dp)
        assertEquals(height("card.next.a"), panel)
    }
}
