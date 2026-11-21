package com.ivandsky.puzzlebox.app.widget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Spacer

/**
 * The chips1 and chips2 block: the streak line, the five chips and, in a look that has one, the action pill. The block has a
 * fixed height, and what the content leaves is shared into the gaps between its rows, never into the chips.
 */
@Composable
internal fun ColumnScope.WidgetChipsStep(state: WidgetState?, look: WidgetLayout.Look, colors: WidgetColors) {
    val context = LocalContext.current
    WidgetStreakLine(state?.streak, state?.let { doneCount(it, context.resources) }, look, colors)
    Spacer(GlanceModifier.defaultWeight())
    WidgetChips(state?.rings, look, colors)
    if (look.pill) {
        Spacer(GlanceModifier.defaultWeight())
        WidgetActionPill(upNextButton(state, context), colors)
    }
}
