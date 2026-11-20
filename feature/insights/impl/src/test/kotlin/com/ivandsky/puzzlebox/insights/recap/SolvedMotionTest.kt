package com.ivandsky.puzzlebox.insights.recap

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
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

/**
 * The Solved card's arrival at fixed clock points, with motion on: each band comes up from under the sheet at its own
 * start (650 + k x 90ms), its count rolls from 220ms after that, and the hint-free ribbon comes in after the last count.
 * The default fixture has five games, so the last band starts at 1010ms and the ribbon at 2130ms.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class SolvedMotionTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(animatorScale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, animatorScale)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalGameBrands provides SolvedFixtures.brands) {
                    SolvedCardPage(SolvedFixtures.default, "October 2026", SolvedFixtures.gameName)
                }
            }
        }
    }

    private fun node(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    private fun top(game: String) = node("recap.solved.game.$game").boundsInRoot.top
    private fun text(tag: String) = node(tag).config[SemanticsProperties.Text].joinToString("") { it.text }
    private fun count(game: String) = text("recap.solved.game.$game.count").toInt()
    private fun ribbonCount() = text("recap.hintfree.count").substringBefore(' ').toInt()
    private fun ribbonOffscreen() = node("recap.hintfree.count").boundsInRoot.right <= 0f

    private class Frame(val tops: Map<String, Float>, val counts: Map<String, Int>, val ribbonCount: Int, val ribbonOffscreen: Boolean)

    private fun frame() = Frame(games.associateWith(::top), games.associateWith(::count), ribbonCount(), ribbonOffscreen())

    @Test
    fun `bands come up one after another and the ribbon waits for the last count`() {
        show(animatorScale = 1f)
        val at = linkedMapOf<Int, Frame>()
        var now = 0
        for (ms in listOf(0, 700, 1200, 2000, 2500, 5000)) {
            rule.mainClock.advanceTimeBy((ms - now).toLong())
            now = ms
            at[ms] = frame()
        }
        val settled = at.getValue(5000)
        assertEquals(listOf(28, 24, 22, 14, 8), games.map { settled.counts.getValue(it) })
        assertEquals(71, settled.ribbonCount)
        assertTrue("the settled ribbon is on the page", !settled.ribbonOffscreen)

        // 0ms: nothing has come in.
        assertTrue("every band is still below its place", games.all { at.getValue(0).tops.getValue(it).let { top -> top > settled.tops.getValue(it) + 1f || top == 0f } })
        assertTrue(at.getValue(0).ribbonOffscreen)
        // 700ms: band 0 (650) has started up, band 1 (740) has not; no count has started (the first is 870).
        val start = at.getValue(700)
        assertTrue("band 0 is on its way", start.tops.getValue("queens") < at.getValue(0).tops.getValue("queens"))
        assertEquals("band 1 has not moved", at.getValue(0).tops.getValue("tango"), start.tops.getValue("tango"), 0.5f)
        // (a band wholly under the sheet's clip reports no bounds, top 0)
        assertEquals(0, start.counts.getValue("queens"))
        // 1200ms: band 0's count is mid-roll; the last count (1230) has not started.
        val mid = at.getValue(1200).counts
        assertTrue("band 0 is mid-roll ($mid)", mid.getValue("queens") in 1 until 28)
        assertEquals(0, mid.getValue("strands"))
        // 2000ms: every count is in and every band is home, but the ribbon (2130) is still off the page.
        val last = at.getValue(2000)
        assertEquals(listOf(28, 24, 22, 14, 8), games.map { last.counts.getValue(it) })
        assertTrue(games.all { last.tops.getValue(it) == settled.tops.getValue(it) })
        assertTrue(last.ribbonOffscreen)
        // 2500ms: the ribbon is in and its own count is mid-roll.
        assertTrue(!at.getValue(2500).ribbonOffscreen)
        assertTrue("the ribbon's count is mid-roll", at.getValue(2500).ribbonCount in 1 until 71)
    }

    @Test
    fun `reduced motion shows the final counts and every band in place at once`() {
        show(animatorScale = 0f)
        rule.mainClock.advanceTimeBy(0)

        val frame = frame()
        assertEquals(listOf(28, 24, 22, 14, 8), games.map { frame.counts.getValue(it) })
        assertEquals(71, frame.ribbonCount)
        assertTrue(!frame.ribbonOffscreen)
    }

    private val games = listOf("queens", "tango", "zip", "sudoku", "strands")
}
