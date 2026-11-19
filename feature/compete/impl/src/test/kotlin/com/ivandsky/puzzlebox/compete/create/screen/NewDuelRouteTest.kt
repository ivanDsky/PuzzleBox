package com.ivandsky.puzzlebox.compete.create.screen

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.compete.api.NewDuelKey
import com.ivandsky.puzzlebox.compete.create.NewDuelHarness
import com.ivandsky.puzzlebox.compete.create.Opponent
import com.ivandsky.puzzlebox.compete.create.newDuelModule
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The New duel screen on its real view model: the key's challenge fills it in, and each way out ends where it should. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class NewDuelRouteTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val games = Navigator(NavigationState(PlayKey, listOf(listOf(PlayKey))))

    @After
    fun stopKoinAgain() = stopKoin()

    private fun show(key: NewDuelKey = NewDuelKey(), harness: NewDuelHarness = NewDuelHarness()): NewDuelHarness {
        startKoin { modules(newDuelModule(), module { viewModel { harness.viewModel } }) }
        games.pushOnce(key)
        rule.setContent { PuzzleBoxTheme { NewDuelRoute(key, games) } }
        rule.waitForIdle()
        return harness
    }

    private fun stack() = games.state.currentStack

    @Test
    fun `the screen shows the form and Back pops to where it was opened`() {
        show()

        rule.onNodeWithTag("duel.new.screen").assertIsDisplayed()
        rule.onNodeWithTag("duel.create").assertIsDisplayed()

        rule.onNodeWithTag("duel.new.back").performClick()
        rule.waitForIdle()

        assertEquals(listOf(PlayKey), stack())
    }

    @Test
    fun `a challenge fills the form in with its friend`() {
        show(NewDuelKey("sam", "Sam", "alpha"))

        rule.onNodeWithTag("duel.invitee").assertTextContains("Sam", substring = true)
    }

    @Test
    fun `a created friend duel shows Duel created over the screen and Done pops`() {
        show()

        rule.onNodeWithTag("duel.create").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("duel.created.sheet").fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithTag("duel.created.done").performClick()
        rule.waitForIdle()

        assertEquals(listOf(PlayKey), stack())
    }

    @Test
    fun `a duel against Past you replaces the screen with that duel's own`() {
        val harness = NewDuelHarness().also { it.solveSome() }
        show(harness = harness)
        rule.runOnIdle { harness.viewModel.chooseOpponent(Opponent.PastYou) }
        rule.waitForIdle()

        rule.onNodeWithTag("duel.create").performClick()
        rule.waitUntil(5_000) { (stack().last() as? DuelKey) != null }

        val code = harness.self.observeAll().let { runBlocking { it.first() } }.single().code
        assertEquals(listOf(PlayKey, DuelKey(code.value)), stack())
    }

    @Test
    fun `a signed out player meets sign-in first and Cancel pops`() {
        show(harness = NewDuelHarness(signedIn = false))

        rule.waitUntil(5_000) { rule.onAllNodesWithTag("duel.signIn.cancel").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("duel.new.screen").assertIsDisplayed()

        rule.onNodeWithTag("duel.signIn.cancel").performClick()
        rule.waitForIdle()

        assertEquals(listOf(PlayKey), stack())
    }
}
