package com.ivandsky.puzzlebox.compete.create.lineup

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

private const val PAGE = 30
private const val LEADING_ITEMS = 3
private const val CLEAR = "duel.lineup.filter.clear"

/** The Add puzzle page's friend search, shown on its own: the tiles, the exact-puzzle field, its filters, its lines and its rows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class PuzzlePickerPageTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun stopKoinAgain() = stopKoin()

    private val picked = mutableListOf<LineupEntry>()

    private fun show(
        rows: List<PickablePuzzle>,
        offline: Boolean = false,
        added: Set<LegRef> = emptySet(),
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
        val picker = LineupPickerModel(position = 3, changing = false, games = GAMES, solved = null, added = added, pastYou = false)
        val content: @Composable () -> Unit = {
            PuzzleBoxTheme {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    PuzzlePickerPage(picker, onPick = { picked += it }, viewModel = koinViewModel<AddPuzzleViewModel>())
                }
            }
        }
        if (restoration != null) restoration.setContent(content) else rule.setContent(content)
        rule.waitForIdle()
    }

    private fun addTag(puzzle: PickablePuzzle) = "duel.lineup.pick.add.${puzzle.entry.ref.wire}"

    @Test
    fun `every game has a tile that picks that game, whichever puzzle it draws`() {
        show(listOf(CROWN_JEWELS))

        GAMES.forEach { rule.onNodeWithTag("duel.lineup.sheet.game.${it.id.value}").assertIsEnabled() }
        rule.onNodeWithTag("duel.lineup.sheet.game.${QUEENS.value}").performClick()

        assertEquals(listOf<LineupEntry>(AnyGame(QUEENS)), picked)
    }

    @Test
    fun `typing crown, then New and Queens, finds the friend's puzzle and Add picks that exact puzzle`() {
        show(listOf(CROWN_JEWELS, CROWN_TANGO, OLD_CROWN, MY_GRID))

        rule.onNodeWithTag("duel.lineup.search").performTextInput("crown")
        rule.pickInSheet(PickerFilterPage.Freshness, "duel.lineup.chip.new")
        rule.pickInSheet(PickerFilterPage.Games, "duel.lineup.chip.game.queens")

        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("1 puzzle")
        rule.onNodeWithTag(addTag(CROWN_JEWELS)).assertTextEquals("Add").assertIsEnabled().performClick()

        assertEquals(listOf<LineupEntry>(CROWN_JEWELS.entry), picked)
    }

    @Test
    fun `a puzzle the lineup already holds reads Added and is off, and the others stay on`() {
        show(listOf(CROWN_JEWELS, OLD_CROWN), added = setOf(CROWN_JEWELS.entry.ref))

        rule.onNodeWithTag(addTag(CROWN_JEWELS)).assertTextEquals("Added").assertIsNotEnabled()
        rule.onNodeWithTag(addTag(OLD_CROWN)).assertTextEquals("Add").assertIsEnabled()
    }

    @Test
    fun `no match says so with the typed text, and with these filters once a chip is on`() {
        show(listOf(CROWN_JEWELS))

        rule.onNodeWithTag("duel.lineup.search").performTextInput("zzz")
        rule.waitForIdle()
        rule.onNodeWithTag("duel.lineup.empty").assertTextEquals("Nothing matches \"zzz\". Try a code, or clear the filters.")

        rule.pickInSheet(PickerFilterPage.Source, "duel.lineup.chip.community")
        rule.onNodeWithTag("duel.lineup.empty").assertTextEquals("Nothing matches \"zzz\" with these filters. Try a code, or clear the filters.")
    }

    @Test
    fun `Clear turns every chip off and widens the list, and is not there while none is on`() {
        show(listOf(CROWN_JEWELS, CROWN_TANGO, OLD_CROWN, MY_GRID))
        rule.onNodeWithTag(CLEAR).assertDoesNotExist()

        rule.pickInSheet(PickerFilterPage.Freshness, "duel.lineup.chip.new")
        rule.pickInSheet(PickerFilterPage.Games, "duel.lineup.chip.game.queens")
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
    fun `offline puts the connection note on the page and keeps the cached rows`() {
        show(listOf(CROWN_JEWELS), offline = true)

        rule.onNodeWithTag("duel.lineup.offline").assertTextEquals("You're offline. Code lookup needs a connection.")
        rule.onNodeWithTag("duel.lineup.pick.${CROWN_JEWELS.entry.ref.wire}").assertExists()
    }

    @Test
    fun `the clear button empties the field and brings every puzzle back`() {
        show(listOf(CROWN_JEWELS, MY_GRID))
        rule.onNodeWithTag("duel.lineup.search").performTextInput("grid")
        rule.waitForIdle()
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("1 puzzle")

        rule.onNodeWithTag("duel.lineup.search.clear").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("2 puzzles · Newest first")
    }

    @Test
    fun `an empty field offers Paste, which fills it from the clipboard and finds the puzzle, and typing hides it`() {
        show(listOf(CROWN_JEWELS, OLD_CROWN))
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
        ApplicationProvider.getApplicationContext<Context>().let { (it.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).clearPrimaryClip() }

        rule.onNodeWithTag("duel.lineup.search.paste").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.search").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test
    fun `a typed word that reads as a code says Nothing matches and asks nobody`() {
        val asked = mutableListOf<String>()
        show(listOf(CROWN_JEWELS), lookup = { asked += it; CodeLookup.NotFound })

        rule.onNodeWithTag("duel.lineup.search").performTextInput("dragon")
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.empty").assertTextEquals("Nothing matches \"dragon\". Try a code, or clear the filters.")
        rule.onNodeWithTag("duel.lineup.lookup").assertDoesNotExist()
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `a code nobody shared, sent with the keyboard's Search, shows its lookup line, not Nothing matches`() {
        show(listOf(CROWN_JEWELS), lookup = { CodeLookup.NotFound })

        rule.onNodeWithTag("duel.lineup.search").performTextInput("k7qx2m")
        rule.onNodeWithTag("duel.lineup.search").performImeAction()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.lookup").assertTextEquals("No puzzle has the code K7Q-X2M. Check it with the person who shared it.")
        rule.onNodeWithTag("duel.lineup.empty").assertDoesNotExist()
    }

    @Test
    fun `an offline lookup offers Retry, which asks again and shows the puzzle`() {
        var online = false
        show(listOf(CROWN_JEWELS), lookup = { if (online) CodeLookup.Found(OLD_CROWN) else CodeLookup.Offline })
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
    fun `scrolling to the end of the list asks for the next page of the archive, and not before`() {
        val days = List(60) { i ->
            val ref = LegRef.Daily(QUEENS, LocalDate.of(2026, 11, 9).minusDays(i.toLong()), 100 - i)
            PickablePuzzle(ExactPuzzle(ref, null, "Queens #${100 - i}", null, PuzzleSource.PastDay), isNew = false, played = false, meta = "Nov 9, 2026", code = null)
        }
        val asked = mutableListOf<Int>()
        show(days, observe = { _, pages -> pages.map { n -> asked += n; PickerResult(days.take(PAGE * n), offline = false, moreDays = PAGE * n < days.size) } })
        assertEquals(listOf(1), asked)

        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(LEADING_ITEMS + PAGE - 1)
        rule.waitForIdle()

        assertEquals(listOf(1, 2), asked)
        rule.onNode(hasScrollToIndexAction()).performScrollToIndex(LEADING_ITEMS + PAGE)
        rule.onNodeWithTag("duel.lineup.pick.${days[PAGE].entry.ref.wire}").assertExists()
    }

    @Test
    fun `a rotation under the open page keeps the typed text, the filtered rows and the chips`() {
        val restoration = StateRestorationTester(rule)
        show(listOf(CROWN_JEWELS, CROWN_TANGO, OLD_CROWN, MY_GRID), restoration = restoration)
        rule.onNodeWithTag("duel.lineup.search").performTextInput("crown")
        rule.pickInSheet(PickerFilterPage.Freshness, "duel.lineup.chip.new")
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("2 puzzles")

        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag("duel.lineup.search").assertTextContains("crown")
        rule.onNodeWithTag("duel.lineup.count").assertTextEquals("2 puzzles")
        rule.onNodeWithTag("duel.lineup.pick.${CROWN_JEWELS.entry.ref.wire}").assertExists()
        rule.onNodeWithTag("duel.lineup.pick.${OLD_CROWN.entry.ref.wire}").assertDoesNotExist()
        rule.onNodeWithTag(PickerFilterPage.Freshness.tag).assertTextContains("New")
    }
}
