package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.ivandsky.puzzlebox.compete.duel.Pill
import com.ivandsky.puzzlebox.compete.share.shareInvite
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.NewBestBurst
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
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry

private const val QuietFadeMs = 900
private val PhoneMaxWidth = 560.dp
private val TileMinHeight = 64.dp
private val WinBorder = 3.dp
private val QuietBorder = 1.dp

/**
 * The reveal's end: the verdict card, the three tiles, the puzzles list
 * and Rematch / Share / Watch again. [wide] puts the summary on the left and the list on the right; a phone has one
 * column. Back arrow leaves through [onBack].
 */
@Composable
internal fun RevealSummary(
    summary: DuelSummary,
    brands: GamePlayRegistry,
    wide: Boolean,
    onBack: () -> Unit,
    onRematch: () -> Unit,
    onWatchAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).testTag("reveal.summary")) {
        CompactTopBar(
            title = "Reveal",
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("reveal.back")) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back")
                }
            },
        )
        if (wide) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Column(Modifier.weight(1f).scrolling().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    SummaryHead(summary)
                    SummaryActions(summary, onRematch, onWatchAgain)
                }
                Column(Modifier.weight(1f).scrolling().padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    SummaryPuzzleList(summary, brands)
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier.widthIn(max = PhoneMaxWidth).fillMaxWidth().scrolling().padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                ) {
                    SummaryHead(summary)
                    SummaryPuzzleList(summary, brands)
                    SummaryActions(summary, onRematch, onWatchAgain)
                }
            }
        }
    }
}

@Composable
private fun Modifier.scrolling(): Modifier = verticalScroll(rememberScrollState())

/** The verdict card over the tile row. */
@Composable
private fun SummaryHead(summary: DuelSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        VerdictCard(summary)
        Row(
            modifier = Modifier.fillMaxWidth().resultBeat(ResultBeats.Supporting),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val tiles = summary.tiles
            Tile("${tiles.puzzlesWon} of ${tiles.puzzles}", "Puzzles won", "reveal.tile.won", Modifier.weight(1f))
            Tile(tiles.fastestMs?.let(::formatDuration) ?: "–", "Fastest puzzle", "reveal.tile.fastest", Modifier.weight(1f))
            Tile(formatDuration(tiles.totalMs), "Total time", "reveal.tile.total", Modifier.weight(1f))
        }
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

/**
 * The final-score card. A win stamps in with a sweep and a burst, in the primary accent; a loss or a draw fades in
 * slowly in the outline colour, with no burst, so a loss stays quiet. The numerals count up from 0.
 */
@Composable
private fun VerdictCard(summary: DuelSummary) {
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
    Box(Modifier.fillMaxWidth().testTag("reveal.summary.card")) {
        Stamp(visible = stamped || !win) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = scheme.surfaceContainerHigh,
                border = BorderStroke(if (win) WinBorder else QuietBorder, if (win) scheme.primary else scheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = fade.value }
                    .solveSweep(sweep, scheme.primary, MaterialTheme.shapes.large),
            ) {
                CardBody(summary, win)
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
private fun CardBody(summary: DuelSummary, win: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "${summary.me} vs ${summary.opponent}",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("reveal.summary.title"),
        )
        Text(summary.bestOf, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Pill(
            text = summary.pill,
            modifier = Modifier.testTag("reveal.summary.pill"),
            container = if (win) scheme.primary else scheme.surfaceContainerHighest,
            content = if (win) scheme.onPrimary else scheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Numeral(summary.myScore, win, Modifier.testTag("reveal.summary.mine"))
            Text("–", style = MaterialTheme.typography.displayMedium, color = scheme.onSurfaceVariant)
            Numeral(summary.theirScore, win, Modifier.testTag("reveal.summary.theirs"))
        }
        Text(
            text = summary.verdict,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (win) scheme.primary else scheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("reveal.summary.verdict"),
        )
    }
}

@Composable
private fun Numeral(target: Int, win: Boolean, modifier: Modifier) {
    val shown = rememberCountUp(target.toLong(), durationMs = MotionTokens.ScoreCountUpDuration)
    Text(
        text = shown.toString(),
        style = MaterialTheme.typography.displayLarge.tabular(),
        color = if (win) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/** Opacity of the quiet entrance: a slow fade from 0 (at once under reduced motion or in a preview); [enabled] false is already 1. */
@Composable
private fun rememberQuietFade(enabled: Boolean): Animatable<Float, *> {
    val instant = rememberReducedMotion() || LocalInspectionMode.current || !enabled
    val alpha = remember { Animatable(if (instant) 1f else 0f) }
    LaunchedEffect(alpha) { alpha.animateTo(1f, tween(QuietFadeMs)) }
    return alpha
}

/** Rematch, then Share and Watch again as a tonal pair. */
@Composable
private fun SummaryActions(summary: DuelSummary, onRematch: () -> Unit, onWatchAgain: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.resultBeat(ResultBeats.Actions), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PrimaryButton("Rematch", onRematch, Modifier.fillMaxWidth().testTag("reveal.rematch"))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SecondaryButton("Share", { context.shareInvite(summary.shareText) }, Modifier.weight(1f).testTag("reveal.share"))
            SecondaryButton("Watch again", onWatchAgain, Modifier.weight(1f).testTag("reveal.watchAgain"))
        }
    }
}
