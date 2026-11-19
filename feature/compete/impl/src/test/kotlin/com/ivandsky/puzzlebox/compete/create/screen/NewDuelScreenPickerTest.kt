package com.ivandsky.puzzlebox.compete.create.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.create.DuelPreset
import com.ivandsky.puzzlebox.compete.create.NewDuelActions
import com.ivandsky.puzzlebox.compete.create.NewDuelState
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.compete.create.custom
import com.ivandsky.puzzlebox.compete.create.friend
import com.ivandsky.puzzlebox.compete.create.lineup.AddPuzzleViewModel
import com.ivandsky.puzzlebox.compete.create.lineup.CROWN_JEWELS
import com.ivandsky.puzzlebox.compete.create.lineup.LineupDraft
import com.ivandsky.puzzlebox.compete.create.lineup.LineupEditor
import com.ivandsky.puzzlebox.compete.create.lineup.NO_LOOKUP
import com.ivandsky.puzzlebox.compete.create.lineup.OLD_CROWN
import com.ivandsky.puzzlebox.compete.create.lineup.PAST_YOU_PICKER
import com.ivandsky.puzzlebox.compete.create.lineup.PuzzlePickerPage
import com.ivandsky.puzzlebox.compete.create.lineup.pickerOf
import com.ivandsky.puzzlebox.compete.create.newDuelModule
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
import org.robolectric.annotation.GraphicsMode

private const val FLASH_MS = 600L
private val MAX_WIDTH = 640.dp

/**
 * The picker as a page of the New duel screen: Add or a row's tap swaps the form for it, Back returns to the form as it
 * was, and a pick closes it. The state is held here and edited by the screen's own actions, as the view model does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class NewDuelScreenPickerTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun stopKoinAgain() = stopKoin()

    private var backs = 0
    private var state by mutableStateOf(friendCustomizing(2))

    private fun friendCustomizing(rows: Int): NewDuelState = friend().let {
        it.copy(draft = it.draft.copy(preset = DuelPreset.Custom), lineup = LineupEditor(LineupDraft(List(rows) { i -> AnyGame(if (i % 2 == 0) ZIP else TANGO) })))
    }

    private val actions = NewDuelActions(
        onQuick = {}, onDaily = {}, onToggle = {}, onDeadline = {}, onFriends = {}, onOpponent = {},
        onCreate = {}, onGoogle = {}, onCancel = {},
        onEditLineup = { edit -> state = state.copy(lineup = edit(state.lineup)) },
    )

    @Composable
    private fun Screen(pane: Boolean = false) = PuzzleBoxTheme {
        NewDuelScreen(state, actions, onBack = { backs++ }, pane = pane, picker = { m, onClose ->
            PuzzlePickerPage(
                picker = m,
                onClose = onClose,
                onPick = { entry -> state = state.copy(lineup = state.lineup.pick(entry)) },
                viewModel = if (m.pastYou) koinViewModel<AddPuzzleViewModel>(qualifier = PAST_YOU_PICKER) else koinViewModel<AddPuzzleViewModel>(),
            )
        })
    }

    private fun show(pane: Boolean = false, restoration: StateRestorationTester? = null) {
        startKoin { modules(newDuelModule(), module { viewModel { AddPuzzleViewModel(pickerOf(listOf(CROWN_JEWELS, OLD_CROWN)), NO_LOOKUP) } }) }
        if (restoration != null) restoration.setContent { Screen(pane) } else rule.setContent { Screen(pane) }
        rule.onNodeWithTag("duel.preset.custom").performClick()
        rule.waitForIdle()
    }

    private fun openAdd() {
        rule.onNodeWithTag("duel.lineup.add").performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun systemBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun `Add puzzle shows the page in place of the form and its Create bar, titled by its row`() {
        show()
        openAdd()

        rule.onNodeWithTag("duel.lineup.picker").assertIsDisplayed()
        rule.onNodeWithTag("duel.new.form").assertDoesNotExist()
        rule.onNodeWithTag("duel.new.createBar").assertDoesNotExist()
        rule.onNodeWithText("Add puzzle 3").assertIsDisplayed()
        rule.onNodeWithText("New duel").assertDoesNotExist()
        rule.onNodeWithTag("duel.new.back").assertDoesNotExist()
        rule.onNodeWithTag("duel.lineup.picker.back").assertIsDisplayed()
    }

    @Test
    fun `a tap on a row shows the page as Change puzzle with that row's number`() {
        show()

        rule.onNodeWithTag("duel.lineup.row.2").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.picker").assertIsDisplayed()
        rule.onNodeWithText("Change puzzle 2").assertIsDisplayed()
    }

    @Test
    fun `the arrow in the bar closes the page with the lineup as it was and Customize still open, and leaves the screen alone`() {
        show()
        val before = state.lineup.draft
        openAdd()

        rule.onNodeWithTag("duel.lineup.picker.back").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.picker").assertDoesNotExist()
        rule.onNodeWithTag("duel.lineup.add").assertExists()
        rule.onNodeWithTag("duel.new.createBar").assertIsDisplayed()
        assertEquals(before, state.lineup.draft)
        assertNull(state.lineup.target)
        assertEquals(0, backs)
        rule.onNodeWithTag("duel.new.back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `the system Back closes the page, and only the page`() {
        show()
        val before = state.lineup.draft
        openAdd()

        systemBack()

        rule.onNodeWithTag("duel.lineup.picker").assertDoesNotExist()
        rule.onNodeWithTag("duel.lineup.add").assertExists()
        assertEquals(before, state.lineup.draft)
        assertEquals(0, backs)
    }

    @Test
    fun `in the tablet's pane the page's arrow is Back, and the form's is Close again once it is closed`() {
        show(pane = true)
        openAdd()

        rule.onNodeWithTag("duel.new.close").assertDoesNotExist()
        rule.onNodeWithTag("duel.lineup.picker.back").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.new.close").assertIsDisplayed()
        assertEquals(0, backs)
    }

    @Test
    fun `closing the page leaves the form scrolled where it was`() {
        state = friendCustomizing(9)
        show()
        val unscrolled = rule.onNodeWithTag("duel.lineup.add").getUnclippedBoundsInRoot().top
        rule.onNodeWithTag("duel.lineup.add").performScrollTo()
        rule.waitForIdle()
        val before = rule.onNodeWithTag("duel.lineup.add").getUnclippedBoundsInRoot().top
        assertTrue("the form did not scroll: the Add row stayed at $unscrolled", before < unscrolled)

        rule.onNodeWithTag("duel.lineup.add").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("duel.lineup.picker.back").performClick()
        rule.waitForIdle()

        assertEquals("the form came back scrolled to a different place", before.value, rule.onNodeWithTag("duel.lineup.add").getUnclippedBoundsInRoot().top.value, 0.5f)
    }

    @Test
    fun `a game tile adds an Any row, closes the page and flashes the row, and the flash then clears`() {
        show()
        openAdd()

        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("duel.lineup.sheet.game.${QUEENS.value}").performClick()
        rule.mainClock.advanceTimeBy(300)

        assertEquals(listOf(AnyGame(ZIP), AnyGame(TANGO), AnyGame(QUEENS)), state.lineup.draft.entries)
        assertNull(state.lineup.target)
        assertEquals(2, state.lineup.flashed)
        rule.onNodeWithTag("duel.lineup.picker").assertDoesNotExist()
        rule.onNodeWithTag("duel.lineup.row.3").assertExists()

        rule.mainClock.advanceTimeBy(FLASH_MS + 300)
        assertNull(state.lineup.flashed)
        rule.mainClock.autoAdvance = true
    }

    @Test
    fun `an exact puzzle adds that puzzle, and it reads Added the next time the page opens`() {
        show()
        openAdd()

        rule.onNodeWithTag("duel.lineup.pick.add.${CROWN_JEWELS.entry.ref.wire}").performClick()
        rule.waitForIdle()

        assertEquals(CROWN_JEWELS.entry, state.lineup.draft.entries.last())
        rule.onNodeWithTag("duel.lineup.picker").assertDoesNotExist()
        openAdd()
        rule.onNodeWithTag("duel.lineup.pick.add.${CROWN_JEWELS.entry.ref.wire}").assertTextContains("Added")
    }

    @Test
    fun `Change replaces the row it was opened on`() {
        show()
        rule.onNodeWithTag("duel.lineup.row.1").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.sheet.game.${QUEENS.value}").performClick()
        rule.waitForIdle()

        assertEquals(listOf(AnyGame(QUEENS), AnyGame(TANGO)), state.lineup.draft.entries)
        rule.onNodeWithTag("duel.lineup.picker").assertDoesNotExist()
    }

    @Test
    fun `a rotation under the open page keeps the page and what was typed`() {
        val restoration = StateRestorationTester(rule)
        show(restoration = restoration)
        openAdd()
        rule.onNodeWithTag("duel.lineup.search").performTextInput("crown")
        rule.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.picker").assertIsDisplayed()
        rule.onNodeWithTag("duel.lineup.search").assertTextContains("crown")
        rule.onNodeWithTag("duel.lineup.count").assertTextContains("2 puzzles")
    }

    @Test
    fun `Past you's page reads the solved puzzles`() {
        state = custom()
        show()
        openAdd()

        rule.onNodeWithTag("duel.lineup.picker").assertIsDisplayed()
        rule.onNodeWithTag("duel.lineup.count").assertTextContains("Recently solved first", substring = true)
    }

    @Test
    @Config(qualifiers = "w800dp-h800dp-mdpi")
    fun `on a window under 840 dp the page stops at 640 dp and sits in the middle`() {
        show()
        openAdd()

        val page = rule.onNodeWithTag("duel.lineup.picker").getUnclippedBoundsInRoot()
        val screen = rule.onNodeWithTag("duel.new.screen").getUnclippedBoundsInRoot()
        assertEquals(MAX_WIDTH.value, (page.right - page.left).value, 0.5f)
        assertEquals("centred", ((screen.right - screen.left) - (page.right - page.left)).value / 2, page.left.value, 0.5f)
    }
}
