package com.ivandsky.puzzlebox.compete.create.lineup

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.newDuelBrands
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.PickablePuzzle
import org.junit.After
import org.junit.Assert.assertEquals
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

private const val SOURCE = "duel.lineup.filter.source"
private const val FRESHNESS = "duel.lineup.filter.freshness"
private const val GAMES_CHIP = "duel.lineup.filter.games"
private const val CLEAR = "duel.lineup.filter.clear"
private val ROWS = listOf(CROWN_JEWELS, CROWN_TANGO, OLD_CROWN, MY_GRID)

/** The Add puzzle page's filters: three "Pick several" chips that each open a sheet, and Clear. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PickerFilterChipsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun noSheetAnimation() {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<Context>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private fun show(rows: List<PickablePuzzle> = ROWS, pastYou: Boolean = false, fontScale: Float = 1f) {
        startKoin {
            modules(module {
                single { newDuelBrands() }
                viewModel {
                    AddPuzzleViewModel(pickerOf(rows), if (pastYou) null else NO_LOOKUP, pastYou = pastYou)
                }
            })
        }
        val picker = LineupPickerModel(position = 3, changing = false, games = GAMES, solved = if (pastYou) emptyMap() else null, added = emptySet(), pastYou = pastYou)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                PuzzleBoxTheme {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        PuzzlePickerPage(picker, onPick = {}, viewModel = koinViewModel<AddPuzzleViewModel>())
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun top(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    /** Opens [chip], taps [rows], and closes the sheet again. */
    private fun pick(chip: String, vararg rows: String) {
        tap(chip)
        rows.forEach { tap(it) }
        tap("$chip.sheet.close")
    }

    private fun assertIdle() {
        rule.onNodeWithTag(FRESHNESS).assertTextEquals("Freshness")
        rule.onNodeWithTag(SOURCE).assertTextEquals("Source")
        rule.onNodeWithTag(GAMES_CHIP).assertTextEquals("Games")
        assertTrue("Clear is gone", !exists(CLEAR))
    }

    @Test
    fun `the three chips are on the page, and Clear is not there until a row is picked`() {
        show()

        listOf(FRESHNESS, SOURCE, GAMES_CHIP).forEach { rule.onNodeWithTag(it).assertIsDisplayed() }
        assertTrue("no Clear with nothing picked", !exists(CLEAR))
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("4 puzzles · Newest first")
    }

    @Test
    fun `Source opens a sheet with its rows in order, that stays open while rows are picked and narrows the list live`() {
        show()

        tap(SOURCE)

        rule.onNodeWithTag("$SOURCE.sheet").assertIsDisplayed()
        rule.onNodeWithTag("$SOURCE.sheet.close").assertIsDisplayed()
        val order = listOf("$SOURCE.all", "duel.lineup.chip.community", "duel.lineup.chip.friends", "duel.lineup.chip.pastdays", "duel.lineup.chip.mine")
        assertEquals(order, order.sortedBy { top(it) })
        tap("duel.lineup.chip.community")
        tap("duel.lineup.chip.mine")
        rule.onNodeWithTag("$SOURCE.sheet").assertIsDisplayed()
        rule.onNodeWithTag(SOURCE).assertTextContains("Source · 2")
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("3 puzzles")
        assertTrue("the friend's puzzle left the list", !exists("duel.lineup.pick.${CROWN_JEWELS.entry.ref.wire}"))

        tap("$SOURCE.all")

        rule.onNodeWithTag("$SOURCE.sheet").assertIsDisplayed()
        rule.onNodeWithTag(SOURCE).assertTextEquals("Source")
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("4 puzzles · Newest first")
    }

    @Test
    fun `one value picked is the chip's text, a game by its name`() {
        show()

        pick(SOURCE, "duel.lineup.chip.mine")
        pick(GAMES_CHIP, "duel.lineup.chip.game.queens")
        pick(FRESHNESS, "duel.lineup.chip.new")

        rule.onNodeWithTag(SOURCE).assertTextContains("Mine")
        rule.onNodeWithTag(GAMES_CHIP).assertTextContains("Queens")
        rule.onNodeWithTag(FRESHNESS).assertTextContains("New")
    }

    @Test
    fun `Clear shows once a row is picked and puts all three groups back`() {
        show()
        pick(FRESHNESS, "duel.lineup.chip.new")
        pick(SOURCE, "duel.lineup.chip.community", "duel.lineup.chip.friends")
        pick(GAMES_CHIP, "duel.lineup.chip.game.queens")
        rule.onNodeWithTag(CLEAR).assertIsDisplayed()

        tap(CLEAR)

        assertIdle()
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("4 puzzles · Newest first")
    }

    @Test
    fun `Past you has no Freshness chip, only Past days and From friends under Source, and the games`() {
        show(pastYou = true)

        assertTrue("no Freshness", !exists(FRESHNESS))
        rule.onNodeWithTag(GAMES_CHIP).assertIsDisplayed()
        tap(SOURCE)
        val order = listOf("$SOURCE.all", "duel.lineup.chip.pastdays", "duel.lineup.chip.friends")
        assertEquals(order, order.sortedBy { top(it) })
        listOf("community", "mine").forEach { assertTrue("no $it", !exists("duel.lineup.chip.$it")) }
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("4 puzzles · Recently solved first")
    }

    @Test
    fun `an empty list offers Clear filters only while a chip is on, and it brings the list back`() {
        show()
        assertTrue("nothing picked", !exists("duel.lineup.empty.clear"))
        pick(FRESHNESS, "duel.lineup.chip.new")
        pick(SOURCE, "duel.lineup.chip.mine")
        assertTrue("New and Mine together match nothing", !exists("duel.lineup.pick.${MY_GRID.entry.ref.wire}"))

        rule.onNodeWithTag("duel.lineup.empty.clear").assertTextContains("Clear filters")
        tap("duel.lineup.empty.clear")

        assertTrue("the button left with the filters", !exists("duel.lineup.empty.clear"))
        rule.onNodeWithTag("duel.lineup.pick.${MY_GRID.entry.ref.wire}").assertIsDisplayed()
    }

    @Test
    fun `at 1_3 font scale every chip and row is 48 dp tall and Clear can still be reached by scrolling the row`() {
        show(fontScale = 1.3f)
        val minPx = with(rule.density) { 48.dp.toPx() }
        listOf(FRESHNESS, SOURCE, GAMES_CHIP).forEach {
            val bounds = rule.onNodeWithTag(it).fetchSemanticsNode().touchBoundsInRoot
            assertTrue("$it is ${bounds.height}px", bounds.height >= minPx - 1)
        }
        pick(SOURCE, "duel.lineup.chip.community")

        rule.onNodeWithTag(CLEAR).performScrollTo().assertIsDisplayed()
        tap(SOURCE)

        listOf("$SOURCE.all", "duel.lineup.chip.community", "duel.lineup.chip.friends", "duel.lineup.chip.pastdays", "duel.lineup.chip.mine").forEach {
            val bounds = rule.onNodeWithTag(it).fetchSemanticsNode().touchBoundsInRoot
            assertTrue("$it is ${bounds.height}px", bounds.height >= minPx - 1)
        }
    }
}
