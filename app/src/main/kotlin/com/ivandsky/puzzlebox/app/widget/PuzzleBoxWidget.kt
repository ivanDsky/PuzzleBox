package com.ivandsky.puzzlebox.app.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.app.shortcut.ShortcutIntents
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The home-screen widget, and the only class that draws it: the streak line, Today's games and the action pill, drawn once
 * per render, in the layout the size the launcher gives it asks for ([WidgetStep]): chips for a short
 * widget, rows for a tall one. It fills the widget however large the user makes it. Nothing in it changes on its own; a redraw
 * is [WidgetRefresher]. A chip or row opens its game and the pill opens what it says; a tap anywhere else opens Today.
 *
 * Each session draws the last good picture first ([CachedWidgetReader.cached], the empty state on a first draw) and the fresh
 * read over it when that lands, so a slow or failed read, or a session killed before its read ends, never leaves the
 * launcher's initial layout on screen. A failed fresh read keeps the cached picture. The fresh read runs once per session,
 * outside the composition, however many responsive sizes are composed.
 */
class PuzzleBoxWidget(private val pictures: CachedWidgetReader) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(WidgetSizes.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cached = pictures.cached()
        val picture = MutableStateFlow(cached)
        coroutineScope {
            // The one fresh read of the session. Glance composes the content once per responsive size, so a read started
            // inside the composition would run once per size ([WidgetSizes.ALL]), all in parallel, and time out together.
            launch { pictures.fresh()?.let { picture.value = it } }
            provideContent {
                val state by picture.collectAsState()
                WidgetContent(state)
            }
        }
    }
}

@Composable
internal fun WidgetContent(state: WidgetState?) {
    val context = LocalContext.current
    val colors = remember(context) { WidgetColors(context) }
    val layout = WidgetLayout.of(LocalSize.current)
    // Under a responsive size mode the launcher lays the views out at the widget's real size, which is at least the size the
    // layout was made for, so the block fills it. A list block of fixed rows is the one exception: it keeps its height, centred.
    val block = if (layout.fixedRows) GlanceModifier.fillMaxWidth().height(layout.contentHeight) else GlanceModifier.fillMaxSize()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(colors.of(R.color.widget_surface))
            .rounded()
            .padding(horizontal = layout.look.horizontalPadding, vertical = layout.look.verticalPadding)
            .clickable(actionStartActivity(ShortcutIntents.intent(context, ShortcutTarget.Today))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = block) {
            when (layout.step) {
                WidgetStep.Chips1, WidgetStep.Chips2 -> WidgetChipsStep(state, layout.look, colors)
                WidgetStep.ListNarrow, WidgetStep.ListWide -> WidgetListStep(state, layout, colors)
            }
        }
    }
}

private fun GlanceModifier.rounded(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        cornerRadius(16.dp)
    }
