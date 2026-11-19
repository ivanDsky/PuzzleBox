package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.play.domain.ArchiveDay
import com.ivandsky.puzzlebox.domain.PastDayStatus
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Text drawn on a game's accent fill is that accent's own on-colour: white clears 4.5:1 on none of the
 * hues (AccentTokens), so the solved archive day and a How-to-play rule number are read off the rendered text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AccentInkContrastTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val slots = AccentSlot.entries
    private val themes = listOf(false, true)

    /** The colour the rendered [text] is drawn in, from its layout result. */
    private fun inkOf(text: String, index: Int = 0): Color {
        val results = mutableListOf<TextLayoutResult>()
        val node = composeRule.onAllNodesWithText(text, useUnmergedTree = true)[index].fetchSemanticsNode()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.first().layoutInput.style.color
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val l1 = foreground.luminance() + 0.05f
        val l2 = background.luminance() + 0.05f
        return maxOf(l1, l2) / minOf(l1, l2)
    }

    @Test
    fun `a solved archive day's number clears 4_5 to 1 on every game's disc in light and dark`() {
        val cases = themes.flatMap { dark -> slots.map { dark to it } }
        val fills = arrayOfNulls<Color>(cases.size)
        composeRule.setContent {
            Column {
                cases.forEachIndexed { i, (dark, slot) ->
                    PuzzleBoxTheme(darkTheme = dark) {
                        fills[i] = slot.colors().accent
                        SolvedDisc(day = i + 1, accent = slot)
                    }
                }
            }
        }
        composeRule.waitForIdle()

        cases.forEachIndexed { i, (dark, slot) ->
            val ratio = contrast(inkOf("${i + 1}"), fills[i]!!)
            assertTrue("$slot dark=$dark day number = $ratio", ratio >= 4.5f)
        }
    }

    @Test
    fun `a How to play rule number clears 4_5 to 1 on every game's disc in light and dark`() {
        val base = previewState().rows.first()
        val cases = themes.flatMap { dark -> slots.map { dark to it } }
        val fills = arrayOfNulls<Color>(cases.size)
        composeRule.setContent {
            Column {
                cases.forEachIndexed { i, (dark, slot) ->
                    PuzzleBoxTheme(darkTheme = dark) {
                        fills[i] = slot.colors().accent
                        val brand = base.card.brand.copy(accent = slot)
                        HowToPlayCard(base.copy(card = base.card.copy(brand = brand), rules = listOf("rule $i")), hasLesson = false, onOpenLesson = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()

        cases.forEachIndexed { i, (dark, slot) ->
            val ratio = contrast(inkOf("1", i), fills[i]!!)
            assertTrue("$slot dark=$dark rule number = $ratio", ratio >= 4.5f)
        }
    }

    @Composable
    private fun SolvedDisc(day: Int, accent: AccentSlot) = ArchiveDayDisc(
        date = LocalDate.of(2026, 10, day),
        day = ArchiveDay(LocalDate.of(2026, 10, day), day, PastDayStatus.Solved(60_000L), archived = true, openedOn = null),
        today = false,
        selected = false,
        offline = false,
        accent = accent.colors(),
        cell = 56.dp,
        disc = 46.dp,
        reduced = false,
        onClick = {},
    )
}
