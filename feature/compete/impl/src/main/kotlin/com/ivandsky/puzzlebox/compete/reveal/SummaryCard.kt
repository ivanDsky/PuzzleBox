package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.compete.duel.Pill
import com.ivandsky.puzzlebox.compete.share.shareInvite
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.NewBestBurst
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberCountUp
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.rememberSolveSweepState
import com.ivandsky.puzzlebox.designsystem.resultBeat
import com.ivandsky.puzzlebox.designsystem.solveSweep
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.PlayerAvatar

private const val QuietFadeMs = 900
private val TileMinHeight = 64.dp
private val WinBorder = 3.dp
private val QuietBorder = 1.dp
private val HeadAvatar = 40.dp
private val HeadAvatarOverlap = (-12).dp
private val NameAvatar = 24.dp
private val PillIcon = 16.dp
private val NumeralSize = 64.sp

/**
 * The final-score card: who played, the outcome pill, the two numerals over their names, the verdict, the three tiles,
 * Rematch and Share / Watch again. A win stamps in with a sweep and a burst, in the primary accent; a loss or a draw
 * fades in slowly in the outline colour, with no burst, so a loss stays quiet. The numerals count up from 0.
 */
@Composable
internal fun SummaryCard(summary: DuelSummary, onRematch: () -> Unit, onWatchAgain: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val win = summary.kind == VerdictKind.Win
    val sweep = rememberSolveSweepState()
    val fade = rememberQuietFade(enabled = !win)
    val inspecting = LocalInspectionMode.current
    var stamped by remember { mutableStateOf(inspecting) }
    LaunchedEffect(win) {
        stamped = true
        if (win) sweep.play()
    }
    Box(modifier.fillMaxWidth().testTag("reveal.summary.card")) {
        Stamp(visible = stamped || !win) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = scheme.surface,
                border = BorderStroke(if (win) WinBorder else QuietBorder, if (win) scheme.primary else scheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = fade.value }
                    .solveSweep(sweep, scheme.primary, MaterialTheme.shapes.large),
            ) {
                CardBody(summary, win, onRematch, onWatchAgain)
            }
        }
        NewBestBurst(
            trigger = win,
            colors = listOf(scheme.primary, scheme.tertiary, scheme.secondary),
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun CardBody(summary: DuelSummary, win: Boolean, onRematch: () -> Unit, onWatchAgain: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CardHead(summary, win)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            ScoreSide(summary.myScore, summary.me, summary.myPhotoUrl, DuelTone.Me, win, "reveal.summary.mine", Modifier.weight(1f))
            Text(
                text = "–",
                style = MaterialTheme.typography.displayMedium.copy(fontSize = NumeralSize, lineHeight = NumeralSize),
                color = scheme.onSurfaceVariant,
            )
            ScoreSide(summary.theirScore, summary.opponent, summary.theirPhotoUrl, DuelTone.Them, win, "reveal.summary.theirs", Modifier.weight(1f))
        }
        Text(
            text = summary.verdict,
            style = MaterialTheme.typography.headlineMedium,
            color = if (win) LocalPrimaryText.current else scheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("reveal.summary.verdict"),
        )
        Tiles(summary.tiles)
        Actions(summary, onRematch, onWatchAgain)
    }
}

/** Two overlapping avatars, "{Me} vs {Opponent}" over the best-of line, and the outcome pill. */
@Composable
private fun CardHead(summary: DuelSummary, win: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(HeadAvatarOverlap)) {
            val ring = Modifier.border(2.dp, scheme.surface, CircleShape)
            PlayerAvatar(summary.me, summary.myPhotoUrl, ring, HeadAvatar, DuelTone.Me)
            PlayerAvatar(summary.opponent, summary.theirPhotoUrl, ring, HeadAvatar, DuelTone.Them)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = "${summary.me} vs ${summary.opponent}",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("reveal.summary.title"),
            )
            Text(summary.bestOf, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        OutcomePill(summary, win)
    }
}

@Composable
private fun OutcomePill(summary: DuelSummary, win: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val tag = Modifier.testTag("reveal.summary.pill")
    if (!win) {
        Pill(summary.pill, tag, scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
        return
    }
    Row(
        modifier = tag.background(scheme.primaryContainer, PillShape).padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(PuzzleIcons.Trophy, contentDescription = null, modifier = Modifier.size(PillIcon), tint = scheme.onPrimaryContainer)
        Text(summary.pill, style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer, maxLines = 1)
    }
}

/** One big numeral over its player's avatar and name. */
@Composable
private fun ScoreSide(score: Int, name: String, photoUrl: String?, tone: DuelTone, win: Boolean, tag: String, modifier: Modifier) {
    val shown = rememberCountUp(score.toLong(), durationMs = MotionTokens.ScoreCountUpDuration)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = shown.toString(),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = NumeralSize, lineHeight = NumeralSize).tabular(),
            color = if (win && tone == DuelTone.Me) LocalPrimaryText.current else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(tag),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PlayerAvatar(name, photoUrl, size = NameAvatar, tone = tone)
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun Tiles(tiles: SummaryTiles) {
    Row(Modifier.fillMaxWidth().resultBeat(ResultBeats.Supporting), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Tile("${tiles.puzzlesWon} of ${tiles.puzzles}", "Puzzles won", "reveal.tile.won", Modifier.weight(1f))
        Tile(tiles.fastestMs?.let(::formatDuration) ?: "–", "Fastest puzzle", "reveal.tile.fastest", Modifier.weight(1f))
        Tile(formatDuration(tiles.totalMs), "Total time", "reveal.tile.total", Modifier.weight(1f))
    }
}

@Composable
private fun Tile(value: String, label: String, tag: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = TileMinHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Rematch, then Share and Watch again as a tonal pair, each with its icon. */
@Composable
private fun Actions(summary: DuelSummary, onRematch: () -> Unit, onWatchAgain: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.resultBeat(ResultBeats.Actions), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PrimaryButton("Rematch", onRematch, Modifier.fillMaxWidth().testTag("reveal.rematch"))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SecondaryButton(
                "Share", { context.shareInvite(summary.shareText) },
                Modifier.weight(1f).testTag("reveal.share"), icon = PuzzleIcons.Share,
            )
            SecondaryButton(
                "Watch again", onWatchAgain,
                Modifier.weight(1f).testTag("reveal.watchAgain"), icon = PuzzleIcons.Retry,
            )
        }
    }
}

/** Opacity of the quiet entrance: a slow fade from 0 (at once under reduced motion or in a preview); [enabled] false is already 1. */
@Composable
private fun rememberQuietFade(enabled: Boolean): Animatable<Float, *> {
    val instant = rememberReducedMotion() || LocalInspectionMode.current || !enabled
    val alpha = remember { Animatable(if (instant) 1f else 0f) }
    LaunchedEffect(alpha) { alpha.animateTo(1f, tween(QuietFadeMs)) }
    return alpha
}
