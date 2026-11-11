package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.domain.DuelRowKind
import com.ivandsky.puzzlebox.gameui.GameCardColors
import com.ivandsky.puzzlebox.gameui.GameCardSurface
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import java.time.Duration
import java.time.Instant

private const val SECONDARY_ALPHA = 0.82f
private const val MOTIF_ALPHA = 0.18f

/** How much of onPrimary the challenge card's lip takes: primary 70 %, onPrimary 30 % (the kit's `color-mix`). */
private const val LIP_BLEND = 0.3f
private const val MINUTES_PER_HOUR = 60L
private val AvatarSize = 48.dp
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

/**
 * A duel I am in: results ready (highlighted, trophy), my move, waiting on them, or finished with its score, over
 * "Best of n · k of n played".
 */
@Composable
internal fun DuelCard(row: DuelRow, now: () -> Instant, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = row.opponent?.name ?: "a friend"
    val revealReady = row.kind == DuelRowKind.RevealReady
    DuelRowShell(
        title = when (val kind = row.kind) {
            DuelRowKind.RevealReady -> "See results vs $name"
            DuelRowKind.YourMove -> "Your move vs $name"
            is DuelRowKind.Finished -> finishedTitle(kind, name)
            else -> "Waiting on $name"
        },
        sub = "Best of ${row.legs} · ${row.legsDone} of ${row.legs} played",
        onClick = onClick,
        modifier = modifier,
        highlighted = revealReady,
        avatar = {
            val scheme = MaterialTheme.colorScheme
            // The highlighted row sits on primaryContainer, where the avatar flips to primary / onPrimary.
            if (revealReady) {
                PlayerAvatar(name, row.opponent?.photoUrl, size = AvatarSize, fill = scheme.primary, onFill = scheme.onPrimary)
            } else {
                PlayerAvatar(name, row.opponent?.photoUrl, size = AvatarSize, tone = DuelTone.Them)
            }
        },
        trailing = {
            if (revealReady) {
                Icon(PuzzleIcons.Trophy, contentDescription = null)
            } else if (row.kind !is DuelRowKind.Finished) {
                TimeLeft { timeLeft(row.deadline, now()) }
            }
        },
    )
}

/** "You won 2–1 vs Sam", "Sam won 2–0", "Draw 1–1 vs Sam", the winner's score first; "Finished vs Sam" when the scores were not kept. */
internal fun finishedTitle(kind: DuelRowKind.Finished, name: String): String {
    val mine = kind.myScore ?: return "Finished vs $name"
    val theirs = kind.theirScore ?: return "Finished vs $name"
    return when {
        mine > theirs -> "You won $mine–$theirs vs $name"
        mine < theirs -> "$name won $theirs–$mine"
        else -> "Draw $mine–$theirs vs $name"
    }
}

/**
 * The shared row: avatar, one-line title over one-line sub, [trailing], chevron; ellipsizes before it pushes [trailing].
 * [below] sits in the text column under the sub line, where a row carries actions of its own.
 */
@Composable
internal fun DuelRowShell(
    title: String,
    sub: String,
    onClick: () -> Unit,
    avatar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    trailing: @Composable () -> Unit = {},
    below: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = AppShapes.large
    val container = if (highlighted) scheme.primaryContainer else scheme.surface
    val onContainer = if (highlighted) scheme.onPrimaryContainer else scheme.onSurface
    val quiet = if (highlighted) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .border(1.dp, if (highlighted) container else scheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        avatar()
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = quiet,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            below?.invoke()
        }
        CompositionLocalProvider(LocalContentColor provides onContainer) { trailing() }
        Icon(PuzzleIcons.Chevron, contentDescription = null, tint = quiet)
    }
}

/** The "5h 12m" over "LEFT" column; it keeps its width when the title ellipsizes. */
@Composable
private fun TimeLeft(text: () -> String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = text(),
            style = MaterialTheme.typography.labelLarge.tabular(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = "LEFT",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            maxLines = 1,
        )
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
