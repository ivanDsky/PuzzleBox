package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ivandsky.puzzlebox.compete.duel.Pill
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.gameui.DuelCompare
import com.ivandsky.puzzlebox.gameui.DuelSide
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize

private const val LoserDimAlpha = 0.6f

/**
 * One puzzle's race card: the game glyph, "{title}" over "Puzzle {position} of {N}", both
 * sides with their figures counting up while the puzzle runs, and the winner's pill and basis line once it locks.
 * Past the race (Score, End) the card shows the full figures, resolved. The loser's half dims when it resolves.
 */
@Composable
internal fun RaceCard(plan: RevealPlan, card: RevealCard, state: RevealState, position: Int, brand: GameBrand, wide: Boolean) {
    val racing = state.phase is RevealPhase.Leg
    val resolved = !racing || state.locked != null || card.leg.tickersDone(state)
    val mine = if (racing) card.leg.mine.ticking(state.myTickMs) else card.leg.mine
    val theirs = if (racing) card.leg.theirs.ticking(state.theirTickMs) else card.leg.theirs
    OutlinedCard(Modifier.fillMaxWidth().testTag("reveal.card")) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            GameGlyph(brand, if (wide) GameGlyphSize.Hero else GameGlyphSize.Tile)
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Puzzle $position of ${plan.cards.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                DuelCompare(
                    you = DuelSide(plan.me.name, plan.me.photoUrl, mine),
                    them = DuelSide(plan.opponent.name, plan.opponent.photoUrl, theirs),
                    rankOrder = card.rankOrder,
                )
                LoserScrim(card.leg.verdict, resolved, Modifier.matchParentSize())
            }
            AnimatedVisibility(visible = resolved, enter = fadeIn(tween(MotionTokens.DurationMedium))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    VerdictPill(card)
                    Text(
                        text = card.basis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** A surface-coloured veil over the losing half, fading in with a finite tween (instantly under reduced motion). */
@Composable
private fun LoserScrim(verdict: LegVerdict, resolved: Boolean, modifier: Modifier) {
    val loser = when (verdict) {
        is LegVerdict.Win -> Side.Theirs
        is LegVerdict.Loss -> Side.Mine
        else -> null
    }
    val alpha by animateFloatAsState(
        targetValue = if (resolved && loser != null) LoserDimAlpha else 0f,
        animationSpec = tween(if (rememberReducedMotion()) 0 else MotionTokens.DurationMedium),
        label = "loserDim",
    )
    val veil = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = alpha))
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Box(Modifier.weight(1f).fillMaxHeight().then(if (loser == Side.Mine) veil else Modifier))
        Box(Modifier.weight(1f).fillMaxHeight().then(if (loser == Side.Theirs) veil else Modifier))
    }
}

@Composable
private fun VerdictPill(card: RevealCard) {
    val scheme = MaterialTheme.colorScheme
    val tag = Modifier.testTag("reveal.pill")
    when (card.leg.verdict) {
        is LegVerdict.Win -> Pill(card.pill, tag, scheme.primaryContainer, scheme.onPrimaryContainer)
        is LegVerdict.Loss -> Pill(card.pill, tag, scheme.secondaryContainer, scheme.onSecondaryContainer)
        else -> Pill(card.pill, tag)
    }
}

/** A Time side shows the running figure; any other side shows itself. */
private fun DuelSideState.ticking(tickMs: Long): DuelSideState = if (this is DuelSideState.Time) copy(timeMs = tickMs) else this

/** True once every ticker of a puzzle nobody wins has reached its figure (a won puzzle resolves on the lock). */
private fun LegReveal.tickersDone(state: RevealState): Boolean {
    if (verdict is LegVerdict.Win || verdict is LegVerdict.Loss) return false
    val mineDone = (mine as? DuelSideState.Time)?.let { state.myTickMs >= it.timeMs } ?: true
    val theirsDone = (theirs as? DuelSideState.Time)?.let { state.theirTickMs >= it.timeMs } ?: true
    return mineDone && theirsDone
}
