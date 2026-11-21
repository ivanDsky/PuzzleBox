package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.share.RecapShareContent
import com.ivandsky.puzzlebox.insights.share.RecapShareImage
import com.ivandsky.puzzlebox.insights.share.ShareContract
import com.ivandsky.puzzlebox.insights.share.ShareStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * While the share deck's numbers count up, the poster overlay is not recomposed: only the number texts follow the count.
 * The poster's own modifier is rebuilt each time the poster is recomposed, so a counter in it counts those recompositions.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1080dp-h1920dp-mdpi")
class ShareCountRecomposeTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val cards = ShareContract.default.map { if (it is RecapCard.Solved) it.copy(total = 96, hintFree = 80) else it }
    private val content = RecapShareContent.of("October 2026", "Ada", cards, SolvedFixtures.gameName)
    private var recompositions = 0

    private val probe = Modifier.composed { recompositions++; Modifier }

    @Test
    fun `the poster is not recomposed on any tick of the count, though its numbers change`() {
        val progress = mutableStateOf(0f)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalGameBrands provides SolvedFixtures.brands) {
                    RecapShareImage(content, ShareStyle.Classic, probe, count = { progress.value })
                }
            }
        }
        rule.mainClock.advanceTimeBy(SettleMs)
        val before = recompositions
        val atStart = texts()

        val seen = Ticks.map { p ->
            set { progress.value = p }
            rule.mainClock.advanceTimeBy(FrameMs)
            texts()
        }

        assertEquals("the poster recomposed during the count", before, recompositions)
        assertTrue("the hero never read 0: ${seen[0]}", "0" in seen[0])
        assertTrue("the hero never read half way: ${seen[1]}", content.hero!!.valueAt(0.5f) in seen[1])
        assertTrue("the hero did not end on its final value: ${seen.last()}", content.hero!!.value in seen.last())
    }

    @Test
    fun `the probe sees a recomposition when the poster itself changes`() {
        val shown = mutableStateOf(content)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CompositionLocalProvider(LocalGameBrands provides SolvedFixtures.brands) { RecapShareImage(shown.value, ShareStyle.Classic, probe) }
            }
        }
        rule.mainClock.advanceTimeBy(SettleMs)
        val before = recompositions
        set { shown.value = content.copy(title = "November 2026") }
        rule.mainClock.advanceTimeBy(FrameMs)

        assertTrue("the probe did not count a recomposition: $before -> $recompositions", recompositions > before)
    }

    /** Writes state on the main thread and tells the composition. */
    private fun set(write: () -> Unit) = rule.runOnUiThread { write(); Snapshot.sendApplyNotifications() }

    /** The text of every Text on the poster, in reading order. */
    private fun texts(): List<String> = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
        .fetchSemanticsNodes().flatMap { node ->
            val results = mutableListOf<TextLayoutResult>()
            rule.runOnUiThread { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results) }
            results.map { it.layoutInput.text.text }
        }

    private companion object {
        const val FrameMs = 16L
        const val SettleMs = 1_000L
        val Ticks = listOf(0f, 0.5f, 0.75f, 1f)
    }
}
