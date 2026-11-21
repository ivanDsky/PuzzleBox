package com.ivandsky.puzzlebox.app.widget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Spacer

/**
 * The chips1 and chips2 block. A [look] with an action pill is chips2: the streak line (without the done count when the look
 * has none), the five chips and the pill, and what the block's height leaves is shared into the gaps between those rows, never
 * into the chips. Without one it is chips1, which is the five chips alone, filling the block.
 */
@Composable
internal fun ColumnScope.WidgetChipsStep(state: WidgetState?, look: WidgetLayout.Look, colors: WidgetColors) {
    val context = LocalContext.current
    if (!look.pill) {
        WidgetChips(state?.rings, look, colors, fillHeight = true)
        return
    }
    val done = if (look.doneCount) state?.let { doneCount(it, context.resources) } else null
    WidgetStreakLine(state?.streak, done, look, colors)
    Spacer(GlanceModifier.defaultWeight())
    WidgetChips(state?.rings, look, colors, fillHeight = false)
    Spacer(GlanceModifier.defaultWeight())
    WidgetActionPill(upNextButton(state, context), colors)
}
