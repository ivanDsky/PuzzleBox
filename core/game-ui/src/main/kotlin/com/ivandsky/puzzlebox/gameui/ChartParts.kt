package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.GameAccent

private val PillRadius = 8.dp

/** The "You" pill over this solve's bar: accent fill; a "Replay" pill is outlined on surface. */
@Composable
internal fun Pill(text: String, playAgain: Boolean, palette: GameAccent, modifier: Modifier) {
    val shape = RoundedCornerShape(PillRadius)
    val base = if (playAgain) {
        Modifier.background(MaterialTheme.colorScheme.surface, shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)
    } else {
        Modifier.background(palette.accent, shape)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
        color = if (playAgain) palette.accent else palette.onAccent,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .wrapContentWidth(unbounded = true)
            .then(base)
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .testTag("result.side.you"),
    )
}

/** A dashed outline of a bar open at the bottom: up the left edge, over the rounded top, down the right edge. */
internal fun DrawScope.drawDashedBarOutline(color: Color, strokeWidth: Float, radius: Float) {
    val r = radius.coerceAtMost(size.width / 2f)
    val path = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, r)
        arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
        lineTo(size.width - r, 0f)
        arcTo(Rect(size.width - 2 * r, 0f, size.width, 2 * r), 270f, 90f, false)
        lineTo(size.width, size.height)
    }
    drawPath(path, color, style = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(strokeWidth * 3, strokeWidth * 2))))
}

/** The sentence with its [Emphasized.bold] stretch in bold. */
internal fun Emphasized.annotated(): AnnotatedString = buildAnnotatedString {
    val range = bold
    if (range == null) {
        append(text)
    } else {
        append(text.substring(0, range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(range.first, range.last + 1)) }
        append(text.substring(range.last + 1))
    }
}
