package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular

/**
 * A Moments legend row's [label] at its full width, then its [value] at the end: beside the label when both fit the
 * line, else on the line under it. The value breaks only between its " · " items, never inside one.
 */
@Composable
internal fun LabelThenValue(label: String, value: String, ink: Color, modifier: Modifier = Modifier) {
    // No-break spaces hold each item whole; a screen reader still gets the plain value, and the drawn lines with it.
    var valueLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Layout(
        content = {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = ink)
            Text(
                text = value.split(ItemBreak).joinToString(WholeItemBreak) { it.replace(' ', NoBreakSpace) },
                modifier = Modifier.clearAndSetSemantics {
                    text = AnnotatedString(value)
                    getTextLayoutResult { results -> valueLayout?.let { results.add(it) } ?: false }
                },
                style = MaterialTheme.typography.titleMedium.tabular(),
                color = ink,
                textAlign = TextAlign.End,
                onTextLayout = { valueLayout = it },
            )
        },
        modifier = modifier,
    ) { (labelText, valueText), constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val room = width - labelText.maxIntrinsicWidth(constraints.maxHeight) - Spacing.md.roundToPx()
        val beside = valueText.maxIntrinsicWidth(constraints.maxHeight) <= room
        val labelLine = labelText.measure(loose)
        val valueLine = valueText.measure(loose)
        val height = (if (beside) maxOf(labelLine.height, valueLine.height) else labelLine.height + valueLine.height)
            .coerceAtLeast(constraints.minHeight)
        layout(width, height) {
            if (beside) {
                labelLine.place(0, (height - labelLine.height) / 2)
                valueLine.place(width - valueLine.width, (height - valueLine.height) / 2)
            } else {
                labelLine.place(0, 0)
                valueLine.place(width - valueLine.width, labelLine.height)
            }
        }
    }
}

/** Where a value may break: after an item's dot, the items themselves held whole by no-break spaces. */
private const val ItemBreak = " · "
private const val WholeItemBreak = "\u00A0· "
private const val NoBreakSpace = '\u00A0'
