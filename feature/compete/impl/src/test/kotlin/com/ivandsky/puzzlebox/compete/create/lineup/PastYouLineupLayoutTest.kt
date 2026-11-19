package com.ivandsky.puzzlebox.compete.create.lineup

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.compete.assertTouchTargetsAtLeast48
import com.ivandsky.puzzlebox.compete.create.DuelPreset
import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.NO_ACTIONS
import com.ivandsky.puzzlebox.compete.create.NewDuelContent
import com.ivandsky.puzzlebox.compete.create.NewDuelState
import com.ivandsky.puzzlebox.compete.create.Opponent
import com.ivandsky.puzzlebox.compete.create.SelfDuelForm
import com.ivandsky.puzzlebox.compete.create.SelfDuelView
import com.ivandsky.puzzlebox.compete.create.SolvedCounts
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.compete.create.custom
import com.ivandsky.puzzlebox.compete.create.newDuelBrands
import com.ivandsky.puzzlebox.compete.create.state
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import com.ivandsky.puzzlebox.model.GameId
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val NARROW = "w360dp-h800dp-420dpi"
private const val PHONE = "w412dp-h915dp-420dpi"
private const val TABLET = "w1280dp-h800dp-mdpi"
private const val DOUBLE = 2f
private val MIN_TARGET = 48.dp

/**
 * Past you's lineup where the screen is not the usual one: turned under an open sheet, closed and opened again, on a narrow phone, a phone and
 * a tablet, and at double font size with ten rows and with the sheet open. The sheet's own structure is in [PastYouAddSheetTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = PHONE)
class PastYouLineupLayoutTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startKoinWithoutSheetAnimation() {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<Context>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        startKoin {
            modules(module {
                single { newDuelBrands() }
                viewModel { AddPuzzleViewModel(pickerOf(emptyList()), NO_LOOKUP) }
                viewModel(PAST_YOU_PICKER) { AddPuzzleViewModel(pickerOf(SOLVED_ROWS), lookup = null, pastYou = true) }
            })
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private var editor by mutableStateOf(LineupEditor(LineupDraft(listOf(AnyGame(ZIP), AnyGame(TANGO), AnyGame(ZIP)))))
    private var counts by mutableStateOf<Map<GameId, Int>?>(mapOf(ZIP to 12, TANGO to 3))

    private fun host(fontScale: Float, restoration: StateRestorationTester?, body: @Composable () -> Unit) {
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PuzzleBoxTheme { Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { body() } }
            }
        }
        if (restoration != null) restoration.setContent(content) else rule.setContent(content)
        rule.waitForIdle()
    }

    private fun panel(fontScale: Float = 1f, restoration: StateRestorationTester? = null) = host(fontScale, restoration) {
        LineupPanel(editor, GAMES, includeFriends = false, onEdit = { edit -> editor = edit(editor) }, audience = LineupAudience.PastYou(counts))
    }

    private fun customize(rows: List<LineupEntry>, fontScale: Float = 1f) {
        val form = SelfDuelForm.quick(GAMES).copy(preset = DuelPreset.Custom)
        val state: NewDuelState = state(Opponent.PastYou, SelfDuelView(form, SolvedCounts(mapOf(ZIP to 12, TANGO to 3)), GAMES), "Best of ${rows.size} · against your first solves")
            .copy(lineup = LineupEditor(LineupDraft(rows)))
        host(fontScale, null) { NewDuelContent(state, NO_ACTIONS) }
        rule.onNodeWithTag("duel.preset.custom").performClick()
        rule.waitForIdle()
    }

    private fun openSheet() {
        rule.onNodeWithTag("duel.lineup.add").performClick()
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

    /** Every text on screen, in the sheet too, is shown whole: none ends in an ellipsis or loses a line to a line limit. */
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

    /** The sheet's tiles, field, chips, and the first result; the results are a lazy list, so only the one in view is placed. */
    private fun assertSheetControlsAreAtLeast48dp() {
        val minPx = with(rule.density) { MIN_TARGET.toPx() }
        val tags = GAMES.map { "duel.lineup.sheet.game.${it.id.value}" } +
            listOf(PickerFilterPage.Source.tag, PickerFilterPage.Games.tag, "duel.lineup.search", "duel.lineup.pick.add.${SOLVED_ROWS.first().entry.ref.wire}")
        for (tag in tags) {
            val target = rule.onNodeWithTag(tag).performScrollTo().fetchSemanticsNode().touchBoundsInRoot
            assertTrue("$tag is ${target.width}x${target.height} px", target.width >= minPx - 1 && target.height >= minPx - 1)
        }
    }

    @Test
    fun `a turn under the open sheet keeps the rows, the sheet, the typed text and the chip`() {
        val restoration = StateRestorationTester(rule)
        panel(restoration = restoration)
        openSheet()
        rule.onNodeWithTag("duel.lineup.search").performTextInput("crown")
        rule.pickInPage(PickerFilterPage.Games, "duel.lineup.chip.game.${TANGO.value}")
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("1 puzzle")

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.sheet").assertExists()
        rule.onNodeWithTag("duel.lineup.search").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("crown")))
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("1 puzzle")
        rule.onNodeWithTag(PickerFilterPage.Games.tag).assertTextContains("Tango")
        rule.onNodeWithTag("duel.lineup.pick.${FRIEND_TANGO.entry.ref.wire}").assertExists()
        rule.onNodeWithTag("duel.lineup.pick.${ZIP_DAILY.entry.ref.wire}").assertDoesNotExist()
        rule.onNodeWithTag("duel.lineup.header").assertTextEquals("Puzzles, in order · 3 of 10")
    }

    @Test
    fun `a closed sheet forgets what was typed, so the next one opens on the whole list`() {
        panel()
        openSheet()
        rule.onNodeWithTag("duel.lineup.search").performTextInput("crown")
        rule.waitForIdle()
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("1 puzzle")

        editor = editor.dismiss()
        rule.waitForIdle()
        openSheet()

        rule.onNodeWithTag("duel.lineup.search").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("3 puzzles · Recently solved first")
    }

    @Test
    fun `a solve while the sheet is open turns its tile from off to on`() {
        counts = mapOf(ZIP to 12)
        editor = editor.openAdd()
        panel()
        rule.onNodeWithTag("duel.lineup.sheet.game.${TANGO.value}").assertTextEquals("Tango · 0 solved").assertIsNotEnabled()

        counts = mapOf(ZIP to 12, TANGO to 1)
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.sheet.game.${TANGO.value}").assertTextEquals("Tango · 1 solved").assertIsEnabled()
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

    @Test
    @Config(qualifiers = NARROW)
    fun `at double font size the open sheet has no clipped text and every control is 48 dp`() {
        editor = editor.openAdd()
        panel(fontScale = DOUBLE)

        assertSheetControlsAreAtLeast48dp()
        assertNoTextIsClipped()
    }
}
