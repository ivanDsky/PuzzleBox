package com.ivandsky.puzzlebox.compete.create.lineup

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.MINI
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.STRANDS
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.compete.create.newDuelBrands
import com.ivandsky.puzzlebox.compete.domain.lineup.PickablePuzzle
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.model.GameId
import org.junit.After
import org.junit.Assert.assertEquals
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

private const val TABLET = "w1280dp-h800dp-mdpi"
private val MIN_TARGET = 48.dp
private const val SOURCE = "duel.lineup.filter.source"
private const val GAMES_CHIP = "duel.lineup.filter.games"
private val SOLVED_COUNTS = mapOf(ZIP to 2, TANGO to 1, QUEENS to 1)

/** The Past you add sheet through the lineup panel: solved puzzles only, found by name, author or code on the device, with no lookup and no connection note. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PastYouAddSheetTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun noSheetAnimation() {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<Context>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private var editor by mutableStateOf(LineupEditor(LineupDraft(listOf(AnyGame(ZIP)))))

    private fun koinOf(solved: List<PickablePuzzle>, onMade: (AddPuzzleViewModel) -> Unit = {}) = startKoin {
        modules(module {
            single { newDuelBrands() }
            viewModel { AddPuzzleViewModel(pickerOf(emptyList()), NO_LOOKUP).also(onMade) }
            viewModel(PAST_YOU_PICKER) { AddPuzzleViewModel(pickerOf(solved), lookup = null, pastYou = true).also(onMade) }
        })
    }

    private fun show(solved: List<PickablePuzzle> = SOLVED_ROWS, counts: Map<GameId, Int>? = SOLVED_COUNTS) {
        koinOf(solved)
        rule.setContent {
            PuzzleBoxTheme {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    LineupPanel(editor, GAMES, includeFriends = false, onEdit = { edit -> editor = edit(editor) }, audience = LineupAudience.PastYou(counts))
                }
            }
        }
        rule.waitForIdle()
    }

    private fun openSheet() {
        rule.onNodeWithTag("duel.lineup.add").performClick()
        rule.waitForIdle()
    }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun touch(tag: String): Rect = rule.onNodeWithTag(tag).performScrollTo().fetchSemanticsNode().touchBoundsInRoot

    private fun addTag(puzzle: PickablePuzzle) = "duel.lineup.pick.add.${puzzle.entry.ref.wire}"

    @Test
    fun `the sheet has tiles with their counts, the Source chip with only Past days and From friends, the Games chip, and the solved list`() {
        show()
        openSheet()

        rule.onNodeWithTag("duel.lineup.sheet.game.${ZIP.value}").assertTextContains("Zip · 2 solved", substring = true).assertIsEnabled()
        rule.onNodeWithTag("duel.lineup.sheet.game.${MINI.value}").assertTextContains("Mini Sudoku · 0 solved", substring = true).assertIsNotEnabled()
        rule.onNodeWithTag(PickerFilterPage.Freshness.tag).assertDoesNotExist()
        rule.onNodeWithTag(SOURCE).assertExists()
        rule.onNodeWithTag(GAMES_CHIP).assertExists()
        rule.onNodeWithTag(SOURCE).performClick()
        rule.waitForIdle()
        listOf("pastdays", "friends").forEach { rule.onNodeWithTag("duel.lineup.chip.$it").assertExists() }
        listOf("new", "notplayed", "community", "mine").forEach { rule.onNodeWithTag("duel.lineup.chip.$it").assertDoesNotExist() }
        rule.onNodeWithTag("$SOURCE.page.back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(GAMES_CHIP).performClick()
        rule.waitForIdle()
        GAMES.forEach { rule.onNodeWithTag("duel.lineup.chip.game.${it.id.value}").assertExists() }
        rule.onNodeWithTag("$GAMES_CHIP.page.back").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("${SOLVED_ROWS.size} puzzles · Recently solved first")
        SOLVED_ROWS.forEach { rule.onNodeWithTag("duel.lineup.pick.${it.entry.ref.wire}").assertExists() }
        rule.onNodeWithTag("duel.lineup.offline").assertDoesNotExist()
        rule.onNodeWithTag("duel.lineup.lookup").assertDoesNotExist()
    }

    @Test
    fun `Add puts a solved daily and then a friend's puzzle in the lineup with where each came from`() {
        show()
        openSheet()

        rule.onNodeWithTag(addTag(ZIP_DAILY)).performClick()
        rule.waitForIdle()
        openSheet()
        rule.onNodeWithTag(addTag(FRIEND_TANGO)).performClick()
        rule.waitForIdle()

        assertEquals(listOf(AnyGame(ZIP), ZIP_DAILY.entry, FRIEND_TANGO.entry), editor.draft.entries)
        rule.onNodeWithTag("duel.lineup.row.2").assertTextContains("Zip #12", substring = true)
        rule.onNodeWithTag("duel.lineup.row.2").assertTextContains("Zip · Past day", substring = true)
        rule.onNodeWithTag("duel.lineup.row.3").assertTextContains("Crown Tango", substring = true)
        rule.onNodeWithTag("duel.lineup.row.3").assertTextContains("Tango · From Bo", substring = true)
        rule.onNodeWithTag("duel.lineup.add").assertTextContains("A game, or a puzzle you've solved", substring = true)
    }

    @Test
    fun `a puzzle already in the lineup reads Added and is off, the others stay on`() {
        editor = LineupEditor(LineupDraft(listOf(ZIP_DAILY.entry)))
        show()
        openSheet()

        rule.onNodeWithTag(addTag(ZIP_DAILY)).assertTextEquals("Added").assertIsNotEnabled()
        rule.onNodeWithTag(addTag(QUEENS_DAILY)).assertTextEquals("Add").assertIsEnabled()
    }

    @Test
    fun `with nothing solved the sheet says so and every tile is off`() {
        show(solved = emptyList(), counts = emptyMap())
        openSheet()

        rule.onNodeWithTag("duel.lineup.empty").assertTextEquals("You haven't solved any puzzles yet.")
        rule.onNodeWithTag("duel.lineup.count").assertDoesNotExist()
        GAMES.forEach { rule.onNodeWithTag("duel.lineup.sheet.game.${it.id.value}").assertIsNotEnabled() }
    }

    @Test
    fun `typing narrows the solved list, and a name or a code that is not in it says so with no lookup`() {
        show()
        openSheet()

        rule.onNodeWithTag("duel.lineup.search").performTextInput("tango")
        rule.waitForIdle()
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("1 puzzle")
        rule.onNodeWithTag(addTag(FRIEND_TANGO)).assertExists()
        rule.onNodeWithTag(addTag(ZIP_DAILY)).assertDoesNotExist()

        rule.onNodeWithTag("duel.lineup.search.clear").performClick()
        rule.onNodeWithTag("duel.lineup.search").performTextInput("ZZ9-QQ2")
        rule.waitForIdle()
        rule.onNodeWithTag("duel.lineup.empty").assertTextEquals("Nothing matches \"ZZ9-QQ2\". Past you only has puzzles you've solved.")
        rule.onNodeWithTag("duel.lineup.lookup").assertDoesNotExist()
        rule.onNodeWithTag("duel.lineup.lookup.retry").assertDoesNotExist()
    }

    @Test
    fun `Past you's chips narrow the solved list, and Clear brings every puzzle back`() {
        show()
        openSheet()

        rule.pickInPage(PickerFilterPage.Source, "duel.lineup.chip.pastdays")
        rule.pickInPage(PickerFilterPage.Games, "duel.lineup.chip.game.${ZIP.value}")
        rule.onNodeWithTag(SOURCE).assertTextContains("Past days")
        rule.onNodeWithTag(GAMES_CHIP).assertTextContains("Zip")
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("1 puzzle")
        rule.onNodeWithTag(addTag(ZIP_DAILY)).assertExists()
        rule.onNodeWithTag(addTag(FRIEND_TANGO)).assertDoesNotExist()

        rule.onNodeWithTag("duel.lineup.filter.clear").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.filter.clear").assertDoesNotExist()
        rule.onNodeWithTag(SOURCE).assertTextEquals("Source")
        SOLVED_ROWS.forEach { rule.onNodeWithTag(addTag(it)).assertExists() }
    }

    @Test
    fun `a phone's sheet is one column, tiles then field then the chips, every control at least 48 dp`() {
        show()
        openSheet()

        assertTrue("the field is under the tiles", bounds("duel.lineup.search").top >= bounds("duel.lineup.sheet.game.${STRANDS.value}").bottom)
        assertTrue("the chips are under the field", bounds(SOURCE).top >= bounds("duel.lineup.search").bottom)
        assertEveryControlIsAtLeast48dp()
    }

    @Test
    @Config(qualifiers = TABLET)
    fun `a tablet splits the sheet, the tiles on the left, the field and the chips on the right`() {
        show()
        openSheet()

        val left = listOf(bounds("duel.lineup.sheet.game.${ZIP.value}"))
        val right = listOf(bounds("duel.lineup.search"), bounds(SOURCE), bounds(GAMES_CHIP), bounds(addTag(ZIP_DAILY)))
        assertTrue("every left control ends before the right pane starts", left.maxOf { it.right } <= right.minOf { it.left })
        assertTrue("the chips are under the field", bounds(SOURCE).top >= bounds("duel.lineup.search").bottom)
        assertEveryControlIsAtLeast48dp()
    }

    @Test
    fun `a friend panel and a Past you panel on one screen each get their own search`() {
        val made = mutableListOf<AddPuzzleViewModel>()
        koinOf(SOLVED_ROWS, made::add)
        rule.setContent {
            PuzzleBoxTheme {
                Column {
                    LineupPanel(editor, GAMES, includeFriends = false, onEdit = {})
                    LineupPanel(editor, GAMES, includeFriends = false, onEdit = {}, audience = LineupAudience.PastYou(null))
                }
            }
        }
        rule.waitForIdle()

        assertEquals(listOf(false, true), made.map { it.state.value.pastYou })
    }

    private fun assertEveryControlIsAtLeast48dp() {
        val minPx = with(rule.density) { MIN_TARGET.toPx() }
        val tags = GAMES.map { "duel.lineup.sheet.game.${it.id.value}" } + listOf(SOURCE, GAMES_CHIP, "duel.lineup.search", "duel.lineup.search.paste") + SOLVED_ROWS.map(::addTag)
        for (tag in tags) {
            val target = touch(tag)
            assertTrue("$tag is ${target.width}x${target.height} px", target.width >= minPx - 1 && target.height >= minPx - 1)
        }
    }
}
