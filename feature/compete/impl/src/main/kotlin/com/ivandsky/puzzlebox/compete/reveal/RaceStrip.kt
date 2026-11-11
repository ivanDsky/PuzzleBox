package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayerAvatar

private val ChipHeight = 48.dp
private val ChipBorder = 2.dp
private val ResultAvatar = 24.dp
private const val PendingGlyphAlpha = 0.6f

/** Where a puzzle stands in the race: not reached, being raced, or settled. */
internal enum class ChipState { Pending, Racing, Done }

/**
 * The race's progress as a strip of puzzle chips under the card, wrapping and centred: each chip is the game's glyph and
 * the puzzle's number. Puzzles not yet reached have a thin outline and a faded glyph, the one on the card is outlined in
 * its game's accent, and a settled one is filled and ends in the winner's avatar (or "=" for a draw).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RaceStrip(plan: RevealPlan, state: RevealState, current: Int, brands: GamePlayRegistry, modifier: Modifier = Modifier) {
    val racing = state.phase is RevealPhase.Leg
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        plan.cards.forEachIndexed { index, card ->
            val settled = !racing || state.locked != null || card.leg.tickersDone(state)
            val chipState = when {
                index < current -> ChipState.Done
                index == current && settled -> ChipState.Done
                index == current -> ChipState.Racing
                else -> ChipState.Pending
            }
            RaceChip(plan, card, brands.brandOf(card.leg.gameId), chipState)
        }
    }
}

@Composable
private fun RaceChip(plan: RevealPlan, card: RevealCard, brand: GameBrand, chipState: ChipState) {
    val scheme = MaterialTheme.colorScheme
    val shape = PillShape
    val base = Modifier.height(ChipHeight)
    val frame = when (chipState) {
        ChipState.Pending -> base.border(BorderStroke(ChipBorder, scheme.outlineVariant), shape)
        ChipState.Racing -> base.border(BorderStroke(ChipBorder, brand.accent.colors().accent), shape)
        ChipState.Done -> base.background(scheme.surfaceContainerHigh, shape)
    }
    Row(
        modifier = frame.padding(start = Spacing.sm, end = if (chipState == ChipState.Done) Spacing.sm else Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        GameGlyph(brand, GameGlyphSize.Chip, if (chipState == ChipState.Pending) Modifier.alpha(PendingGlyphAlpha) else Modifier)
        Text(
            text = card.chip,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold).tabular(),
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (chipState == ChipState.Done) ChipResult(plan, card)
    }
}

/** The winner's avatar, or "=" for a draw; a void puzzle shows neither. */
@Composable
private fun ChipResult(plan: RevealPlan, card: RevealCard) {
    when (card.leg.verdict) {
        is LegVerdict.Win -> PlayerAvatar(plan.me.name, plan.me.photoUrl, size = ResultAvatar, tone = DuelTone.Me)
        is LegVerdict.Loss -> PlayerAvatar(plan.opponent.name, plan.opponent.photoUrl, size = ResultAvatar, tone = DuelTone.Them)
        LegVerdict.Draw -> Box(
            modifier = Modifier.size(ResultAvatar).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("=", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> Unit
    }
}

@PuzzleBoxPreviews
@Composable
internal fun RaceStripPreview() = PuzzleBoxTheme {
    RaceStrip(
        plan = previewPlan,
        state = previewRaceState,
        current = 0,
        brands = GamePlayRegistry(emptyList()),
        modifier = Modifier.padding(Spacing.lg),
    )
}
