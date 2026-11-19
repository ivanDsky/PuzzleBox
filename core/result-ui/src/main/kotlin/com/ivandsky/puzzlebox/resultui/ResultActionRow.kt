package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

private val CircleSize = 44.dp

/**
 * The one frame of the result page's action rows (Watch replay, Post to LinkedIn): a 44 dp circle of [circleColor]
 * holding [circle], then an optional [overline], [title] and [supporting] text, then [trailing]. The rows sit side by
 * side on a tablet, so they share this layout. [modifier] carries the caller's size, click and semantics; the
 * vertical padding is the frame's.
 */
@Composable
internal fun ResultActionRow(
    modifier: Modifier,
    circleColor: Color,
    title: String,
    supporting: String,
    overline: String? = null,
    trailing: @Composable () -> Unit = {},
    circle: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.size(CircleSize).background(circleColor, CircleShape), contentAlignment = Alignment.Center, content = circle)
        Column(Modifier.weight(1f)) {
            overline?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        trailing()
    }
}
