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
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.compete.create.DuelNotice
import com.ivandsky.puzzlebox.compete.create.DuelPreset
import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.NO_ACTIONS
import com.ivandsky.puzzlebox.compete.create.NewDuelActions
import com.ivandsky.puzzlebox.compete.create.NewDuelContent
import com.ivandsky.puzzlebox.compete.create.NewDuelState
import com.ivandsky.puzzlebox.compete.create.Notice
import com.ivandsky.puzzlebox.compete.create.Opponent
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.SelfDuelForm
import com.ivandsky.puzzlebox.compete.create.SelfDuelView
import com.ivandsky.puzzlebox.compete.create.SolvedCounts
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.compete.create.newDuelBrands
import com.ivandsky.puzzlebox.compete.create.state
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import org.junit.After
import org.junit.Assert.assertEquals
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

private val SOLVED = mapOf(ZIP to 2, QUEENS to 1)

/** The lineup panel for Past you: Any rows over the player's solved puzzles, a sheet whose tiles say their solved counts, and the Customize card in the sheet. The search of the sheet is in [PastYouAddSheetTest]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PastYouLineupPanelTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun startKoinWithBrands() {
        startKoin {
            modules(module {
                single { newDuelBrands() }
                viewModel(PAST_YOU_PICKER) { AddPuzzleViewModel(pickerOf(SOLVED_ROWS), lookup = null, pastYou = true) }
            })
        }
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private var editor by mutableStateOf(LineupEditor(LineupDraft(listOf(AnyGame(ZIP), AnyGame(QUEENS), AnyGame(ZIP)))))

    private fun panel(includeFriends: Boolean = false) {
        rule.setContent {
            PuzzleBoxTheme {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    LineupPanel(editor, GAMES, includeFriends, onEdit = { edit -> editor = edit(editor) }, audience = LineupAudience.PastYou(SOLVED))
                }
            }
        }
        rule.waitForIdle()
    }

    private fun pastYouState(counts: SolvedCounts?, preset: DuelPreset = DuelPreset.Custom, rows: List<LineupEntry> = editor.draft.entries, adding: Boolean = false): NewDuelState {
        val form = SelfDuelForm.quick(GAMES).copy(preset = preset)
        val lineup = LineupEditor(LineupDraft(rows)).let { if (adding) it.openAdd() else it }
        return state(Opponent.PastYou, SelfDuelView(form, counts, GAMES), "Best of ${rows.size} · Zip, Queens, Zip · against your first solves")
            .copy(lineup = lineup)
    }

    private fun content(state: NewDuelState, actions: NewDuelActions = NO_ACTIONS) {
        rule.setContent {
            PuzzleBoxTheme { Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { NewDuelContent(state, actions) } }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the rows count the puzzles and each Any row says it draws from what you solved`() {
        panel()

        rule.onNodeWithTag("duel.lineup.header").assertTextEquals("Puzzles, in order · 3 of 10")
        rule.onNodeWithTag("duel.lineup.row.1").assertTextContains("A daily you've solved", substring = true)
        rule.onNodeWithTag("duel.lineup.add").assertTextContains("A game, or a puzzle you've solved", substring = true)
    }

    @Test
    fun `with friends in the rows say a friend's puzzle can be drawn`() {
        panel(includeFriends = true)

        rule.onNodeWithTag("duel.lineup.row.2").assertTextContains("A daily or friend's puzzle you've solved", substring = true)
    }

    @Test
    fun `with the counts unread the sheet's tiles are plain and enabled, not zero solved`() {
        content(pastYouState(counts = null, adding = true))

        rule.onNodeWithTag("duel.preset.custom").performClick()
        rule.waitForIdle()

        GAMES.forEach { game ->
            rule.onNodeWithTag("duel.lineup.sheet.game.${game.id.value}").assertTextEquals(game.name).assertIsEnabled()
        }
    }

    @Test
    fun `a tap on a game with solved puzzles adds an Any row of it, and a tap on one with none changes nothing`() {
        editor = editor.openAdd()
        panel()

        rule.onNodeWithTag("duel.lineup.sheet.game.${TANGO.value}").performClick()
        assertEquals(3, editor.draft.size)
        rule.onNodeWithTag("duel.lineup.sheet.game.${QUEENS.value}").performClick()

        assertEquals(AnyGame(QUEENS), editor.draft.entries.last())
        assertEquals(4, editor.draft.size)
    }

    @Test
    fun `Customize shows the lineup and the friends switch, with no game chips`() {
        content(pastYouState(SolvedCounts(SOLVED)))

        rule.onNodeWithTag("duel.preset.custom").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("duel.friends", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("duel.game.${ZIP.value}", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("duel.summary").assertTextEquals("Best of 3 · Zip, Queens, Zip · against your first solves")
    }

    @Test
    fun `tapping Customize takes the preset, and again while still on Quick takes it again instead of closing the lineup`() {
        var customized = 0
        content(pastYouState(SolvedCounts(SOLVED), preset = DuelPreset.Quick), actionsOnCustomize { customized++ })

        rule.onNodeWithTag("duel.preset.custom").performClick()
        rule.onNodeWithTag("duel.preset.custom").performClick()
        rule.waitForIdle()

        assertEquals(2, customized)
        rule.onNodeWithTag("duel.lineup", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `with nothing solved the reason shows in plain words and Create is off`() {
        content(pastYouState(SolvedCounts(emptyMap()), rows = emptyList()).copy(summary = ""))

        rule.onNodeWithTag("duel.self.reason").assertTextEquals("You haven't solved any puzzles yet.")
        rule.onNodeWithTag("duel.create").assertIsNotEnabled()
        rule.onNodeWithTag("duel.summary").assertDoesNotExist()
    }

    @Test
    fun `an undrawable Any row tells Past you's player to change that puzzle`() {
        rule.setContent { PuzzleBoxTheme { Notice(DuelNotice.EntryUndrawable(3, "Zip", pastYou = true)) } }

        rule.onNodeWithTag("duel.notice").assertTextEquals("No solved Zip puzzle left for Past you. Change puzzle 3.")
    }

    @Test
    fun `an exact row that is no longer solved tells the player to change or remove it`() {
        rule.setContent { PuzzleBoxTheme { Notice(DuelNotice.EntryUnavailable(2, pastYou = true)) } }

        rule.onNodeWithTag("duel.notice").assertTextEquals("Puzzle 2 isn't one you've solved any more (its author may have taken it down). Change or remove it.")
    }

    @Test
    fun `a friend duel's notices keep their words`() {
        rule.setContent { PuzzleBoxTheme { Notice(DuelNotice.EntryUndrawable(3, "Zip")) } }

        rule.onNodeWithTag("duel.notice").assertTextEquals("No unplayed Zip puzzle left for you both. Change puzzle 3.")
    }

    private fun actionsOnCustomize(onCustomize: () -> Unit) = NewDuelActions(
        onQuick = {}, onDaily = {}, onToggle = {}, onDeadline = {}, onFriends = {}, onOpponent = {},
        onCreate = {}, onGoogle = {}, onCancel = {}, onCustomize = onCustomize,
    )
}
