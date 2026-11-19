package com.ivandsky.puzzlebox.compete.create.screen

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.compete.create.DuelPreset
import com.ivandsky.puzzlebox.compete.create.NewDuelActions
import com.ivandsky.puzzlebox.compete.create.NewDuelState
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.compete.create.friend
import com.ivandsky.puzzlebox.compete.create.lineup.AddPuzzleViewModel
import com.ivandsky.puzzlebox.compete.create.lineup.CROWN_JEWELS
import com.ivandsky.puzzlebox.compete.create.lineup.LineupDraft
import com.ivandsky.puzzlebox.compete.create.lineup.LineupEditor
import com.ivandsky.puzzlebox.compete.create.lineup.NO_LOOKUP
import com.ivandsky.puzzlebox.compete.create.lineup.OLD_CROWN
import com.ivandsky.puzzlebox.compete.create.lineup.PuzzlePickerPage
import com.ivandsky.puzzlebox.compete.create.lineup.pickerOf
import com.ivandsky.puzzlebox.compete.create.newDuelModule
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

private const val SLACK = 1f
private const val FORM_SHARE = 1f / 2f

/** The New duel screen at 840 dp and wider: the picker opens beside the form, which stays live; under it the page swap stays. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w900dp-h800dp-mdpi")
class NewDuelScreenWideTest {
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
    private fun Screen() = PuzzleBoxTheme {
        NewDuelScreen(state, actions, onBack = { backs++ }, picker = { m, onClose ->
            PuzzlePickerPage(
                picker = m,
                onPick = { entry -> state = state.copy(lineup = state.lineup.pick(entry)) },
                onClose = onClose,
                viewModel = koinViewModel<AddPuzzleViewModel>(),
            )
        })
    }

    private fun show(restoration: StateRestorationTester? = null) {
        startKoin { modules(newDuelModule(), module { viewModel { AddPuzzleViewModel(pickerOf(listOf(CROWN_JEWELS, OLD_CROWN)), NO_LOOKUP) } }) }
        if (restoration != null) restoration.setContent { Screen() } else rule.setContent { Screen() }
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

    /** The picker's header line; the form's own Add row reads the same words. */
    private fun header(text: String) = rule.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag("duel.new.form")).not())

    private fun tagged(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    @Test
    fun `a targeted row shows the form and the picker side by side, the bar still New duel and the picker headed by its row`() {
        show()
        openAdd()

        rule.onNodeWithTag("duel.new.wide").assertIsDisplayed()
        rule.onNodeWithTag("duel.new.form").assertIsDisplayed()
        rule.onNodeWithTag("duel.new.createBar").assertIsDisplayed()
        rule.onNodeWithTag("duel.lineup.picker").assertIsDisplayed()
        rule.onNodeWithText("New duel").assertIsDisplayed()
        header("Add puzzle 3").assertCountEquals(1)
        rule.onNodeWithTag("duel.lineup.picker.back").assertDoesNotExist()
        assertEquals("the form takes half", 900 * FORM_SHARE, tagged("duel.new.form").width.value, SLACK)
        assertEquals("the picker takes the rest", 900 * (1 - FORM_SHARE), tagged("duel.lineup.picker").width.value, SLACK + 1)
    }

    @Test
    fun `with no row targeted the form is alone, 640 dp at most and centred`() {
        show()

        rule.onNodeWithTag("duel.new.wide").assertIsDisplayed()
        rule.onNodeWithTag("duel.lineup.picker").assertDoesNotExist()
        val form = tagged("duel.new.form")
        assertEquals(640f, form.width.value, SLACK)
        assertEquals("centred", (900 - form.width.value) / 2, form.left.value, SLACK)
    }

    @Test
    fun `a pick adds the row, closes the picker and leaves the form alone again`() {
        show()
        openAdd()

        rule.onNodeWithTag("duel.lineup.sheet.game.${QUEENS.value}").performClick()
        rule.waitForIdle()

        assertEquals(listOf(AnyGame(ZIP), AnyGame(TANGO), AnyGame(QUEENS)), state.lineup.draft.entries)
        rule.onNodeWithTag("duel.lineup.picker").assertDoesNotExist()
        rule.onNodeWithTag("duel.new.form").assertIsDisplayed()
        assertEquals(640f, tagged("duel.new.form").width.value, SLACK)
    }

    @Test
    fun `a tap on another row while the picker is open retargets it, and Add retargets to a new row`() {
        show()
        openAdd()

        rule.onNodeWithTag("duel.lineup.row.2").performScrollTo().performClick()
        rule.waitForIdle()
        header("Change puzzle 2").assertCountEquals(1)

        rule.onNodeWithTag("duel.lineup.add").performScrollTo().performClick()
        rule.waitForIdle()
        header("Add puzzle 3").assertCountEquals(1)
        rule.onNodeWithTag("duel.lineup.picker").assertIsDisplayed()
    }

    @Test
    fun `Create stays usable while the picker is open`() {
        show()
        openAdd()

        rule.onNodeWithTag("duel.create").assertIsDisplayed()
    }

    @Test
    fun `the picker's Close closes it and keeps the draft, and leaves the screen`() {
        show()
        val before = state.lineup.draft
        openAdd()

        rule.onNodeWithTag("duel.lineup.picker.close").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.picker").assertDoesNotExist()
        assertEquals(before, state.lineup.draft)
        assertNull(state.lineup.target)
        assertEquals(0, backs)
    }

    @Test
    fun `the system Back closes the picker first and the screen only the next time`() {
        show()
        val before = state.lineup.draft
        openAdd()

        systemBack()

        rule.onNodeWithTag("duel.lineup.picker").assertDoesNotExist()
        assertEquals(before, state.lineup.draft)
        assertEquals(0, backs)
        rule.onNodeWithTag("duel.new.back").performClick()
        assertEquals(1, backs)
    }

    @Test
    @Config(qualifiers = "w700dp-h800dp-mdpi")
    fun `under 840 dp the picker is the page swap, not the wide layout`() {
        show()
        openAdd()

        rule.onNodeWithTag("duel.new.wide").assertDoesNotExist()
        rule.onNodeWithTag("duel.lineup.picker").assertIsDisplayed()
        rule.onNodeWithTag("duel.new.form").assertDoesNotExist()
        rule.onNodeWithTag("duel.lineup.picker.close").assertDoesNotExist()
        rule.onNodeWithText("Add puzzle 3").assertIsDisplayed()
    }

    @Test
    fun `a rotation keeps the picker open on the same row`() {
        val restoration = StateRestorationTester(rule)
        show(restoration = restoration)
        rule.onNodeWithTag("duel.lineup.row.1").performScrollTo().performClick()
        rule.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.picker").assertIsDisplayed()
        header("Change puzzle 1").assertCountEquals(1)
    }
}
