package com.ivandsky.puzzlebox.app.widget

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Spacer
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.domain.TodayStatus

/**
 * The chips1 and chips2 block: the streak line, the five chips and, in a look that has one, the action pill. The block has a
 * fixed height, and what the content leaves is shared into the gaps between its rows, never into the chips.
 */
@Composable
internal fun ColumnScope.WidgetChipsStep(state: WidgetState?, look: WidgetLayout.ChipLook, colors: WidgetColors) {
    val context = LocalContext.current
    WidgetStreakLine(state?.streak, state?.let { doneCount(it, context.resources) }, look, colors)
    Spacer(GlanceModifier.defaultWeight())
    WidgetChips(state?.rings, look, colors)
    if (look.pill) {
        Spacer(GlanceModifier.defaultWeight())
        WidgetActionPill(upNextButton(state, context), colors)
    }
}

/** "5 of 5 done", only when the read is known: with every game offline there is no count to claim. */
private fun doneCount(state: WidgetState, resources: Resources): String? {
    if (!state.known) return null
    val done = state.rings.count { it.status is TodayStatus.Solved }
    return resources.getString(R.string.widget_done_count, done, state.rings.size)
}
