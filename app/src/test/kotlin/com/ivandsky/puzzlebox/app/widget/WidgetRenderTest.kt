package com.ivandsky.puzzlebox.app.widget

import android.app.Application
import android.view.ViewGroup
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The chip layouts composed to RemoteViews and inflated ([DrawnWidget]); the list layouts are WidgetListRenderTest.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class WidgetRenderTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    private fun draw(state: WidgetState?, size: DpSize = CHIPS2) = DrawnWidget.draw(application, state, size)

    private val mixedChips = listOf(
        "Zip, solved, 1:12, rank 3 of 41",
        "Tango, solved, 0:48, rank 7 of 41",
        "Queens, in progress, 3:12",
        "Mini Sudoku, not started",
        "Strands, not started",
    )

    @Test
    fun `each chip says its game, where it stands, its time and its rank`() {
        assertEquals(listOf("5 freezes") + mixedChips + "Continue Queens", draw(WidgetSamples.mixed).descriptions)
    }

    @Test
    fun `chips2 reads the streak, the done count, rank over time and the pill, the long streak when wide enough`() {
        val texts = draw(WidgetSamples.default).texts

        assertEquals(
            listOf("658 days", "5", "5 of 5 done", "#3", "1:12", "#7", "0:48", "#1", "1:35", "#12", "2:13", "4:11"),
            texts.dropLast(1),
        )
        assertTrue(texts.last().startsWith("New puzzles at "))
        assertEquals("658 days in a row", draw(WidgetSamples.default, WIDE_CHIPS2).texts.first())
    }

    @Test
    fun `chips1 reads one line per chip, with no streak line, freeze count or pill`() {
        assertEquals(listOf("#3", "#7", "#1", "#12", "4:11"), draw(WidgetSamples.default, CHIPS1).texts)
        assertEquals(mixedChips, draw(WidgetSamples.mixed, CHIPS1).descriptions)
    }

    @Test
    fun `a rank that is missing or signed out shows the time on the first line`() {
        listOf(WidgetSamples.rankMissing, WidgetSamples.signedOut).forEach {
            assertEquals(listOf("1:12", "0:48", "1:35", "2:13", "4:11"), draw(it, CHIPS1).texts)
        }
    }

    @Test
    fun `the longest names and times still compose`() {
        assertTrue(draw(WidgetSamples.longest, WIDE_CHIPS2).texts.containsAll(listOf("1658 days in a row", "#40", "1:05:42")))
        assertTrue(draw(WidgetSamples.longest, CHIPS2).texts.containsAll(listOf("1658 days", "#40", "1:05:42")))
        assertEquals(listOf("#3", "#7", "#1", "#40", "#112"), draw(WidgetSamples.longest, CHIPS1).texts)
    }

    @Test
    fun `offline draws Retry, no done count and Open PuzzleBox`() {
        val texts = draw(WidgetSamples.offline).texts

        assertEquals(listOf("658 days", "5") + List(5) { "Retry" } + "Open PuzzleBox", texts)
    }

    @Test
    fun `a failed read draws bare tracks, a blank streak line and Open PuzzleBox, at chips2 only`() {
        val chips2 = draw(null, CHIPS2)

        assertEquals(listOf("Open PuzzleBox"), chips2.descriptions)
        assertEquals(listOf("Open PuzzleBox"), chips2.texts)
        assertEquals(emptyList<String>(), draw(null, CHIPS1).descriptions)
        assertEquals(emptyList<String>(), draw(null, CHIPS1).texts)
    }

    @Test
    fun `each chip opens its own game, whatever its status`() {
        val drawn = draw(WidgetSamples.mixed)

        assertEquals(
            listOf("zip", "tango", "queens", "minisudoku", "strands").map { ShortcutTarget.Game(GameId(it)) },
            mixedChips.map(drawn::tapDescribed),
        )
    }

    @Test
    fun `a chip's rank or time opens its game too`() {
        assertEquals(ShortcutTarget.Game(GameId("zip")), draw(WidgetSamples.mixed).tapText("#3"))
        assertEquals(ShortcutTarget.Game(GameId("queens")), draw(WidgetSamples.mixed).tapText("3:12"))
    }

    @Test
    fun `the pill opens up next, or Today when all is done, and the streak line opens Today`() {
        assertEquals(ShortcutTarget.UpNext, draw(WidgetSamples.mixed).tapText("Continue Queens"))
        val done = draw(WidgetSamples.default)
        assertEquals(ShortcutTarget.Today, done.tapText(done.texts.last()))
        assertEquals(ShortcutTarget.Today, draw(WidgetSamples.mixed).tapText("658 days"))
        assertEquals(ShortcutTarget.Today, draw(WidgetSamples.mixed).tapText("2 of 5 done"))
    }

    @Test
    fun `the widget behind, five chips and the pill are the taps, and a failed read has the background and the pill`() {
        assertEquals(7, draw(WidgetSamples.mixed, CHIPS2).clickable)
        assertEquals(6, draw(WidgetSamples.mixed, CHIPS1).clickable)
        assertEquals(2, draw(null, CHIPS2).clickable)
        assertEquals(1, draw(null, CHIPS1).clickable)
    }

    @Test
    fun `every size the provider allows composes and inflates`() {
        val sizes = listOf(
            DpSize(200.dp, 56.dp), DpSize(200.dp, 110.dp), DpSize(200.dp, 200.dp), DpSize(250.dp, 90.dp), CHIPS1, CHIPS2,
            DpSize(250.dp, 400.dp), NARROW_LIST, WIDE_LIST, DpSize(720.dp, 540.dp), DpSize(900.dp, 600.dp),
        )
        sizes.forEach { size ->
            // The five games speak, and the snowflake and the pill: chips1 has neither and the wide header's snowflake is silent.
            val expected = when (WidgetStep.of(size)) {
                WidgetStep.Chips1 -> 5
                WidgetStep.ListWide -> 6
                else -> 7
            }
            assertEquals("$size", expected, draw(WidgetSamples.mixed, size).descriptions.size)
            draw(WidgetSamples.empty, size)
        }
    }

    @Test
    fun `chips2 at 200dp wide, 3 cells, leaves out the done count so the streak line and the five chips fit`() {
        val texts = draw(WidgetSamples.default, DpSize(200.dp, 200.dp)).texts

        assertEquals(listOf("658 days", "5", "#3", "1:12", "#7", "0:48", "#1", "1:35", "#12", "2:13", "4:11"), texts.dropLast(1))
        assertTrue(texts.last().startsWith("New puzzles at "))
    }

    @Test
    fun `the shortest bucket, 200 by 56dp, draws the five chips with their taps and no streak line`() {
        val drawn = draw(WidgetSamples.mixed, DpSize(200.dp, 56.dp))

        assertEquals(mixedChips, drawn.descriptions)
        assertEquals(6, drawn.clickable)
        assertEquals(ShortcutTarget.Game(GameId("zip")), drawn.tapDescribed(mixedChips.first()))
        assertEquals(listOf("#3", "#7", "#1", "#12", "4:11"), draw(WidgetSamples.default, DpSize(200.dp, 56.dp)).texts)
    }

    @Test
    fun `a chips1 chip is a tap target as tall as the widget, a chips2 chip only as tall as its ring and text`() {
        val chips1 = draw(WidgetSamples.mixed, CHIPS1)
        val chips2 = draw(WidgetSamples.mixed)

        mixedChips.forEach {
            assertEquals(it, ViewGroup.LayoutParams.MATCH_PARENT, chips1.tapTargetHeightOf(it))
            assertEquals(it, ViewGroup.LayoutParams.WRAP_CONTENT, chips2.tapTargetHeightOf(it))
        }
    }

    private companion object {
        val CHIPS1 = DpSize(276.dp, 102.dp)
        val CHIPS2 = DpSize(276.dp, 220.dp)
        val WIDE_CHIPS2 = DpSize(400.dp, 220.dp)
        val NARROW_LIST = DpSize(340.dp, 338.dp)
        val WIDE_LIST = DpSize(620.dp, 430.dp)
    }
}
