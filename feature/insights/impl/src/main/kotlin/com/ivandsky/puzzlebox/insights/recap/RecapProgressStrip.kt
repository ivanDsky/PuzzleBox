package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

/** One segment per card, filled up to and including the one on screen: where the player is in the story. */
@Composable
internal fun RecapProgressStrip(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Card ${current + 1} of $count" }
            .testTag("recap.progress"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        repeat(count) { index ->
            val color = if (index <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Box(
                Modifier
                    .weight(1f)
                    .height(SegmentHeight)
                    .clip(CircleShape)
                    .background(color)
                    .testTag("recap.progress.$index"),
            )
        }
    }
}

private val SegmentHeight = 4.dp
