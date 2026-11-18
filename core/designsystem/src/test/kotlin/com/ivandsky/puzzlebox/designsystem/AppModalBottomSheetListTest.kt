package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

private const val FRAME_MS = 16L
private const val SETTLE_FRAMES = 60
private const val LIST = "list"
private val ROW = 48.dp

/**
 * A long list in a bottom sheet: a fling that runs into the list's end must not push the idle sheet,
 * a drag hands over from the list to the sheet only at the list's top, and rows arriving while
 * nothing is touched move nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class AppModalBottomSheetListTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var sheet: SheetState
    private lateinit var list: LazyListState
    private var dismissed = false
    private val rows = mutableIntStateOf(80)

    private fun show(skipPartiallyExpanded: Boolean, firstRow: Int = 0, body: @Composable () -> Unit) {
        rule.setContent {
            PuzzleBoxTheme {
                sheet = rememberModalBottomSheetState(skipPartiallyExpanded)
                list = rememberLazyListState(initialFirstVisibleItemIndex = firstRow)
                AppModalBottomSheet(onDismissRequest = { dismissed = true }, sheetState = sheet) { body() }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1000)
    }

    @Composable
    private fun Rows(modifier: Modifier = Modifier) {
        LazyColumn(modifier.fillMaxWidth().testTag(LIST), state = list) {
            items(rows.intValue) { Box(Modifier.fillMaxWidth().height(ROW)) { Text("row $it") } }
        }
    }

    /** [ROW]s in a column that scrolls itself, started [offset] px down so a fling can reach either end. */
    @Composable
    private fun ScrollingBody(offset: Int) {
        Column(Modifier.fillMaxWidth().verticalScroll(ScrollState(offset)).testTag(LIST)) {
            repeat(rows.intValue) { Box(Modifier.fillMaxWidth().height(ROW)) { Text("row $it") } }
        }
    }

    /** The duel add sheet's shape: a few tiles, then a capped list, all in a column that scrolls. */
    @Composable
    private fun TilesAboveCappedList() {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            repeat(6) { Box(Modifier.fillMaxWidth().height(ROW)) { Text("tile $it") } }
            Rows(Modifier.heightIn(max = 420.dp))
        }
    }

    private fun flingOnList(fromY: Float, toY: Float) = rule.onNodeWithTag(LIST).performTouchInput {
        swipe(Offset(centerX, fromY), Offset(centerX, toY), 100)
    }

    /** The furthest the sheet strays from [rest] over the frames in which a fling plays out. */
    private fun worstDrift(rest: Float): Float {
        var worst = 0f
        repeat(SETTLE_FRAMES) {
            rule.mainClock.advanceTimeBy(FRAME_MS)
            worst = maxOf(worst, abs(sheet.requireOffset() - rest))
        }
        return worst
    }

    private fun assertSheetStaysPut(rest: Float) = assertEquals("sheet moved on its own after the list was let go", 0f, worstDrift(rest), 1f)

    @Test
    fun `a list fling down to its top leaves the sheet where it is`() {
        show(skipPartiallyExpanded = true, firstRow = 6) { Rows() }
        val rest = sheet.requireOffset()
        flingOnList(300f, 700f)
        assertSheetStaysPut(rest)
    }

    @Test
    fun `a list fling up to its bottom leaves the sheet where it is`() {
        show(skipPartiallyExpanded = true, firstRow = 60) { Rows() }
        val rest = sheet.requireOffset()
        flingOnList(600f, 100f)
        assertSheetStaysPut(rest)
    }

    @Test
    fun `a scrolling body fling down to its top leaves the sheet where it is`() {
        show(skipPartiallyExpanded = true) { ScrollingBody(offset = 800) }
        val rest = sheet.requireOffset()
        flingOnList(300f, 700f)
        assertSheetStaysPut(rest)
    }

    @Test
    fun `a scrolling body fling up to its bottom leaves the sheet where it is`() {
        show(skipPartiallyExpanded = true) { ScrollingBody(offset = 100_000) }
        val rest = sheet.requireOffset()
        flingOnList(600f, 100f)
        assertSheetStaysPut(rest)
    }

    @Test
    fun `a capped list in a scrolling body fling up to its bottom leaves the sheet where it is`() {
        rows.intValue = 14
        show(skipPartiallyExpanded = true) { TilesAboveCappedList() }
        val rest = sheet.requireOffset()
        rule.onNodeWithTag(LIST).performTouchInput { swipe(Offset(centerX, bottom - 20f), Offset(centerX, top + 20f), 100) }
        assertSheetStaysPut(rest)
    }

    @Test
    fun `a slow drag down scrolls the list to its top before the sheet moves`() {
        show(skipPartiallyExpanded = true, firstRow = 2) { Rows() }
        val rest = sheet.requireOffset()
        rule.onNodeWithTag(LIST).performTouchInput {
            down(center)
            repeat(3) { moveBy(Offset(0f, 50f), 16) }
        }
        assertEquals("sheet moved while the list could still scroll", rest, sheet.requireOffset(), 1f)
        assertTrue("list did not scroll", list.firstVisibleItemIndex < 2)
        rule.onNodeWithTag(LIST).performTouchInput {
            repeat(6) { moveBy(Offset(0f, 50f), 16) }
            advanceEventTime(300)
            up()
        }
        assertEquals(0, list.firstVisibleItemIndex + list.firstVisibleItemScrollOffset)
        assertTrue("sheet did not follow once the list was at its top", sheet.requireOffset() > rest + 50f)
    }

    @Test
    fun `a fast drag down on a list at its top still dismisses the sheet`() {
        show(skipPartiallyExpanded = true) { Rows() }
        flingOnList(200f, 800f)
        rule.mainClock.advanceTimeBy(1000)
        assertTrue("flicking the sheet away stopped working", dismissed)
    }

    @Test
    fun `rows arriving while nothing is touched move neither the sheet nor the list`() {
        show(skipPartiallyExpanded = true, firstRow = 10) { Rows() }
        val rest = sheet.requireOffset()
        val at = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
        rule.runOnUiThread { rows.intValue = 140 }
        assertSheetStaysPut(rest)
        assertEquals(at, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset)
    }

    @Test
    fun `rows arriving in a half open sheet keep it half open`() {
        rows.intValue = 3
        show(skipPartiallyExpanded = false) { TilesAboveCappedList() }
        val rest = sheet.requireOffset()
        rule.runOnUiThread { rows.intValue = 12 }
        assertSheetStaysPut(rest)
        rule.runOnUiThread { rows.intValue = 40 }
        assertSheetStaysPut(rest)
        assertEquals(SheetValue.PartiallyExpanded, sheet.currentValue)
        assertEquals(0, list.firstVisibleItemIndex + list.firstVisibleItemScrollOffset)
    }
}
