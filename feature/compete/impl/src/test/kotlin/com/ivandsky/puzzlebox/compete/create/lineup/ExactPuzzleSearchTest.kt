package com.ivandsky.puzzlebox.compete.create.lineup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.newDuelBrands
import com.ivandsky.puzzlebox.compete.domain.lineup.CodeLookup
import com.ivandsky.puzzlebox.compete.domain.lineup.PickablePuzzle
import com.ivandsky.puzzlebox.compete.domain.lineup.PickerQuery
import com.ivandsky.puzzlebox.compete.domain.lineup.PickerResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    private fun show(
        rows: List<PickablePuzzle>,
        offline: Boolean = false,
        restoration: StateRestorationTester? = null,
        observe: (Flow<PickerQuery>, Flow<Int>) -> Flow<PickerResult> = pickerOf(rows, offline),
        lookup: suspend (String) -> CodeLookup = NO_LOOKUP,
    ) {
        startKoin {
            modules(module {
                single { newDuelBrands() }
                viewModel { AddPuzzleViewModel(observe, lookup) }
            })
        }
        val content: @Composable () -> Unit = {
            PuzzleBoxTheme {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    LineupPanel(editor, GAMES, includeFriends = false, onEdit = { edit -> editor = edit(editor) })
                }
            }
        }
        if (restoration != null) restoration.setContent(content) else rule.setContent(content)
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
        rule.pickInPage(PickerFilterPage.Freshness, "duel.lineup.chip.new")
        rule.pickInPage(PickerFilterPage.Games, "duel.lineup.chip.game.queens")

        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("1 puzzle")
        val ref = CROWN_JEWELS.entry.ref.wire
        rule.onNodeWithTag("duel.lineup.pick.add.$ref").assertTextEquals("Add").assertIsEnabled()
        rule.onNodeWithTag("duel.lineup.pick.add.$ref").performClick()
        rule.waitForIdle()

        assertEquals(CROWN_JEWELS.entry, editor.draft.entries.last())
        rule.onNodeWithTag("duel.lineup.row.4").assertTextContains("Crown jewels", substring = true)
        rule.onNodeWithTag("duel.lineup.row.4").assertTextContains("Queens · From Bo", substring = true)

        openSheet()
        rule.onNodeWithTag("duel.lineup.pick.add.$ref").assertTextEquals("Added").assertIsNotEnabled()
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("4 puzzles · Newest first")
    }

    @Test
    fun `no match says so with the typed text, and with these filters once a chip is on`() {
        show(listOf(CROWN_JEWELS))
        openSheet()

        rule.onNodeWithTag("duel.lineup.search").performTextInput("zzz")
        rule.waitForIdle()
        rule.onNodeWithTag("duel.lineup.empty").assertTextEquals("Nothing matches \"zzz\". Try a code, or clear the filters.")

        rule.pickInPage(PickerFilterPage.Source, "duel.lineup.chip.community")
        rule.onNodeWithTag("duel.lineup.empty").assertTextEquals("Nothing matches \"zzz\" with these filters. Try a code, or clear the filters.")
    }

    @Test
    fun `Clear turns every chip off and widens the list, and is not there while none is on`() {
        show(listOf(CROWN_JEWELS, CROWN_TANGO, OLD_CROWN, MY_GRID))
        openSheet()
        rule.onNodeWithTag(CLEAR).assertDoesNotExist()

        rule.pickInPage(PickerFilterPage.Freshness, "duel.lineup.chip.new")
        rule.pickInPage(PickerFilterPage.Games, "duel.lineup.chip.game.queens")
        rule.onNodeWithTag(PickerFilterPage.Freshness.tag).assertTextContains("New")
        rule.onNodeWithTag(PickerFilterPage.Games.tag).assertTextContains("Queens")
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("1 puzzle")

        rule.onNodeWithTag(CLEAR).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(CLEAR).assertDoesNotExist()
        rule.onNodeWithTag(PickerFilterPage.Freshness.tag).assertTextEquals("Freshness")
        rule.onNodeWithTag(PickerFilterPage.Games.tag).assertTextEquals("Games")
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("4 puzzles · Newest first")
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
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("1 puzzle")

        rule.onNodeWithTag("duel.lineup.search.clear").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("2 puzzles · Newest first")
    }

    @Test
    fun `a rotation under the open sheet keeps the typed text, the filtered rows and the chips`() {
        val restoration = StateRestorationTester(rule)
        show(listOf(CROWN_JEWELS, CROWN_TANGO, OLD_CROWN, MY_GRID), restoration = restoration)
        openSheet()
        rule.onNodeWithTag("duel.lineup.search").performTextInput("crown")
        rule.pickInPage(PickerFilterPage.Freshness, "duel.lineup.chip.new")
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("2 puzzles")

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.search").assertTextContains("crown")
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("2 puzzles")
        rule.onNodeWithTag("duel.lineup.pick.${CROWN_JEWELS.entry.ref.wire}").assertExists()
        rule.onNodeWithTag("duel.lineup.pick.${OLD_CROWN.entry.ref.wire}").assertDoesNotExist()
        rule.onNodeWithTag(PickerFilterPage.Freshness.tag).assertTextContains("New")
    }

    @Test
    fun `scrolling to the end of the list asks for the next page of the archive, and not before`() {
        val days = List(60) { i ->
            val ref = LegRef.Daily(QUEENS, LocalDate.of(2026, 11, 9).minusDays(i.toLong()), 100 - i)
            PickablePuzzle(ExactPuzzle(ref, null, "Queens #${100 - i}", null, PuzzleSource.PastDay), isNew = false, played = false, meta = "Nov 9, 2026", code = null)
        }
        val asked = mutableListOf<Int>()
        show(days, observe = { _, pages -> pages.map { n -> asked += n; PickerResult(days.take(PAGE * n), offline = false, moreDays = PAGE * n < days.size) } })
        openSheet()
        assertEquals(listOf(1), asked)

        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(PAGE - 1)
        rule.waitForIdle()

        assertEquals(listOf(1, 2), asked)
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(PAGE)
        rule.onNodeWithTag("duel.lineup.pick.${days[PAGE].entry.ref.wire}").assertExists()
    }

    @Test
    fun `an empty field offers Paste, which fills it from the clipboard and finds the puzzle, and typing hides it`() {
        show(listOf(CROWN_JEWELS, OLD_CROWN))
        openSheet()
        val clipboard = ApplicationProvider.getApplicationContext<Context>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("code", "r6s7t8"))

        rule.onNodeWithTag("duel.lineup.search.paste").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.search").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("r6s7t8")))
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("1 puzzle")
        rule.onNodeWithTag("duel.lineup.pick.${OLD_CROWN.entry.ref.wire}").assertExists()
        rule.onNodeWithTag("duel.lineup.search.paste").assertDoesNotExist()
        rule.onNodeWithTag("duel.lineup.search.clear").assertExists()
    }

    @Test
    fun `Paste with nothing on the clipboard leaves the field empty`() {
        show(listOf(CROWN_JEWELS))
        openSheet()
        ApplicationProvider.getApplicationContext<Context>().let { (it.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).clearPrimaryClip() }

        rule.onNodeWithTag("duel.lineup.search.paste").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.search").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test
    fun `a code nobody shared, sent with the keyboard's Search, shows its lookup line, not Nothing matches`() {
        show(listOf(CROWN_JEWELS), lookup = { CodeLookup.NotFound })
        openSheet()

        rule.onNodeWithTag("duel.lineup.search").performTextInput("k7qx2m")
        rule.onNodeWithTag("duel.lineup.search").performImeAction()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.lookup").assertTextEquals("No puzzle has the code K7Q-X2M. Check it with the person who shared it.")
        rule.onNodeWithTag("duel.lineup.empty").assertDoesNotExist()
    }

    @Test
    fun `a typed word that reads as a code says Nothing matches and asks nobody`() {
        val asked = mutableListOf<String>()
        show(listOf(CROWN_JEWELS), lookup = { asked += it; CodeLookup.NotFound })
        openSheet()

        rule.onNodeWithTag("duel.lineup.search").performTextInput("dragon")
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.empty").assertTextEquals("Nothing matches \"dragon\". Try a code, or clear the filters.")
        rule.onNodeWithTag("duel.lineup.lookup").assertDoesNotExist()
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `an offline lookup offers Retry, which asks again and shows the puzzle`() {
        var online = false
        show(listOf(CROWN_JEWELS), lookup = { if (online) CodeLookup.Found(OLD_CROWN) else CodeLookup.Offline })
        openSheet()
        rule.onNodeWithTag("duel.lineup.search").performTextInput("k7qx2m")
        rule.onNodeWithTag("duel.lineup.search").performImeAction()
        rule.waitForIdle()
        rule.onNodeWithTag("duel.lineup.lookup").assertTextEquals("You're offline. Code lookup needs a connection.")

        online = true
        rule.onNodeWithTag("duel.lineup.lookup.retry").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.pick.${OLD_CROWN.entry.ref.wire}").assertExists()
        rule.onNodeWithTag("duel.lineup.lookup.retry").assertDoesNotExist()
    }

    @Test
    fun `closing the sheet clears the search for the next opening`() {
        show(listOf(CROWN_JEWELS, MY_GRID))
        openSheet()
        rule.onNodeWithTag("duel.lineup.search").performTextInput("grid")
        rule.waitForIdle()

        editor = editor.dismiss()
        rule.waitForIdle()
        openSheet()

        rule.onNodeWithTag("duel.lineup.search").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("2 puzzles · Newest first")
    }
}

private const val PAGE = 30
private const val CLEAR = "duel.lineup.filter.clear"
