package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.ivandsky.puzzlebox.compete.assertTouchTargetsAtLeast48
import com.ivandsky.puzzlebox.compete.create.DuelPreset
import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.NO_ACTIONS
import com.ivandsky.puzzlebox.compete.create.NewDuelState
import com.ivandsky.puzzlebox.compete.create.Opponent
import com.ivandsky.puzzlebox.compete.create.SelfDuelForm
import com.ivandsky.puzzlebox.compete.create.SelfDuelView
import com.ivandsky.puzzlebox.compete.create.SolvedCounts
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.compete.create.newDuelBrands
import com.ivandsky.puzzlebox.compete.create.screen.NewDuelScreen
import com.ivandsky.puzzlebox.compete.create.state
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val NARROW = "w360dp-h800dp-420dpi"
private const val PHONE = "w412dp-h915dp-420dpi"
private const val TABLET = "w1280dp-h800dp-mdpi"
private const val DOUBLE = 2f

/** Past you's Customize panel on the New duel page where the screen is not the usual one: on a narrow phone, a phone and a tablet, and at double font size with ten rows. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE)
class PastYouLineupLayoutTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startKoinForBrands() {
        startKoin { modules(module { single { newDuelBrands() } }) }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private fun host(fontScale: Float, body: @Composable () -> Unit) {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PuzzleBoxTheme { Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { body() } }
            }
        }
        rule.waitForIdle()
    }

    private fun customize(rows: List<LineupEntry>, fontScale: Float = 1f) {
        val form = SelfDuelForm.quick(GAMES).copy(preset = DuelPreset.Custom)
        val state: NewDuelState = state(Opponent.PastYou, SelfDuelView(form, SolvedCounts(mapOf(ZIP to 12, TANGO to 3)), GAMES), "Best of ${rows.size} · against your first solves")
            .copy(lineup = LineupEditor(LineupDraft(rows)))
        host(fontScale) { NewDuelScreen(state, NO_ACTIONS, onBack = {}) }
        rule.onNodeWithTag("duel.preset.custom").performClick()
        rule.waitForIdle()
    }

    private fun assertInsideScreen(tags: List<String>) {
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        for (tag in tags) {
            val box = rule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo().fetchSemanticsNode().boundsInRoot
            assertTrue("$tag $box is outside the screen $root", box.left >= root.left - 1 && box.right <= root.right + 1 && box.top >= root.top - 1 && box.bottom <= root.bottom + 1)
        }
    }

    private fun assertCustomizeFits(rows: Int, below: String = "duel.lineup.add") {
        assertInsideScreen((1..rows).map { "duel.lineup.row.$it" } + listOf(below, "duel.friends"))
        rule.assertTouchTargetsAtLeast48()
    }

    /** Every text on screen is shown whole: none ends in an ellipsis or loses a line to a line limit. */
    private fun assertNoTextIsClipped() {
        val withText = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
        val clipped = rule.onAllNodes(withText, useUnmergedTree = true).fetchSemanticsNodes().mapNotNull { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            layouts.firstOrNull()?.takeIf { text -> text.multiParagraph.didExceedMaxLines || (0 until text.lineCount).any(text::isLineEllipsized) }
                ?.layoutInput?.text?.text
        }
        assertTrue("clipped text: $clipped", clipped.isEmpty())
    }

    @Test
    @Config(qualifiers = NARROW)
    fun `a narrow phone shows three rows, Add and the friends switch inside the screen, all 48 dp`() {
        customize(listOf(AnyGame(ZIP), AnyGame(TANGO), AnyGame(ZIP)))

        assertCustomizeFits(rows = 3)
    }

    @Test
    fun `a phone shows three rows, Add and the friends switch inside the screen, all 48 dp`() {
        customize(listOf(AnyGame(ZIP), AnyGame(TANGO), AnyGame(ZIP)))

        assertCustomizeFits(rows = 3)
    }

    @Test
    @Config(qualifiers = TABLET)
    fun `a tablet shows three rows, Add and the friends switch inside the screen, all 48 dp`() {
        customize(listOf(AnyGame(ZIP), AnyGame(TANGO), AnyGame(ZIP)))

        assertCustomizeFits(rows = 3)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun `at double font size ten rows and the max line fit a narrow phone with no clipped text`() {
        customize(List(10) { AnyGame(if (it % 2 == 0) ZIP else TANGO) }, fontScale = DOUBLE)

        rule.onNodeWithTag("duel.lineup.max", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("duel.lineup.add", useUnmergedTree = true).assertDoesNotExist()
        assertCustomizeFits(rows = 10, below = "duel.lineup.max")
        assertNoTextIsClipped()
    }
}
