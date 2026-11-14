package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.TonalIconButton
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.Instant

private val PillMinHeight = 28.dp

/** The header's puzzle line, as the play top bar names it: "Queens · #918", "Tango · Practice", or just the name. */
internal fun resultTitle(game: String, number: Int?, practice: Boolean): String = when {
    number != null -> "$game · #$number"
    practice -> "$game · Practice"
    else -> game
}

/**
 * The result's header row: the game's glyph tile (tag `result.stamp`), which
 * lands with the [Stamp] once on entry (Stamp handles reduced motion; a preview shows it
 * at rest), then [title] (tag `result.label`, wrapping to a second line rather than losing
 * words to the pill) and the record pill. The glyph's box is reserved from the first
 * frame, so the stamp never shifts the row.
 */
@Composable
internal fun ResultHeader(brand: GameBrand, title: String, verdict: RecordVerdict?, modifier: Modifier = Modifier) {
    val inspection = LocalInspectionMode.current
    var entered by rememberSaveable { mutableStateOf(inspection) }
    LaunchedEffect(Unit) { entered = true }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(GameGlyphSize.Tile.box).testTag("result.stamp")) {
            Stamp(visible = entered) { GameGlyph(brand, GameGlyphSize.Tile) }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("result.label"),
        )
        RecordTag(verdict, brand)
    }
}

/**
 * The honest-best pill (tag `result.record_badge`): "New best", "Tied your best!" or
 * "First solve"; nothing for a slower solve, a play-again or an unknown verdict. A tag, not a
 * button — 28 dp, pill-shaped, in the game's accent container, with a leading trophy.
 */
@Composable
internal fun RecordTag(verdict: RecordVerdict?, brand: GameBrand, modifier: Modifier = Modifier) {
    val text = when (verdict) {
        RecordVerdict.NewBest -> "New best"
        RecordVerdict.TiedBest -> "Tied your best!"
        RecordVerdict.FirstSolve -> "First solve"
        RecordVerdict.NotBest, RecordVerdict.PlayAgain, null -> return
    }
    val accent = brand.accent.colors()
    Surface(
        shape = PillShape,
        color = accent.container,
        contentColor = accent.onContainer,
        modifier = modifier.heightIn(min = PillMinHeight).testTag("result.record_badge"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = PuzzleIcons.Trophy,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The play-again line (tag `result.previous_best`): the [PlayAgainCopy.LABEL] word, then the best
 * the play-again left standing, in the way [bestScoreText] shows it for [rankOrder]. No best, the
 * label alone.
 */
@Composable
internal fun PlayAgainLine(previousBest: BestScore?, rankOrder: RankOrder) {
    val best = previousBest?.let { bestScoreText(it, rankOrder) }
    Text(
        text = best?.let { "${PlayAgainCopy.LABEL} · Best $it" } ?: PlayAgainCopy.LABEL,
        style = MaterialTheme.typography.bodyLarge.tabular(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("result.previous_best"),
    )
}

/**
 * The result's actions, in one row: the primary takes the width the icons leave, and the two
 * 48 dp icon buttons follow it, [Spacing.sm] apart and centred on the row's height (the countdown is taller than 48 dp).
 * The primary is "Next: <game>" (`result.next`) or a play-again's Done (`result.done`); once every
 * daily is solved the [ResultCountdown] to [rolloverAt] stands in its place. Then come Share
 * (tag `result.share`) and, unless [onPlayAgain] is null, Play again (`result.playAgain`), both
 * tonal icon buttons that name themselves "Share" and "Play again" for TalkBack and a long-press tooltip.
 */
@Composable
internal fun ResultActions(
    primary: ResultPrimaryAction,
    onPrimary: () -> Unit,
    onShare: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    rolloverAt: Instant?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fill = Modifier.weight(1f)
        when (primary) {
            is ResultPrimaryAction.Next -> PrimaryButton(
                text = "Next: ${primary.name}",
                onClick = onPrimary,
                modifier = fill.testTag("result.next"),
            )
            ResultPrimaryAction.AllDone -> ResultCountdown(rolloverAt, fill)
            ResultPrimaryAction.Done -> PrimaryButton(
                text = "Done",
                onClick = onPrimary,
                modifier = fill.testTag("result.done"),
            )
        }
        TonalIconButton(
            icon = PuzzleIcons.Share,
            contentDescription = "Share",
            onClick = onShare,
            modifier = Modifier.testTag("result.share"),
        )
        onPlayAgain?.let {
            TonalIconButton(
                icon = PuzzleIcons.Retry,
                contentDescription = "Play again",
                onClick = it,
                modifier = Modifier.testTag("result.playAgain"),
            )
        }
    }
}
