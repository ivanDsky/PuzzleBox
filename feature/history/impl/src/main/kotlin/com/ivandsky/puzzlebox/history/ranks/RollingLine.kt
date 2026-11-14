package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import com.ivandsky.puzzlebox.designsystem.MorphCell
import com.ivandsky.puzzlebox.designsystem.ScrubCell
import com.ivandsky.puzzlebox.gameui.board.RollingNumber

private val Runs = Regex("[0-9]+|[^0-9]+")

// More digits than this is no number to roll (it would not fit an Int): it is drawn as text.
private const val MaxDigits = 9

// A line cut where its digits start and stop: "1:35 · 1 hint" is 1, ":", 35, " · ", 1, " hint". Two lines with one
// shape (numbers and words in the same places) change part by part; any others change as a whole.
private class LineParts(val text: String) {
    val parts: List<String> = Runs.findAll(text).map { it.value }.toList()
    val shape: List<Boolean> = parts.map { it.number() != null }
}

private fun String.number(): Int? = if (length <= MaxDigits && this[0] in '0'..'9') toInt() else null

// The digits a number is written with at least when it has a zero in front, as a time's seconds do ("05"); otherwise one.
private fun String.fixedDigits(): Int = if (length > 1 && this[0] == '0') length else 1

/**
 * One line of text whose numbers roll and whose words frost when it changes: [text] in [style] and [color], on one
 * line. A line that keeps its shape (numbers and words in the same places, as two times do) rolls each number that
 * changed, as the board's places roll ([RollingNumber]), and frosts each word that did; a line of another shape frosts
 * over as a whole ([MorphCell]). So "1:15 · 1 hint" rolls to "1:35 · 2 hints", and frosts to "1,240 pts".
 *
 * At rest the change plays on a clock. Between two lines the caller gives the one it leaves as [was] and how far along
 * it is as [progress] (0 to 1, read only where the line is laid out and drawn): the line then follows [progress], and
 * lands without moving again. Reduced motion swaps. The line reads as [text] to assistive tech and tests whatever it
 * is drawing; [modifier] carries its tag.
 */
@Composable
internal fun RollingLine(
    text: String,
    style: TextStyle,
    color: ColorProducer,
    modifier: Modifier = Modifier,
    was: String? = null,
    progress: (() -> Float)? = null,
) {
    val now = remember(text) { LineParts(text) }
    val before = remember(was) { was?.let(::LineParts) }
    Box(modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }) {
        if (before != null && progress != null && before.shape != now.shape) {
            ScrubCell(before.text, now.text, progress) { Word(it, style, color) }
        } else {
            MorphCell(now, identity = { it.shape }) { layer ->
                // A layer of the line's shape is the line itself, drawn from the text of this frame and not a frame late.
                val current = layer.shape == now.shape
                Parts(if (current) now else layer, before.takeIf { current }, progress, style, color)
            }
        }
    }
}

@Composable
private fun Parts(line: LineParts, was: LineParts?, progress: (() -> Float)?, style: TextStyle, color: ColorProducer) {
    Row {
        line.parts.forEachIndexed { index, part ->
            key(index) {
                val old = was?.parts?.get(index)
                val number = part.number()
                when {
                    number != null -> {
                        // Once a number here has been written with a zero in front, the ones that roll past are too.
                        val seen = remember { intArrayOf(1) }
                        val digits = maxOf(seen[0], part.fixedDigits(), old?.fixedDigits() ?: 1)
                        seen[0] = digits
                        RollingNumber(number, from = old?.number(), progress = progress) {
                            Word(it.toString().padStart(digits, '0'), style, color)
                        }
                    }
                    old != null && progress != null -> ScrubCell(old, part, progress) { Word(it, style, color) }
                    else -> MorphCell(part) { Word(it, style, color) }
                }
            }
        }
    }
}

@Composable
private fun Word(text: String, style: TextStyle, color: ColorProducer) {
    BasicText(text, style = style, color = color, maxLines = 1, softWrap = false)
}
