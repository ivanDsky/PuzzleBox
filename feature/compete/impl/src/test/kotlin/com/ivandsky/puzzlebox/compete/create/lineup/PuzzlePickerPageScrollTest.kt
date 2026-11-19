package com.ivandsky.puzzlebox.compete.create.lineup

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ivandsky.puzzlebox.compete.assertTouchTargetsAtLeast48
import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.compete.create.newDuelBrands
import com.ivandsky.puzzlebox.compete.domain.lineup.PickablePuzzle
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.compose.koinViewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ROWS = 40
private const val LEADING_ITEMS = 3
private val KEYBOARD = 270.dp
private val ONE_ROW = 72.dp

/** The Add puzzle page as one list: a single vertical scroller, the search kept at the top, and every row reachable above the keyboard. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PuzzlePickerPageScrollTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun stopKoinAgain() = stopKoin()

    private val rows: List<PickablePuzzle> = List(ROWS) { solvedDaily(QUEENS, LocalDate.of(2026, 11, 9).minusDays(it.toLong()), 100 - it, "Queens #${100 - it}") }
    private lateinit var view: View

    private fun show() {
        startKoin {
            modules(module {
                single { newDuelBrands() }
                viewModel { AddPuzzleViewModel(pickerOf(rows), NO_LOOKUP) }
            })
        }
        val picker = LineupPickerModel(position = 2, changing = false, games = GAMES, solved = null, added = emptySet(), pastYou = false)
        rule.setContent {
            view = LocalView.current
            PuzzleBoxTheme {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    PuzzlePickerPage(picker, onPick = {}, viewModel = koinViewModel<AddPuzzleViewModel>())
                }
            }
        }
        rule.waitForIdle()
    }

    private fun openKeyboard(height: Dp) {
        val px = with(rule.density) { height.roundToPx() }
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        rule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        rule.waitForIdle()
    }

    private fun scrollTo(row: Int) = rule.onNode(hasScrollToIndexAction()).performScrollToIndex(LEADING_ITEMS + row)

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun rowTag(index: Int) = "duel.lineup.pick.${rows[index].entry.ref.wire}"

    @Test
    fun `the page has one vertical scroller`() {
        show()
        val vertical = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

        rule.onAllNodes(vertical).assertCountEquals(1)
    }

    @Test
    fun `after thirty rows the search field and the chips are still at the top and the tiles have scrolled away`() {
        show()
        rule.onNodeWithTag("duel.lineup.sheet.game.${ZIP.value}").assertIsDisplayed()
        val top = bounds("duel.lineup.picker").top

        scrollTo(30)
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.search").assertIsDisplayed()
        listOf(PickerFilterPage.Freshness, PickerFilterPage.Source, PickerFilterPage.Games).forEach { rule.onNodeWithTag(it.tag).assertIsDisplayed() }
        assertEquals("the field sits at the top of the list", top.value, bounds("duel.lineup.exact").top.value, 0.5f)
        GAMES.forEach { rule.onAllNodesWithTag("duel.lineup.sheet.game.${it.id.value}").assertCountEquals(0) }
    }

    @Test
    fun `the last row is reachable above the keyboard`() {
        show()
        openKeyboard(KEYBOARD)

        scrollTo(ROWS - 1)
        rule.waitForIdle()

        val keyboardTop = rule.onRoot().getUnclippedBoundsInRoot().bottom - KEYBOARD
        assertTrue("the last row ends at ${bounds(rowTag(ROWS - 1)).bottom}, under the keyboard at $keyboardTop", bounds(rowTag(ROWS - 1)).bottom <= keyboardTop + 1.dp)
    }

    @Test
    fun `every control on the page is at least 48 dp`() {
        show()

        rule.assertTouchTargetsAtLeast48()
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-420dpi")
    fun `with the chips under the field and rows scrolled, the rows get at least two rows of room above the keyboard`() {
        show()
        openKeyboard(KEYBOARD)
        scrollTo(10)
        rule.waitForIdle()

        val keyboardTop = rule.onRoot().getUnclippedBoundsInRoot().bottom - KEYBOARD
        val room = keyboardTop - bounds("duel.lineup.exact").bottom
        assertTrue("only $room is left for rows between the search field and the keyboard", room >= ONE_ROW * 2)
    }
}
