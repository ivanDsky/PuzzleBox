package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GameCardColors
import com.ivandsky.puzzlebox.gameui.GameCardSurface
import java.time.Duration
import java.time.Instant

private const val SECONDARY_ALPHA = 0.82f
private const val MOTIF_ALPHA = 0.18f

/** How much of onPrimary the challenge card's lip takes: primary 70 %, onPrimary 30 % (the kit's `color-mix`). */
private const val LIP_BLEND = 0.3f
private const val MINUTES_PER_HOUR = 60L
private val MotifSize = 96.dp

/** The challenge card's paint: primary fill, onPrimary content, and a lip blended from primary toward onPrimary. */
@Composable
internal fun duelCardColors(): GameCardColors {
    val scheme = MaterialTheme.colorScheme
    return GameCardColors(
        fill = scheme.primary,
        fg = scheme.onPrimary,
        fg2 = scheme.onPrimary.copy(alpha = SECONDARY_ALPHA),
        motif = scheme.onPrimary,
        motifAlpha = MOTIF_ALPHA,
        lip = lerp(scheme.primary, scheme.onPrimary, LIP_BLEND),
    )
}

/** "Challenge a friend" with the sub line, or "Sign in to duel friends" while [signedIn] is false. */
@Composable
internal fun DuelChallengeCard(signedIn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = duelCardColors()
    GameCardSurface(
        colors = colors,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("duels.challenge"),
        motif = {
            Icon(
                imageVector = PuzzleIcons.Trophy,
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = Spacing.xxl).size(MotifSize),
                tint = colors.motif.copy(alpha = colors.motifAlpha),
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Challenge a friend", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(PuzzleIcons.Trophy, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.fg2)
                    Text(
                        text = if (signedIn) "Best of 3 · same puzzles, your own time" else "Sign in to duel friends",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.fg2,
                    )
                }
            }
            Icon(PuzzleIcons.Chevron, contentDescription = null)
        }
    }
}

/** One line under the list when there is nothing in it. */
@Composable
internal fun DuelsEmptyNote(modifier: Modifier = Modifier) {
    Text(
        text = "Same puzzles, your own time. Times stay sealed until both finish.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.md),
    )
}

/** "5h 12m", or "42m" under an hour; "0m" once the deadline has passed. */
internal fun timeLeft(deadline: Instant, now: Instant): String {
    val minutes = Duration.between(now, deadline).toMinutes().coerceAtLeast(0L)
    val hours = minutes / MINUTES_PER_HOUR
    return if (hours > 0) "${hours}h ${minutes % MINUTES_PER_HOUR}m" else "${minutes}m"
}
