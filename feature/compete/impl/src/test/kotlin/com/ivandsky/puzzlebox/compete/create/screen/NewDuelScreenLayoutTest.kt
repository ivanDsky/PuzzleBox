package com.ivandsky.puzzlebox.compete.create.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.create.DuelStep
import com.ivandsky.puzzlebox.compete.create.NewDuelState
import com.ivandsky.puzzlebox.compete.create.NO_ACTIONS
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.compete.create.custom
import com.ivandsky.puzzlebox.compete.create.friend
import com.ivandsky.puzzlebox.compete.create.lineup.LineupDraft
import com.ivandsky.puzzlebox.compete.create.lineup.LineupEditor
import com.ivandsky.puzzlebox.compete.create.newDuelModule
import com.ivandsky.puzzlebox.compete.create.noneSolved
import com.ivandsky.puzzlebox.compete.create.quick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val MIN_TARGET = 48.dp
private const val SLACK = 0.5f

/** Where the New duel page puts things: the title in the bar, the form scrolling, the Create bar pinned and capped in width. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class NewDuelScreenLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    @Before
    fun startKoinWithBrands() {
        startKoin { modules(newDuelModule()) }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private fun show(state: NewDuelState) = rule.setContent {
        PuzzleBoxTheme { NewDuelScreen(state, NO_ACTIONS, onBack = {}) }
    }

    private fun tenRows() = custom().let { it.copy(lineup = LineupEditor(LineupDraft(List(10) { AnyGame(ZIP) }))) }

    private fun tagged(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun `New duel is named once, by the bar`() {
        show(friend())

        rule.onAllNodesWithText("New duel").assertCountEquals(1)
    }

    @Test
    fun `a long lineup scrolls under the bar and Create stays on screen`() {
        show(tenRows())
        rule.onNodeWithTag("duel.preset.custom").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.create").assertIsDisplayed()
        val screen = tagged("duel.new.screen")
        assertTrue(tagged("duel.new.createBar").bottom <= screen.bottom + SLACK.dp)
        assertTrue("the form is taller than the room above the bar", tagged("duel.new.form").height > tagged("duel.new.createBar").top)
        assertTrue(tagged("duel.create").height >= MIN_TARGET)
    }

    @Test
    fun `a short form sits at the top and the bar at the bottom`() {
        show(friend())

        val screen = tagged("duel.new.screen")
        val bar = tagged("duel.new.createBar")
        assertTrue(bar.bottom >= screen.bottom - bar.height / 2 - SLACK.dp && bar.bottom <= screen.bottom)
        assertTrue(tagged("duel.new.form").top < screen.top + 96.dp)
        assertTrue(tagged("duel.create").bottom > bar.bottom - 24.dp)
    }

    @Test
    fun `Past you with no solved puzzles shows its reason and a disabled Create`() {
        show(noneSolved())
        rule.onNodeWithTag("duel.new.createBar").assertIsDisplayed()

        rule.onNodeWithTag("duel.self.reason").assertIsDisplayed()
        rule.onNodeWithTag("duel.create").assertIsNotEnabled()
        rule.onAllNodesWithTag("duel.summary").assertCountEquals(0)
    }

    @Test
    fun `a friend duel shows the summary line in the bar`() {
        show(friend())

        rule.onNodeWithTag("duel.summary").assertIsDisplayed()
        rule.onAllNodesWithTag("duel.self.reason").assertCountEquals(0)
    }

    @Test
    fun `the sign-in step has no Create bar`() {
        show(friend().copy(step = DuelStep.SignIn))

        rule.onNodeWithTag("duel.signIn.google").assertIsDisplayed()
        rule.onAllNodesWithTag("duel.new.createBar").assertCountEquals(0)
        rule.onAllNodesWithTag("duel.new.form").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w924dp-h891dp-420dpi")
    fun `on a wide window the form and the bar are 640dp at most and centred`() {
        show(quick())

        val wide = tagged("duel.new.screen").width
        listOf("duel.new.form", "duel.new.createBar").forEach { tag ->
            val bounds = tagged(tag)
            assertEquals("$tag width", 640f, bounds.width.value, SLACK)
            assertEquals("$tag centred", ((wide - bounds.width) / 2).value, bounds.left.value, SLACK)
        }
    }
}
