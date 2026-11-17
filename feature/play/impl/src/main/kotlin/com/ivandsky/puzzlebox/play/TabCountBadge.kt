package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular

private val BadgeSize = 18.dp
private const val BADGE_CAP = 9

/** The red need-you badge: nothing for 0, the number up to nine, "9+" above. */
@Composable
internal fun TabCountBadge(count: Int, modifier: Modifier = Modifier) {
    val text = tabCountText(count) ?: return
    Box(
        modifier = modifier
            .widthIn(min = BadgeSize)
            .heightIn(min = BadgeSize)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold).tabular(),
            color = MaterialTheme.colorScheme.onError,
            maxLines = 1,
        )
    }
}

/** The badge text for [count]: null for none, capped at "9+". */
internal fun tabCountText(count: Int): String? = when {
    count <= 0 -> null
    count > BADGE_CAP -> "$BADGE_CAP+"
    else -> count.toString()
}
