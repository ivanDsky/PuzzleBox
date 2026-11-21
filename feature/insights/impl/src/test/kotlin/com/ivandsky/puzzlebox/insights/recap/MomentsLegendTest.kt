package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.GOLDEN_PHONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Moments legend on a year's card: whole words in every label, and the long streak value under its label, at the end. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class MomentsLegendTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(fontScale: Float) {
        val story = MomentsFixtures.story(MomentsFixtures.year, RecapFixtures.year2025, "2025")
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PuzzleBoxTheme { RecapScreen(story, story.title, FastestFixtures.gameName, onPage = {}, onWatchReplay = {}, onClose = {}) }
            }
        }
        rule.mainClock.advanceTimeBy(3_000)
    }

    // Catches the reported "Streak mile / stones": the value squeezing its label until a word breaks.
    @Test
    fun `the legend keeps its words and items whole`() = assertLegend(fontScale = 1f)

    @Test
    fun `the legend keeps its words and items whole at a 1_3 font scale`() = assertLegend(fontScale = 1.3f)

    // Catches a streak value broken inside an item ("100 / days") when even its own line is too narrow for it.
    @Test
    @Config(qualifiers = "w320dp-h891dp-420dpi")
    fun `a narrow phone at a 1_3 font scale wraps the streak value between its items`() {
        assertLegend(fontScale = 1.3f, countBeside = false)
        assertTrue(layoutOf(text(StreakTag, StreakValue)).lineCount > 1)
    }

    /** The legend's checks at [fontScale]; [countBeside] when the best count fits beside its label, else it goes under. */
    private fun assertLegend(fontScale: Float, countBeside: Boolean = true) {
        show(fontScale)
        for ((tag, label) in Labels) {
            val lines = layoutOf(text(tag, label))
            assertBreaks("$label at $fontScale", lines) { line, end -> line[end - 1].isWhitespace() }
        }
        val streak = rule.onNodeWithTag(StreakTag).getBoundsInRoot()
        val label = text(StreakTag, "Streak milestones").getBoundsInRoot()
        val value = text(StreakTag, StreakValue)
        assertBreaks("$StreakValue at $fontScale", layoutOf(value)) { line, end -> line.substring(end - 2, end) == "· " }
        val under = value.getBoundsInRoot()
        assertTrue("value $under over label $label at $fontScale", under.top >= label.bottom - Tolerance)
        assertEquals("value's end at $fontScale", streak.right.value, under.right.value, Tolerance.value)
        assertTrue("value $under out of its row $streak at $fontScale", under.bottom <= streak.bottom)

        val best = rule.onNodeWithTag(BestTag).getBoundsInRoot()
        val count = text(BestTag, "12").getBoundsInRoot()
        assertEquals("count's end at $fontScale", best.right.value, count.right.value, Tolerance.value)
        val bestLabel = text(BestTag, "Personal bests").getBoundsInRoot()
        if (countBeside) {
            assertEquals("count beside its label at $fontScale", middle(bestLabel), middle(count), Tolerance.value)
        } else {
            assertTrue("count $count over its label $bestLabel at $fontScale", count.top >= bestLabel.bottom - Tolerance)
        }

        val card = rule.onNodeWithTag("recap.moments").getBoundsInRoot()
        val facts = rule.onNodeWithTag("recap.moments.facts").getBoundsInRoot()
        assertTrue("legend $facts off the card $card at $fontScale", facts.bottom <= card.bottom)
    }

    private fun text(tag: String, text: String) = rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)

    private fun layoutOf(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    /** Every line but the last of [layout] ends where [allowed] says a break may fall. */
    private fun assertBreaks(what: String, layout: TextLayoutResult, allowed: (String, Int) -> Boolean) {
        val text = layout.layoutInput.text.text
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue("$what breaks after \"${text.substring(0, end)}\"", allowed(text, end))
        }
    }

    private fun middle(bounds: DpRect) = (bounds.top.value + bounds.bottom.value) / 2
}

private const val BestTag = "recap.moments.personalBest"
private const val StreakTag = "recap.moments.streak"
private const val StreakValue = "7 days · 30 days · 100 days"
private val Labels = listOf(
    BestTag to "Personal bests",
    "recap.moments.firstSubX" to "First under a time",
    "recap.moments.fullSet" to "Full sets",
    StreakTag to "Streak milestones",
)

/** A row's side padding, and the rounding allowed when comparing edges. */
private val RowPad = 16.dp
private val Tolerance = 1.dp
