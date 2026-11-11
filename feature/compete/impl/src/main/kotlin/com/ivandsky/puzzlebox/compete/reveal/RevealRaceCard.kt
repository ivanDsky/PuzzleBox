package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.duel.Pill
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.GameAccent
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import kotlin.math.roundToInt

private val CardBorder = 2.dp
private val PillIcon = 16.dp

/**
 * One puzzle's race card, outlined in the game's accent: the glyph tile with "{title}" over "Puzzle {position} of {N}"
 * and, once it locks, the winner's pill; both sides with their figures counting up and a thin bar filling beneath
 * each; the basis line at the foot. Past the race (Score, End) the card shows the full figures, resolved.
 * The loser's half dims when it resolves. Only Strands, which ranks on hints, shows hints.
 */
@Composable
internal fun RaceCard(plan: RevealPlan, card: RevealCard, state: RevealState, position: Int, brand: GameBrand, wide: Boolean) {
    val racing = state.phase is RevealPhase.Leg
    val resolved = !racing || state.locked != null || card.leg.tickersDone(state)
    val accent = brand.accent.colors()
    val longest = maxOf(card.leg.mine.timeMs(), card.leg.theirs.timeMs())
    val mine = figureOf(card.leg.mine, card.rankOrder, if (racing) state.myTickMs else null, longest)
    val theirs = figureOf(card.leg.theirs, card.rankOrder, if (racing) state.theirTickMs else null, longest)
    val loser = when (card.leg.verdict) {
        is LegVerdict.Win -> Side.Theirs
        is LegVerdict.Loss -> Side.Mine
        else -> null
    }
    val dimTarget = if (resolved) 1f else 0f
    val dim by animateFloatAsState(dimTarget, tween(if (rememberReducedMotion()) 0 else MotionTokens.DurationMedium), label = "loserDim")
    val basisAlpha by animateFloatAsState(dimTarget, tween(if (rememberReducedMotion()) 0 else MotionTokens.DurationMedium), label = "basis")
    OutlinedCard(Modifier.fillMaxWidth().testTag("reveal.card"), border = BorderStroke(CardBorder, accent.accent)) {
        Column(
            modifier = Modifier.padding(if (wide) Spacing.xl else Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            CardHeader(card, position, plan.cards.size, brand, accent, showPill = resolved)
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RaceSide(
                    plan.me.name, plan.me.photoUrl, DuelTone.Me, mine, accent.accent,
                    dim = if (loser == Side.Mine || mine.dimmed) dim else 0f, wide = wide, modifier = Modifier.weight(1f),
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RaceSide(
                    plan.opponent.name, plan.opponent.photoUrl, DuelTone.Them, theirs, accent.accent,
                    dim = if (loser == Side.Theirs || theirs.dimmed) dim else 0f, wide = wide, modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = card.basis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(basisAlpha),
            )
        }
    }
}

@Composable
private fun CardHeader(card: RevealCard, position: Int, count: Int, brand: GameBrand, accent: GameAccent, showPill: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GameGlyph(brand, GameGlyphSize.Tile)
        Column(Modifier.weight(1f)) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Puzzle $position of $count",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showPill) VerdictPill(card, accent)
    }
}

/** The winner's pill: the game's accent with a trophy; a tie gets the plain muted pill. */
@Composable
private fun VerdictPill(card: RevealCard, accent: GameAccent) {
    val tag = Modifier.testTag("reveal.pill")
    when (card.leg.verdict) {
        is LegVerdict.Win, is LegVerdict.Loss -> Row(
            modifier = tag
                .background(accent.container, PillShape)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(PuzzleIcons.Trophy, contentDescription = null, modifier = Modifier.size(PillIcon), tint = accent.onContainer)
            Text(card.pill, style = MaterialTheme.typography.labelMedium, color = accent.onContainer, maxLines = 1)
        }
        else -> Pill(card.pill, tag)
    }
}

private fun DuelSideState.timeMs(): Long = (this as? DuelSideState.Time)?.timeMs ?: 0L

/**
 * What a side shows. A Time side counts up with [tickMs] (null: its full figure); Strands' headline is the hint count,
 * rising with the clock, over "{n} hints · m:ss". The bar fills against the [longest] figure of the puzzle.
 */
private fun figureOf(state: DuelSideState, rankOrder: RankOrder, tickMs: Long?, longest: Long): RaceFigure = when (state) {
    is DuelSideState.Time -> {
        val shown = tickMs?.coerceIn(0L, state.timeMs) ?: state.timeMs
        val progress = if (longest > 0) shown.toFloat() / longest else 1f
        if (rankOrder == RankOrder.HintsThenTime) {
            val hints = if (state.timeMs > 0) (state.hints * shown.toFloat() / state.timeMs).roundToInt() else state.hints
            RaceFigure(hints.toString(), "${hintsLabel(hints)} · ${formatDuration(shown)}", progress)
        } else {
            RaceFigure(formatDuration(shown), null, progress)
        }
    }
    DuelSideState.Sealed -> RaceFigure("—", "Hidden until you play", 0f, dimmed = true)
    DuelSideState.NotPlayed -> RaceFigure("—", "Not played", 0f, dimmed = true)
    DuelSideState.GaveUp -> RaceFigure("—", "Gave up", 0f, dimmed = true)
}

private fun hintsLabel(hints: Int) = if (hints == 1) "1 hint" else "$hints hints"

/** True once every ticker of a puzzle nobody wins has reached its figure (a won puzzle resolves on the lock). */
private fun LegReveal.tickersDone(state: RevealState): Boolean {
    if (verdict is LegVerdict.Win || verdict is LegVerdict.Loss) return false
    val mineDone = (mine as? DuelSideState.Time)?.let { state.myTickMs >= it.timeMs } ?: true
    val theirsDone = (theirs as? DuelSideState.Time)?.let { state.theirTickMs >= it.timeMs } ?: true
    return mineDone && theirsDone
}
