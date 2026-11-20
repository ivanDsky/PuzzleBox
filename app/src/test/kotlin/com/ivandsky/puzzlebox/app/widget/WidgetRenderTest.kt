package com.ivandsky.puzzlebox.app.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Composes the widget's content to RemoteViews and inflates them, so a composition the host would reject fails here, not on a launcher.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetRenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val rings = listOf(
        WidgetRing(GameId("zip"), "Zip", TodayStatus.Solved(timeMs = 2_000)),
        WidgetRing(GameId("queens"), "Queens", TodayStatus.InProgress(elapsedMs = 1_000)),
        WidgetRing(GameId("tango"), "Tango", TodayStatus.NotStarted),
        WidgetRing(GameId("minisudoku"), "Mini Sudoku", TodayStatus.Offline),
        WidgetRing(GameId("strands"), "Strands", TodayStatus.NotStarted),
    )

    private class Drawn(val descriptions: List<String>, val texts: List<String>)

    private fun draw(state: WidgetState?, size: DpSize = DEFAULT): Drawn {
        val views = runBlocking { GlanceRemoteViews().compose(context, size) { WidgetContent(state) }.remoteViews }
        val descriptions = mutableListOf<String>()
        val texts = mutableListOf<String>()
        fun walk(view: View) {
            view.contentDescription?.let { descriptions += it.toString() }
            if (view is TextView) texts += view.text.toString()
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(views.apply(context, FrameLayout(context)))
        return Drawn(descriptions, texts)
    }

    private fun state(freezesHeld: Int?, upNext: WidgetUpNext = WidgetUpNext.Done) =
        WidgetState(rings, WidgetStreak(days = 3, best = 12, freezesHeld = freezesHeld), upNext)

    @Test
    fun `each ring says its game and where it stands`() {
        val drawn = draw(state(freezesHeld = 2))

        assertEquals(
            listOf("Zip, solved", "Queens, in progress", "Tango, not started", "Mini Sudoku, offline", "Strands, not started"),
            drawn.descriptions.dropLast(1),
        )
    }

    @Test
    fun `the game names show under the rings only at the large size`() {
        assertFalse(draw(state(freezesHeld = 2), DEFAULT).texts.contains("Zip"))
        assertTrue(draw(state(freezesHeld = 2), LARGE).texts.containsAll(rings.map { it.displayName }))
    }

    @Test
    fun `the streak line shows the freeze count only when freezes are held`() {
        assertEquals(listOf("3 days in a row · Best 12", "2"), draw(state(freezesHeld = 2)).texts.dropLast(1))
        assertEquals(listOf("3 days in a row · Best 12"), draw(state(freezesHeld = null)).texts.dropLast(1))
    }

    @Test
    fun `a failed read draws bare tracks, a blank streak line and a plain Up next button`() {
        val drawn = draw(null)

        assertEquals(listOf("Up next"), drawn.descriptions)
        assertEquals(listOf("Up next"), drawn.texts)
    }

    @Test
    fun `the button says what is up next, with the same words spoken`() {
        val play = draw(state(freezesHeld = 2, upNext = WidgetUpNext.Play(GameId("queens"), "Queens")))
        val done = draw(state(freezesHeld = 2, upNext = WidgetUpNext.Done))

        assertEquals("Up next · Queens", play.texts.last())
        assertEquals("Up next · Queens", play.descriptions.last())
        assertEquals("All done for today", done.texts.last())
        assertEquals("All done for today", done.descriptions.last())
    }

    private companion object {
        val DEFAULT = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(320.dp, 180.dp)
    }
}
