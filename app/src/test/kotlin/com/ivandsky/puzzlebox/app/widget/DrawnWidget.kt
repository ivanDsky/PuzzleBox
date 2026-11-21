package com.ivandsky.puzzlebox.app.widget

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.ivandsky.puzzlebox.app.shortcut.ShortcutIntents
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf

/**
 * The widget composed to RemoteViews and inflated at [size], the way a launcher would: what it says ([texts],
 * [descriptions]), how many views take a tap ([clickable]), and a tap by what it landed on. Composing here fails a
 * composition the host would reject, not on a launcher.
 */
internal class DrawnWidget private constructor(private val application: Application, root: View) {
    private val all = mutableListOf<View>().also { collect(root, it) }
    val descriptions = all.mapNotNull { it.contentDescription?.toString() }
    val texts = all.filterIsInstance<TextView>().map { it.text.toString() }.filter { it.isNotEmpty() }
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

    companion object {
        @OptIn(ExperimentalGlanceRemoteViewsApi::class)
        fun draw(application: Application, state: WidgetState?, size: DpSize): DrawnWidget {
            val views = runBlocking { GlanceRemoteViews().compose(application, size) { WidgetContent(state) }.remoteViews }
            return DrawnWidget(application, views.apply(application, FrameLayout(application)))
        }
    }
}
