package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.RankOrder

/**
 * The two-column You | Them block of a duel. It shows each side's figure and never decides
 * who won or draws a caption; the screen below it does.
 */
@Composable
fun DuelCompare(you: DuelSide, them: DuelSide, rankOrder: RankOrder, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        DuelColumn(you, DuelTone.Me, rankOrder, "duel.compare.you")
        DuelColumn(them, DuelTone.Them, rankOrder, "duel.compare.them")
    }
}

private class DuelFigure(val headline: String, val sub: String?, val dimmed: Boolean)

private fun figureOf(state: DuelSideState, rankOrder: RankOrder): DuelFigure = when (state) {
    is DuelSideState.Time -> when (rankOrder) {
        RankOrder.TimeThenHints -> DuelFigure(formatDuration(state.timeMs), hintsLabel(state.hints), false)
        RankOrder.HintsThenTime -> DuelFigure(hintsLabel(state.hints), formatDuration(state.timeMs), false)
    }
    DuelSideState.Sealed -> DuelFigure("–", "Hidden until you play", false)
    DuelSideState.NotPlayed -> DuelFigure("–", "Not played", true)
    DuelSideState.GaveUp -> DuelFigure("–", "Gave up", true)
}

private fun hintsLabel(hints: Int) = if (hints == 1) "1 hint" else "$hints hints"

@Composable
private fun RowScope.DuelColumn(side: DuelSide, tone: DuelTone, rankOrder: RankOrder, tag: String) {
    val figure = figureOf(side.state, rankOrder)
    Column(
        modifier = Modifier
            .weight(1f)
            .testTag(tag)
            .semantics(mergeDescendants = true) {}
            .alpha(if (figure.dimmed) 0.6f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        PlayerAvatar(name = side.name, photoUrl = side.photoUrl, size = 40.dp, tone = tone)
        Text(side.name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        Text(
            figure.headline,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = Spacing.xs).testTag("$tag.figure"),
        )
        figure.sub?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun DuelCompareTimePreview() = PuzzleBoxTheme {
    DuelCompare(
        you = DuelSide("Ann", null, DuelSideState.Time(58_000, 1)),
        them = DuelSide("Sam", null, DuelSideState.Time(72_000, 0)),
        rankOrder = RankOrder.TimeThenHints,
    )
}

@PuzzleBoxPreviews
@Composable
internal fun DuelCompareHintsPreview() = PuzzleBoxTheme {
    DuelCompare(
        you = DuelSide("Ann", null, DuelSideState.Time(58_000, 1)),
        them = DuelSide("Sam", null, DuelSideState.Time(72_000, 0)),
        rankOrder = RankOrder.HintsThenTime,
    )
}

@PuzzleBoxPreviews
@Composable
internal fun DuelCompareSealedPreview() = PuzzleBoxTheme {
    DuelCompare(
        you = DuelSide("Ann", null, DuelSideState.Time(58_000, 1)),
        them = DuelSide("Sam", null, DuelSideState.Sealed),
        rankOrder = RankOrder.TimeThenHints,
    )
}

@PuzzleBoxPreviews
@Composable
internal fun DuelCompareNotPlayedPreview() = PuzzleBoxTheme {
    DuelCompare(
        you = DuelSide("Ann", null, DuelSideState.NotPlayed),
        them = DuelSide("Sam", null, DuelSideState.GaveUp),
        rankOrder = RankOrder.TimeThenHints,
    )
}
