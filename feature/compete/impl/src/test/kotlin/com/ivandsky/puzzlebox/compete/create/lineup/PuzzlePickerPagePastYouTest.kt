package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.MINI
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.compete.create.newDuelBrands
import com.ivandsky.puzzlebox.compete.domain.lineup.PickablePuzzle
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import com.ivandsky.puzzlebox.model.GameId
import org.junit.After
import org.junit.Assert.assertEquals
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

private val SOLVED_COUNTS = mapOf(ZIP to 2, TANGO to 1, QUEENS to 1)

/** The Add puzzle page for Past you: tiles with what was solved, and a list of solved puzzles found on the device, with no lookup and no connection note. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PuzzlePickerPagePastYouTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun stopKoinAgain() = stopKoin()

    private val picked = mutableListOf<LineupEntry>()

    private fun show(solved: List<PickablePuzzle> = SOLVED_ROWS, counts: Map<GameId, Int>? = SOLVED_COUNTS, added: Set<LegRef> = emptySet()) {
        startKoin {
            modules(module {
                single { newDuelBrands() }
                viewModel(PAST_YOU_PICKER) { AddPuzzleViewModel(pickerOf(solved), lookup = null, pastYou = true) }
            })
        }
        val picker = LineupPickerModel(position = 2, changing = false, games = GAMES, solved = counts, added = added, pastYou = true)
        rule.setContent {
            PuzzleBoxTheme {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    PuzzlePickerPage(picker, onPick = { picked += it }, viewModel = koinViewModel<AddPuzzleViewModel>(qualifier = PAST_YOU_PICKER))
                }
            }
        }
        rule.waitForIdle()
    }

    private fun addTag(puzzle: PickablePuzzle) = "duel.lineup.pick.add.${puzzle.entry.ref.wire}"

    @Test
    fun `tiles say how many were solved and are off at none, and the list is what was solved`() {
        show()

        rule.onNodeWithTag("duel.lineup.sheet.game.${ZIP.value}").assertTextContains("Zip · 2 solved", substring = true).assertIsEnabled()
        rule.onNodeWithTag("duel.lineup.sheet.game.${MINI.value}").assertTextContains("Mini Sudoku · 0 solved", substring = true).assertIsNotEnabled()
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("${SOLVED_ROWS.size} puzzles · Recently solved first")
        SOLVED_ROWS.forEach { rule.onNodeWithTag("duel.lineup.pick.${it.entry.ref.wire}").assertExists() }
        rule.onNodeWithTag("duel.lineup.offline").assertDoesNotExist()
        rule.onNodeWithTag("duel.lineup.lookup").assertDoesNotExist()
    }

    @Test
    fun `a tile picks its game, and Add picks a solved daily and then a friend's puzzle as they are`() {
        show()

        rule.onNodeWithTag("duel.lineup.sheet.game.${ZIP.value}").performClick()
        rule.onNodeWithTag(addTag(ZIP_DAILY)).performClick()
        rule.onNodeWithTag(addTag(FRIEND_TANGO)).performClick()

        assertEquals(listOf<LineupEntry>(AnyGame(ZIP), ZIP_DAILY.entry, FRIEND_TANGO.entry), picked)
    }

    @Test
    fun `a puzzle already in the lineup reads Added and is off, the others stay on`() {
        show(added = setOf(ZIP_DAILY.entry.ref))

        rule.onNodeWithTag(addTag(ZIP_DAILY)).assertTextEquals("Added").assertIsNotEnabled()
        rule.onNodeWithTag(addTag(QUEENS_DAILY)).assertTextEquals("Add").assertIsEnabled()
    }

    @Test
    fun `with nothing solved the page says so and every tile is off`() {
        show(solved = emptyList(), counts = emptyMap())

        rule.onNodeWithTag("duel.lineup.empty").assertTextEquals("You haven't solved any puzzles yet.")
        rule.onNodeWithTag("duel.lineup.count").assertDoesNotExist()
        GAMES.forEach { rule.onNodeWithTag("duel.lineup.sheet.game.${it.id.value}").assertIsNotEnabled() }
    }

    @Test
    fun `while the solved counts are unread no tile is off`() {
        show(counts = null)

        GAMES.forEach { rule.onNodeWithTag("duel.lineup.sheet.game.${it.id.value}").assertIsEnabled() }
    }

    @Test
    fun `a name or code that is not among the solved puzzles says so, with no lookup and no Retry`() {
        show()

        rule.onNodeWithTag("duel.lineup.search").performTextInput("ZZ9-QQ2")
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.empty").assertTextEquals("Nothing matches \"ZZ9-QQ2\". Past you only has puzzles you've solved.")
        rule.onNodeWithTag("duel.lineup.lookup").assertDoesNotExist()
        rule.onNodeWithTag("duel.lineup.lookup.retry").assertDoesNotExist()
    }
}
