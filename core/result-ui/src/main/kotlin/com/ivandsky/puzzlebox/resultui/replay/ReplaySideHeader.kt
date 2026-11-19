package com.ivandsky.puzzlebox.resultui.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.replay.ReplayFacts
import com.ivandsky.puzzlebox.resultui.PastYouAvatar

private val MinSideHeaderHeight = 40.dp
private val DiscSize = 28.dp

/** Air between the stats line and the board's top edge. */
internal val SideHeaderGap = Spacing.sm

/** Room for the name and the stats line together: a larger system font grows it, so the board below never overlaps. */
@Composable
internal fun sideHeaderHeight(): Dp {
    val typography = MaterialTheme.typography
    val lines = with(LocalDensity.current) { typography.titleSmall.lineHeight.toDp() + typography.labelSmall.lineHeight.toDp() }
    return maxOf(MinSideHeaderHeight, lines)
}

/** Who a board belongs to: a [tint] disc with the initial (or the Past you avatar, with the viewer's [pastYouPhotoUrl] when it has one, when [pastYouName] is set), the [name], and under it where that side stands. */
@Composable
internal fun ReplaySideHeader(
    name: String,
    facts: ReplayFacts?,
    pieces: String,
    tint: Color,
    onTint: Color,
    reserveStatsLine: Boolean = false,
    modifier: Modifier = Modifier,
    pastYouName: String? = null,
    pastYouPhotoUrl: String? = null,
) {
    Row(
        modifier.height(sideHeaderHeight()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (pastYouName != null) {
            PastYouAvatar(name = pastYouName, size = DiscSize, photoUrl = pastYouPhotoUrl)
        } else {
            Box(Modifier.size(DiscSize).background(tint, CircleShape), contentAlignment = Alignment.Center) {
                Text(name.take(1).uppercase(), style = MaterialTheme.typography.labelLarge, color = onTint)
            }
        }
        Column {
            Text(
                name,
                modifier = Modifier.testTag(ReplayTags.SIDE_NAME),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (facts != null || reserveStatsLine) {
                // A blank line when there are no facts keeps this name level with the other side's.
                Text(
                    if (facts != null) "${facts.correct} $pieces · ${ReplayCopy.hints(facts.hints)}" else " ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
