package com.ivandsky.puzzlebox.app.widget

import androidx.compose.runtime.Composable
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ivandsky.puzzlebox.app.R

/**
 * The listNarrow and listWide block: a header, one row per game and the action pill, in the order a column reads. Rows are
 * fixed, not a scrolling list; with the pill under them (narrow) or in a footer beside a line of text (wide). The rows are
 * [WidgetLayout.rowHeight] tall in a block of [WidgetLayout.fixedRows], and otherwise share the block's height. A null
 * [state] draws five bare tracks and the "Open PuzzleBox" pill.
 */
@Composable
internal fun ColumnScope.WidgetListStep(state: WidgetState?, layout: WidgetLayout, colors: WidgetColors) {
    val context = LocalContext.current
    val wide = layout.step == WidgetStep.ListWide
    val done = state?.let { doneCount(it, context.resources) }
    if (wide) WidgetWideHeader(state?.streak, done, layout.look, colors) else WidgetStreakLine(state?.streak, done, layout.look, colors)
    Spacer(GlanceModifier.height(WidgetLayout.LIST_HEADER_GAP))
    val rings: List<WidgetRing?> = state?.rings ?: List(WidgetLayout.ROWS) { null }
    rings.forEachIndexed { index, ring ->
        if (wide) WidgetWideRow(index, ring, showRank = state?.signedOut != true, layout, colors) else WidgetNarrowRow(index, ring, layout, colors)
    }
    Spacer(GlanceModifier.height(WidgetLayout.LIST_PILL_GAP))
    val button = upNextButton(state, context)
    if (wide) {
        Row(modifier = GlanceModifier.fillMaxWidth().height(WidgetLayout.PILL_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (state?.wholeSet == true) context.getString(R.string.widget_whole_set) else "",
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = colors.of(R.color.widget_muted), fontSize = layout.look.text(FOOTER_TEXT)),
                maxLines = 1,
            )
            WidgetActionPill(button, colors, fullWidth = false)
        }
    } else {
        WidgetActionPill(button, colors)
    }
}

private const val FOOTER_TEXT = 14
