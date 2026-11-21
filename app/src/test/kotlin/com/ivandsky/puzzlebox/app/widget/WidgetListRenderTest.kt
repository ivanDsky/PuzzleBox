package com.ivandsky.puzzlebox.app.widget

import android.app.Application
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

// The list layouts composed to RemoteViews and inflated ([DrawnWidget]), the way a launcher would draw them.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class WidgetListRenderTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    private fun draw(state: WidgetState?, size: DpSize) = DrawnWidget.draw(application, state, size)

    @Test
    fun `the narrow list reads the streak line, then name, time, rank and best for each game, then the pill`() {
        val texts = draw(WidgetSamples.default, NARROW).texts

        assertEquals(
            listOf(
                "658 days", "5", "5 of 5 done",
                "Zip", "1:12", "#3 of 41", "Best 1:01",
                "Tango", "0:48", "#7 of 41", "Best 0:48",
                "Queens", "1:35", "#1 of 38", "Best 1:30",
                "Mini Sudoku", "2:13", "#12 of 40", "Best 2:00",
                "Strands", "4:11", "–", "Best 4:00",
            ),
            texts.dropLast(1),
        )
        assertTrue(texts.last().startsWith("New puzzles at "))
    }

    @Test
    fun `a game not solved says where it stands, with no rank, and its best still shows`() {
        assertEquals(
            listOf(
                "658 days", "5", "2 of 5 done",
                "Zip", "1:12", "#3 of 41", "Best 1:01",
                "Tango", "0:48", "#7 of 41", "Best 0:44",
                "Queens", "In progress 3:12", "Best 1:30",
                "Mini Sudoku", "Not started", "Best 2:00",
                "Strands", "Not started",
                "Continue Queens",
            ),
            draw(WidgetSamples.mixed, NARROW).texts,
        )
    }

    @Test
    fun `New best shows in the narrow list from five cells wide, and never at four`() {
        assertEquals(listOf("Zip", "1:12", "New best", "#3 of 41"), draw(WidgetSamples.mixed, NARROW_5).texts.subList(3, 7))
        assertEquals(emptyList<String>(), draw(WidgetSamples.mixed, NARROW).texts.filter { it == "New best" })
    }

    @Test
    fun `the wide list reads one line a game, name then rank then time with New best then best`() {
        val texts = draw(WidgetSamples.default, WIDE).texts

        assertEquals(
            listOf(
                "658 days in a row", "5 freezes", "Best 658", "5 of 5 done",
                "Zip", "#3 of 41", "1:12", "Best 1:01",
                "Tango", "#7 of 41", "0:48", "New best", "Best 0:48",
                "Queens", "#1 of 38", "1:35", "Best 1:30",
                "Mini Sudoku", "#12 of 40", "2:13", "Best 2:00",
                "Strands", "–", "4:11", "Best 4:00",
                "That’s the whole set",
            ),
            texts.dropLast(1),
        )
        assertTrue(texts.last().startsWith("New puzzles at "))
    }

    @Test
    fun `the wide list leaves out the rank column when signed out, and the footer text when not all is done`() {
        assertEquals(
            listOf("Zip", "1:12", "Best 1:01", "Tango", "0:48", "New best", "Best 0:48"),
            draw(WidgetSamples.signedOut, WIDE).texts.subList(4, 11),
        )
        val mixed = draw(WidgetSamples.mixed, WIDE).texts
        assertEquals("Continue Queens", mixed.last())
        assertEquals("Best 658", mixed[2])
        assertEquals("2 of 5 done", mixed[3])
        assertEquals("Zip", mixed[4])
    }

    @Test
    fun `a solve over an hour is shown whole in the wide list, with no New best squeezed after it`() {
        val texts = draw(WidgetSamples.longest, WIDE).texts

        assertTrue(texts.containsAll(listOf("1658 days in a row", "15 freezes", "1:05:42")))
        val longest = WidgetSamples.longest.copy(rings = WidgetSamples.longest.rings.map { it.copy(newBest = true) })
        val withChips = draw(longest, WIDE).texts
        assertEquals(4, withChips.count { it == "New best" })
    }

    @Test
    fun `a place too long for the wide rank column shows the chip form, a short one keeps its board`() {
        val texts = draw(WidgetSamples.longest, WIDE).texts

        assertTrue(texts.containsAll(listOf("#40 of 40", "#112")))
        assertEquals(emptyList<String>(), texts.filter { it == "#112 of 120" || it == "#40" })
        assertTrue(draw(WidgetSamples.default, WIDE).texts.containsAll(listOf("#3 of 41", "#7 of 41", "#1 of 38")))
    }

    @Test
    fun `offline draws Retry in every row with its best, no rank, no done count and Open PuzzleBox`() {
        assertEquals(
            listOf(
                "658 days", "5",
                "Zip", "Retry", "Best 1:01",
                "Tango", "Retry", "Best 0:44",
                "Queens", "Retry",
                "Mini Sudoku", "Retry",
                "Strands", "Retry",
                "Open PuzzleBox",
            ),
            draw(WidgetSamples.offline, NARROW).texts,
        )
    }

    @Test
    fun `a failed read draws five bare tracks and Open PuzzleBox in both lists`() {
        listOf(NARROW, WIDE).forEach {
            val drawn = draw(WidgetSamples.empty, it)

            assertEquals(listOf("Open PuzzleBox"), drawn.texts)
            assertEquals(listOf("Open PuzzleBox"), drawn.descriptions)
            assertEquals(2, drawn.clickable)
        }
    }

    @Test
    fun `a row opens its game, the pill opens up next or Today, and the header and footer open Today`() {
        listOf(NARROW, WIDE).forEach { size ->
            val drawn = draw(WidgetSamples.mixed, size)

            assertEquals(ShortcutTarget.Game(GameId("zip")), drawn.tapText("Zip"))
            assertEquals(ShortcutTarget.Game(GameId("queens")), drawn.tapText("Queens"))
            assertEquals(ShortcutTarget.Game(GameId("strands")), drawn.tapText("Strands"))
            assertEquals(ShortcutTarget.UpNext, drawn.tapText("Continue Queens"))
            assertEquals(ShortcutTarget.Today, drawn.tapText("2 of 5 done"))
        }
        val done = draw(WidgetSamples.default, WIDE)
        assertEquals(ShortcutTarget.Today, done.tapText("That’s the whole set"))
        assertEquals(ShortcutTarget.Today, done.tapText(done.texts.last()))
        assertEquals(ShortcutTarget.Game(GameId("tango")), done.tapText("New best"))
    }

    @Test
    fun `the widget behind, five rows and the pill are the taps`() {
        assertEquals(7, draw(WidgetSamples.mixed, NARROW).clickable)
        assertEquals(7, draw(WidgetSamples.mixed, WIDE).clickable)
    }

    private companion object {
        val NARROW = DpSize(276.dp, 338.dp)
        val NARROW_5 = DpSize(349.dp, 338.dp)
        val WIDE = DpSize(620.dp, 430.dp)
    }
}
