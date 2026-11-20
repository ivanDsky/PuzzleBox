package com.ivandsky.puzzlebox.app.widget

import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.shortcut.ShortcutIntents
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// Composes the widget's content to RemoteViews and inflates them, so a composition the host would reject fails here, not on a launcher.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetRenderTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val context: Context = application

    private val rings = listOf(
        WidgetRing(GameId("zip"), "Zip", TodayStatus.Solved(timeMs = 2_000)),
        WidgetRing(GameId("queens"), "Queens", TodayStatus.InProgress(elapsedMs = 1_000)),
        WidgetRing(GameId("tango"), "Tango", TodayStatus.NotStarted),
        WidgetRing(GameId("minisudoku"), "Mini Sudoku", TodayStatus.Offline),
        WidgetRing(GameId("strands"), "Strands", TodayStatus.NotStarted),
    )

    private inner class Drawn(root: View) {
        private val all = mutableListOf<View>().also { collect(root, it) }
        val descriptions = all.mapNotNull { it.contentDescription?.toString() }
        val texts = all.filterIsInstance<TextView>().map { it.text.toString() }
        val images = all.count { it is ImageView }
        val clickable = all.count { it.hasOnClickListeners() }

        fun tapDescribed(description: String): ShortcutTarget? = tap(all.first { it.contentDescription?.toString() == description })

        fun tapText(text: String): ShortcutTarget? = tap(all.first { it is TextView && it.text.toString() == text })

        // A touch goes to the deepest view with a click handler, so walk up from the one aimed at.
        private fun tap(aimed: View): ShortcutTarget? {
            var view: View? = aimed
            while (view != null && !view.hasOnClickListeners()) view = view.parent as? View
            checkNotNull(view) { "no click handler above the view tapped" }.performClick()
            return ShortcutIntents.targetOf(shadowOf(application).nextStartedActivity)
        }

        private fun collect(view: View, into: MutableList<View>) {
            into += view
            if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i), into)
        }
    }

    private fun draw(state: WidgetState?, size: DpSize = DEFAULT): Drawn {
        val views = runBlocking { GlanceRemoteViews().compose(context, size) { WidgetContent(state) }.remoteViews }
        return Drawn(views.apply(context, FrameLayout(context)))
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

    @Test
    fun `each ring opens its own game, whatever its status`() {
        val drawn = draw(state(freezesHeld = 2))

        assertEquals(
            listOf("zip", "queens", "tango", "minisudoku", "strands").map { ShortcutTarget.Game(GameId(it)) },
            listOf("Zip, solved", "Queens, in progress", "Tango, not started", "Mini Sudoku, offline", "Strands, not started")
                .map(drawn::tapDescribed),
        )
    }

    @Test
    fun `a ring name opens its game too`() {
        assertEquals(ShortcutTarget.Game(GameId("queens")), draw(state(freezesHeld = 2), LARGE).tapText("Queens"))
    }

    @Test
    fun `the button still opens up next and the streak line still opens Today`() {
        val drawn = draw(state(freezesHeld = 2, upNext = WidgetUpNext.Play(GameId("queens"), "Queens")))

        assertEquals(ShortcutTarget.UpNext, drawn.tapText("Up next · Queens"))
        assertEquals(ShortcutTarget.Today, drawn.tapText("3 days in a row · Best 12"))
    }

    @Test
    fun `a failed read leaves the bare tracks with no tap of their own`() {
        // The widget behind everything and the button: two taps, none per ring.
        assertEquals(2, draw(null).clickable)
        assertEquals(7, draw(state(freezesHeld = 2)).clickable)
    }

    @Test
    fun `all done drops the play triangle from the button`() {
        val playing = draw(state(freezesHeld = 2, upNext = WidgetUpNext.Play(GameId("queens"), "Queens")))
        val done = draw(state(freezesHeld = 2, upNext = WidgetUpNext.Done))

        assertEquals(playing.images - 1, done.images)
    }

    @Test
    fun `every size from 4 by 2 up to a tablet page composes and inflates`() {
        listOf(DEFAULT, LARGE, TABLET, DpSize(800.dp, 500.dp), DpSize(250.dp, 90.dp)).forEach { size ->
            assertEquals(6, draw(state(freezesHeld = 2), size).descriptions.size)
        }
    }

    private companion object {
        val DEFAULT = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(320.dp, 180.dp)
        val TABLET = DpSize(390.dp, 250.dp)
    }
}
