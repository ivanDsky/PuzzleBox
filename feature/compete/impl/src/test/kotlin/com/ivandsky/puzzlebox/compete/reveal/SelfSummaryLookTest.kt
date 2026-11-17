package com.ivandsky.puzzlebox.compete.reveal

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A self duel's summary reads as the contract: "← You" / "Past you →", each gap number in its winner's hue. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class SelfSummaryLookTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private var primary = Color.Unspecified
    private var secondary = Color.Unspecified
    private var onSurfaceVariant = Color.Unspecified

    private fun colorOf(text: String): Color {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.first().layoutInput.style.color
    }

    private fun showBar(winner: Side?, tint: Boolean) {
        rule.setContent {
            PuzzleBoxTheme {
                primary = LocalPrimaryText.current
                secondary = secondaryText()
                onSurfaceVariant = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                MarginBar(RowMargin(0.5f, "14 s"), winner, 0, 64.dp, Modifier.width(300.dp), tintLabel = tint)
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `a self duel gap you won is in your hue`() {
        showBar(Side.Mine, tint = true)

        assertEquals(primary, colorOf("14 s"))
    }

    @Test
    fun `a self duel gap Past you won is in Past you's hue`() {
        showBar(Side.Theirs, tint = true)

        assertEquals(secondary, colorOf("14 s"))
        assertNotEquals(primary, secondary)
    }

    @Test
    fun `a tied self duel gap stays neutral`() {
        showBar(null, tint = true)

        assertEquals(onSurfaceVariant, colorOf("14 s"))
    }

    @Test
    fun `a friend duel gap stays neutral whoever won`() {
        showBar(Side.Mine, tint = false)

        assertEquals(onSurfaceVariant, colorOf("14 s"))
    }

    private fun showHeader(summary: DuelSummary) {
        rule.setContent { PuzzleBoxTheme { PuzzleTableHeader(summary, TableMetrics.of(compact = false, tableWidth = 1000.dp)) } }
        rule.waitForIdle()
    }

    @Test
    fun `a self duel margin header reads You and Past you in sentence case`() {
        showHeader(RevealSelfGoldenScreenshots.WIN)

        rule.onNodeWithText("← You").assertExists()
        rule.onNodeWithText("Past you →").assertExists()
    }

    @Test
    fun `a friend duel margin header keeps its capitals`() {
        showHeader(RevealSelfGoldenScreenshots.WIN.copy(pastYou = false))

        rule.onNodeWithText("← ${RevealSelfGoldenScreenshots.WIN.me}".uppercase()).assertExists()
    }

    private fun heroScoreColor(summary: DuelSummary): Color {
        rule.setContent {
            PuzzleBoxTheme {
                secondary = secondaryText()
                onSurfaceVariant = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                SummaryCard(summary, onRematch = {}, onWatchAgain = {})
            }
        }
        rule.waitForIdle()
        return colorOf(summary.theirScore.toString())
    }

    @Test
    fun `a phone self loss hero scores Past you's winning number in its hue`() {
        val shown = heroScoreColor(RevealSelfGoldenScreenshots.LOSS)

        assertEquals(secondary, shown)
    }

    @Test
    fun `a phone friend loss hero keeps the winner's number neutral`() {
        val shown = heroScoreColor(RevealSelfGoldenScreenshots.LOSS.copy(pastYou = false))

        assertEquals(onSurfaceVariant, shown)
    }
}
