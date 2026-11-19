package com.ivandsky.puzzlebox.compete.create.lineup

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.newDuelBrands
import com.ivandsky.puzzlebox.compete.domain.lineup.PickerQuery
import com.ivandsky.puzzlebox.compete.domain.lineup.PickerResult
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
import org.robolectric.annotation.GraphicsMode

private const val PAGE = 20
private const val LAST_PAGE = 2
private const val FRAME_MS = 16L
private const val SETTLE_FRAMES = 60
private const val TILE = "duel.lineup.sheet.game.queens"
private const val SEARCH = "duel.lineup.exact"
private const val FLINGS = 5

/**
 * The duel add sheet's results list: a fling that runs into the end of the list (and into the archive's next page) and
 * rows arriving while nothing is touched leave the sheet where it is, and a drag reaches the sheet only from the list's top.
 * A tablet, where the sheet opens whole and the list is in the right pane.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-mdpi")
class AddPuzzleSheetScrollTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun noSheetAnimation() {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<Context>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
    }

    @After
    fun stopKoinAgain() = stopKoin()

    private var editor by mutableStateOf(LineupEditor.opening(GAMES))
    private val arriving = MutableStateFlow(0)

    private fun dailies(count: Int) = (0 until count).map { solvedDaily(QUEENS, LocalDate.of(2026, 11, 17).minusDays(it.toLong()), 500 - it, "Queens #${500 - it}") }

    /** The daily archive a page at a time, [LAST_PAGE] pages in all, plus [arriving] rows that turn up with no one asking. */
    private val archive: (Flow<PickerQuery>, Flow<Int>) -> Flow<PickerResult> = { _, pages ->
        combine(pages, arriving) { page, extra -> PickerResult(dailies(page * PAGE + extra), offline = false, moreDays = page < LAST_PAGE) }
    }

    private fun openSheet() {
        startKoin { modules(module { single { newDuelBrands() }; viewModel { AddPuzzleViewModel(archive, NO_LOOKUP) } }) }
        rule.setContent {
            PuzzleBoxTheme {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    LineupPanel(editor, GAMES, includeFriends = false, onEdit = { edit -> editor = edit(editor) })
                }
            }
        }
        rule.onNodeWithTag("duel.lineup.add").performClick()
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1000)
    }

    private fun top(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    /** The sheet's position is the first tile's: the sheet's own tag sits outside the offset the drag applies. */
    private fun sheetTop() = top(TILE)

    /** Only a row that shows whole has a top of its own: the bounds of one cut by the list's edge start at the edge. */
    private fun rowTop(index: Int) = top("duel.lineup.pick.${dailies(index + 1)[index].entry.ref.wire}")

    /** A point inside the list, in the search column's own coordinates, so it stays valid while rows scroll away. */
    private fun grip(): Offset {
        val column = rule.onNodeWithTag(SEARCH).fetchSemanticsNode().boundsInRoot
        val row = rule.onNodeWithTag("duel.lineup.pick.${dailies(1)[0].entry.ref.wire}").fetchSemanticsNode().boundsInRoot
        return Offset(row.center.x - column.left, row.center.y - column.top)
    }

    /** The furthest the sheet strays from [rest] over the frames in which a fling plays out. */
    private fun worstDrift(rest: Float): Float {
        var worst = 0f
        repeat(SETTLE_FRAMES) {
            rule.mainClock.advanceTimeBy(FRAME_MS)
            worst = maxOf(worst, abs(sheetTop() - rest))
        }
        return worst
    }

    @Test
    fun `flinging the results to their end and into the next page leaves the sheet where it is`() {
        openSheet()
        val rest = sheetTop()
        val at = grip()

        repeat(FLINGS) {
            rule.onNodeWithTag(SEARCH).performTouchInput { swipe(at, Offset(at.x, at.y - 600f), 100) }
            assertEquals("sheet moved on its own after the list was let go", 0f, worstDrift(rest), 1f)
        }
    }

    @Test
    fun `rows arriving while nothing is touched move neither the sheet nor the list`() {
        openSheet()
        val at = grip()
        rule.onNodeWithTag(SEARCH).performTouchInput {
            down(at)
            repeat(4) { moveBy(Offset(0f, -50f), FRAME_MS) }
            advanceEventTime(300)
            up()
        }
        rule.mainClock.advanceTimeBy(1000)
        val rest = sheetTop()
        val row = rowTop(5)

        rule.runOnUiThread { arriving.value = 10 }
        assertEquals("sheet moved when rows arrived", 0f, worstDrift(rest), 1f)
        assertEquals("the list moved when rows arrived", row, rowTop(5), 1f)
    }

    @Test
    fun `a slow drag down scrolls the list to its top before the sheet moves`() {
        openSheet()
        val rest = sheetTop()
        val second = rowTop(1)
        val at = grip()
        rule.onNodeWithTag(SEARCH).performTouchInput {
            down(at)
            repeat(4) { moveBy(Offset(0f, -50f), FRAME_MS) }
            repeat(3) { moveBy(Offset(0f, 50f), FRAME_MS) }
        }
        rule.mainClock.advanceTimeBy(FRAME_MS)
        assertEquals("sheet moved while the list could still scroll", rest, sheetTop(), 1f)
        assertTrue("list did not scroll", rowTop(1) < second)

        rule.onNodeWithTag(SEARCH).performTouchInput {
            repeat(6) { moveBy(Offset(0f, 50f), FRAME_MS) }
        }
        rule.mainClock.advanceTimeBy(FRAME_MS)
        assertTrue("sheet did not follow once the list was at its top", sheetTop() > rest + 50f)
        rule.onNodeWithTag(SEARCH).performTouchInput {
            advanceEventTime(300)
            up()
        }
    }
}
