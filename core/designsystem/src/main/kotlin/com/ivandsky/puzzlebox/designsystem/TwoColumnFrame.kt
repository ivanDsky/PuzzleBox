package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The right column's width on a tablet pane's two-column page (prototype 05, "Tablet"). */
val TwoColumnRightWidth: Dp = 372.dp

/** The narrowest left column that still reads as one: below it the page keeps the single column. */
private val TwoColumnMinLeftWidth = 320.dp

private val TwoColumnGap = Spacing.xl

/** The page's margin above and below the columns, kept even when they fill the pane. */
private val TwoColumnMargin = Spacing.lg

/** The pane width that fits both columns at the page's margins: left column, gap, right column and both margins. */
val TwoColumnMinWidth: Dp = TwoColumnMinLeftWidth + TwoColumnGap + TwoColumnRightWidth + Spacing.lg * 2

/**
 * The two columns of a tablet pane's page on a pane [paneHeight] high: the [left] column takes the width the right
 * leaves, and [right] is [TwoColumnRightWidth] wide. [left] gets the modifier that sizes and tags it, and the height the
 * pane leaves it inside the page's margins. The columns are centred on each other and, when shorter than the pane,
 * on the pane; taller, the caller's scroll starts from the top. [contentMaxWidth] caps the columns and the gap, the margins
 * outside it; the row is centred in a wider pane. [tag] marks the row, `[tag].left` and `[tag].right` the columns.
 * Use it for a pane at least [TwoColumnMinWidth] wide; a narrower one keeps one column.
 */
@Composable
fun TwoColumnFrame(
    paneHeight: Dp,
    tag: String,
    left: @Composable (modifier: Modifier, height: Dp) -> Unit,
    right: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    contentMaxWidth: Dp = Dp.Infinity,
) {
    Box(modifier.fillMaxWidth().heightIn(min = paneHeight), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(max = contentMaxWidth + Spacing.lg * 2)
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = TwoColumnMargin)
                .testTag(tag),
            horizontalArrangement = Arrangement.spacedBy(TwoColumnGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            left(Modifier.weight(1f).testTag("$tag.left"), paneHeight - TwoColumnMargin * 2)
            Column(
                Modifier.width(TwoColumnRightWidth).testTag("$tag.right"),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                content = right,
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun TwoColumnFramePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        TwoColumnFrame(
            paneHeight = 480.dp,
            tag = "preview",
            left = { column, _ -> Box(column.height(200.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh)) },
            right = { Box(Modifier.fillMaxWidth().height(120.dp).background(MaterialTheme.colorScheme.secondaryContainer)) },
        )
    }
}
