package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow

private val Nickname = Regex("""["“„]([^"“”„]+)["”“]""")

/**
 * The short form of a player's [name] for tight spots: the nickname in straight or curly quotes when there is one
 * ("Ivan “ZloyDI” Dobrovolskyi" becomes "ZloyDI"), else the first word. A one-word name stays as it is and a blank
 * one stays blank.
 */
internal fun shortName(name: String): String {
    val trimmed = name.trim()
    val nickname = Nickname.find(trimmed)?.groupValues?.get(1)?.trim()
    if (!nickname.isNullOrEmpty()) return nickname
    return trimmed.substringBefore(' ').substringBefore('\t')
}

/**
 * A name on one line: the [full] text when it fits the width on offer, else the [short] one, and only when even that
 * does not fit is it cut with an ellipsis. Both are measured in [style], which is the style the text is drawn in. The
 * [modifier] sizes the name's box; [textModifier] goes on the text itself. It reads its width from a
 * `BoxWithConstraints`, so it never sits under an `IntrinsicSize` measure.
 */
@Composable
internal fun FittedName(full: String, short: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, textModifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val direction = LocalLayoutDirection.current
    BoxWithConstraints(modifier) {
        val fits = remember(full, style, constraints.maxWidth, direction, measurer) {
            val width = measurer.measure(full, style, softWrap = false, maxLines = 1, layoutDirection = direction).size.width
            width <= constraints.maxWidth
        }
        Text(
            text = if (fits) full else short,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = textModifier,
        )
    }
}
