package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.newDuelBrands
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.PickablePuzzle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The add sheet's exact-puzzle search, through the lineup panel that opens it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ExactPuzzleSearchTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun stopKoinAgain() = stopKoin()

    private var editor by mutableStateOf(LineupEditor.opening(GAMES))

    private fun show(rows: List<PickablePuzzle>, offline: Boolean = false) {
        startKoin {
            modules(module {
                single { newDuelBrands() }
                viewModel { AddPuzzleViewModel(pickerOf(rows, offline)) }
            })
        }
        rule.setContent {
            PuzzleBoxTheme {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    LineupPanel(editor, GAMES, includeFriends = false, onEdit = { edit -> editor = edit(editor) })
                }
            }
        }
        rule.waitForIdle()
    }

    private fun openSheet() {
        rule.onNodeWithTag("duel.lineup.add").performClick()
        rule.waitForIdle()
    }

    @Test
    fun `typing crown, then New and Queens, finds the friend's puzzle, adds it, and its result row then reads Added`() {
        show(listOf(CROWN_JEWELS, CROWN_TANGO, OLD_CROWN, MY_GRID))
        openSheet()

        rule.onNodeWithTag("duel.lineup.search").performTextInput("crown")
        rule.onNodeWithTag("duel.lineup.chip.new").performClick()
        rule.onNodeWithTag("duel.lineup.chip.game.queens").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("1 found")
        val ref = CROWN_JEWELS.entry.ref.wire
        rule.onNodeWithTag("duel.lineup.pick.add.$ref").assertTextEquals("Add").assertIsEnabled()
        rule.onNodeWithTag("duel.lineup.pick.add.$ref").performClick()
        rule.waitForIdle()

        assertEquals(CROWN_JEWELS.entry, editor.draft.entries.last())
        rule.onNodeWithTag("duel.lineup.row.4").assertTextContains("Crown jewels", substring = true)
        rule.onNodeWithTag("duel.lineup.row.4").assertTextContains("Queens · by Bo · From Bo", substring = true)

        openSheet()
        rule.onNodeWithTag("duel.lineup.pick.add.$ref").assertTextEquals("Added").assertIsNotEnabled()
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("Newest first")
    }

    @Test
    fun `no match says so with the typed text, and with these filters once a chip is on`() {
        show(listOf(CROWN_JEWELS))
        openSheet()

        rule.onNodeWithTag("duel.lineup.search").performTextInput("zzz")
        rule.waitForIdle()
        rule.onNodeWithTag("duel.lineup.empty").assertTextEquals("Nothing matches \"zzz\". Try a code, or clear the filters.")

        rule.onNodeWithTag("duel.lineup.chip.community").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("duel.lineup.empty").assertTextEquals("Nothing matches \"zzz\" with these filters. Try a code, or clear the filters.")
    }

    @Test
    fun `offline puts the connection note under the field and keeps the cached rows`() {
        show(listOf(CROWN_JEWELS), offline = true)
        openSheet()

        rule.onNodeWithTag("duel.lineup.offline").assertTextEquals("You're offline. Code lookup needs a connection.")
        rule.onNodeWithTag("duel.lineup.pick.${CROWN_JEWELS.entry.ref.wire}").assertExists()
        rule.onNodeWithText("Crown jewels").assertExists()
    }

    @Test
    fun `the clear button empties the field and brings every puzzle back`() {
        show(listOf(CROWN_JEWELS, MY_GRID))
        openSheet()
        rule.onNodeWithTag("duel.lineup.search").performTextInput("grid")
        rule.waitForIdle()
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("1 found")

        rule.onNodeWithTag("duel.lineup.search.clear").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("Newest first")
    }

}
