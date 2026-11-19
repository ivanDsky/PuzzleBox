package com.ivandsky.puzzlebox.compete.create.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.ivandsky.puzzlebox.compete.api.NewDuelKey
import com.ivandsky.puzzlebox.compete.create.NewDuelHarness
import com.ivandsky.puzzlebox.compete.create.Opponent
import com.ivandsky.puzzlebox.compete.create.lineup.AddPuzzleViewModel
import com.ivandsky.puzzlebox.compete.create.lineup.NO_LOOKUP
import com.ivandsky.puzzlebox.compete.create.lineup.pickable
import com.ivandsky.puzzlebox.compete.create.lineup.pickerOf
import com.ivandsky.puzzlebox.compete.create.newDuelModule
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.module.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val CROWN = pickable(alpha, "K7Q2MX", "Crown jewels", "Bo", PuzzleSource.Friend, isNew = true)
private val ELSEWHERE = pickable(alpha, "R6S7T8", "Plain grid", "Di", PuzzleSource.Community)

/** The New duel screen on its real view model and the real search view models: the picker page opens from Add, picks into the lineup, and starts clean each time. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class NewDuelHostPickerTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val games = Navigator(NavigationState(PlayKey, listOf(listOf(PlayKey))))

    @After
    fun stopKoinAgain() = stopKoin()

    private fun show(harness: NewDuelHarness = NewDuelHarness()): NewDuelHarness {
        startKoin {
            modules(
                newDuelModule(),
                module {
                    viewModel { harness.viewModel }
                    viewModel { AddPuzzleViewModel(pickerOf(listOf(CROWN, ELSEWHERE)), NO_LOOKUP) }
                },
            )
        }
        val key = NewDuelKey()
        games.pushOnce(key)
        rule.setContent { PuzzleBoxTheme { NewDuelRoute(key, games) } }
        rule.waitForIdle()
        return harness
    }

    private fun openAdd() {
        rule.onNodeWithTag("duel.lineup.add").performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun customize() {
        rule.onNodeWithTag("duel.preset.custom").performClick()
        rule.waitForIdle()
    }

    private fun rows(harness: NewDuelHarness) = harness.viewModel.state.value.lineup.draft.size

    @Test
    fun `Add puzzle shows the page, a pick adds the puzzle to the real lineup and closes it`() {
        val harness = show()
        customize()
        val before = rows(harness)

        openAdd()
        rule.onNodeWithTag("duel.lineup.picker").assertIsDisplayed()
        rule.onNodeWithTag("duel.lineup.pick.add.${CROWN.entry.ref.wire}").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.picker").assertDoesNotExist()
        assertEquals(before + 1, rows(harness))
        assertEquals(CROWN.entry, harness.viewModel.state.value.lineup.draft.entries.last())
        rule.onNodeWithTag("duel.create").assertIsDisplayed()
    }

    @Test
    fun `what was typed is gone the next time the page opens, and Back leaves the lineup as it was`() {
        val harness = show()
        customize()
        val before = rows(harness)
        openAdd()
        rule.onNodeWithTag("duel.lineup.search").performTextInput("crown")
        rule.waitForIdle()
        rule.onNodeWithTag("duel.lineup.count").assertTextContains("1 puzzle")

        rule.onNodeWithTag("duel.lineup.picker.back").performClick()
        rule.waitForIdle()
        assertEquals(before, rows(harness))
        openAdd()

        rule.onNodeWithTag("duel.lineup.search").assertTextContains("")
        rule.onNodeWithTag("duel.lineup.count").assertTextContains("2 puzzles · Newest first")
    }

    @Test
    fun `Past you's page reads the solved puzzles, not the friend's list`() {
        val harness = NewDuelHarness().also { it.solveSome() }
        show(harness)
        rule.runOnIdle { harness.viewModel.chooseOpponent(Opponent.PastYou) }
        rule.waitForIdle()
        customize()

        openAdd()

        rule.onNodeWithTag("duel.lineup.picker").assertIsDisplayed()
        rule.onNodeWithTag("duel.lineup.count").assertTextContains("Recently solved first", substring = true)
    }
}
