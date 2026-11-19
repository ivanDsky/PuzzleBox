package com.ivandsky.puzzlebox.compete.create.lineup

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.common.flags.FilterFlag
import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.newDuelBrands
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
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
import org.robolectric.shadows.ShadowDialog

private const val SOURCE = "duel.lineup.filter.source"
private const val PAGE = "$SOURCE.page"
private const val SHEET = "duel.lineup.sheet"
private val TILE = "duel.lineup.sheet.game.${GAMES.first().id.value}"
private const val BACK_SETTLE_MS = 1_000L

/** The Add puzzle sheet with FilterSheets on: a chip swaps its options in as a page of that same sheet, never a second sheet over it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PickerFilterAddSheetTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun noSheetAnimation() {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<Context>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private var editor by mutableStateOf(LineupEditor.opening(GAMES))

    private fun show(restoration: StateRestorationTester? = null) {
        startKoin {
            modules(module {
                single { newDuelBrands() }
                viewModel { AddPuzzleViewModel(pickerOf(listOf(CROWN_JEWELS, CROWN_TANGO, OLD_CROWN, MY_GRID)), NO_LOOKUP, releaseFlags = FakeReleaseFlags(FilterFlag.FilterSheets)) }
            })
        }
        val content: @Composable () -> Unit = {
            PuzzleBoxTheme {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    LineupPanel(editor, GAMES, includeFriends = false, onEdit = { edit -> editor = edit(editor) })
                }
            }
        }
        if (restoration != null) restoration.setContent(content) else rule.setContent(content)
        rule.waitForIdle()
        tap("duel.lineup.add")
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun systemBack() {
        val dialog = ShadowDialog.getLatestDialog()
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { dialog.onBackPressed() }
        rule.mainClock.advanceTimeBy(BACK_SETTLE_MS)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    @Test
    fun `a chip shows its options as a page in the sheet, with no second sheet and the tiles, search and chips gone`() {
        show()
        rule.onNodeWithTag(TILE).assertIsDisplayed()

        tap(SOURCE)

        rule.onNodeWithTag(PAGE).assertIsDisplayed()
        rule.onNodeWithTag(SHEET).assertIsDisplayed()
        assertTrue("no sheet over the sheet", !exists("$SOURCE.sheet"))
        listOf(TILE, "duel.lineup.search", SOURCE, "duel.lineup.filter.games").forEach { assertTrue("$it is replaced", !exists(it)) }
        val back = rule.onNodeWithTag("$PAGE.back").fetchSemanticsNode().touchBoundsInRoot
        val min = with(rule.density) { 48.dp.toPx() }
        assertTrue("Back is ${back.width}x${back.height}px", back.width >= min - 1 && back.height >= min - 1)
    }

    @Test
    fun `picks on the page narrow the list, and Back returns to the same chips and text with the sheet never closed`() {
        show()
        rule.onNodeWithTag("duel.lineup.search").performTextInput("crown")
        tap(SOURCE)

        tap("duel.lineup.chip.community")
        tap("duel.lineup.chip.friends")
        tap("$PAGE.back")

        rule.onNodeWithTag(SHEET).assertIsDisplayed()
        assertTrue("the page is gone", !exists(PAGE))
        rule.onNodeWithTag(SOURCE).assertTextContains("Source · 2")
        rule.onNodeWithTag("duel.lineup.search").assertTextContains("crown")
        rule.onNodeWithTag("duel.lineup.count").assertTextContains("3 puzzles")
    }

    @Test
    fun `system Back from a page returns to the chips and leaves the sheet up`() {
        show()
        tap(SOURCE)
        tap("duel.lineup.chip.mine")

        systemBack()

        rule.onNodeWithTag(SHEET).assertIsDisplayed()
        assertTrue("the page is gone", !exists(PAGE))
        assertTrue("the sheet was not asked to close", editor.target != null)
        rule.onNodeWithTag(SOURCE).assertTextContains("Mine")
    }

    @Test
    fun `system Back with no page closes the sheet, and the sheet opens on the tiles again`() {
        show()

        systemBack()

        assertTrue("the sheet closed", !exists(SHEET))
        tap("duel.lineup.add")
        rule.onNodeWithTag(TILE).assertIsDisplayed()
        assertTrue("it opens on the tiles, not the page", !exists(PAGE))
    }

    @Test
    fun `a rotation keeps the open page and what was picked on it`() {
        val restoration = StateRestorationTester(rule)
        show(restoration)
        tap(SOURCE)
        tap("duel.lineup.chip.mine")

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag(PAGE).assertIsDisplayed()
        tap("$PAGE.back")
        rule.onNodeWithTag(SOURCE).assertTextContains("Mine")
    }
}
