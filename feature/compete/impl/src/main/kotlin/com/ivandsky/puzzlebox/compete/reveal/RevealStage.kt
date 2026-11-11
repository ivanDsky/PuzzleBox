package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.duel.Pill
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId

private val PhoneMaxWidth = 448.dp
private val TabletMaxWidth = 720.dp

/**
 * The reveal at one instant of the machine's [state]: the bar, the running score, then the VS intro or the
 * last race card's puzzle, and the tap hint with Skip at the foot. The route shows the summary at End instead.
 * [wide] is the tablet layout; [onBack] and [onSkip] both jump to the summary. It draws [state] and animates only
 * its own entrances.
 */
@Composable
internal fun RevealStage(
    plan: RevealPlan,
    state: RevealState,
    brands: GamePlayRegistry,
    wide: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = plan.cards.size
    val phase = state.phase
    val eyebrow = when (phase) {
        RevealPhase.VsIntro -> "Best of $count"
        is RevealPhase.Leg -> "Puzzle ${phase.index + 1} of $count"
        else -> "Final"
    }
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).testTag("reveal.stage"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RevealTopBar(plan.me.name, plan.opponent.name, count, onBack)
        RunningScore(
            plan, state, eyebrow, if (wide) TabletMaxWidth else PhoneMaxWidth,
            Modifier.padding(horizontal = Spacing.lg),
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            when (phase) {
                RevealPhase.VsIntro -> VsIntro(plan, brands, wide, Modifier.align(Alignment.Center))
                else -> RaceBody(plan, state, (phase as? RevealPhase.Leg)?.index ?: plan.cards.lastIndex, brands, wide)
            }
        }
        Row(
            modifier = Modifier.padding(vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (state.speed == 1) Pill("Tap to speed up", Modifier.testTag("reveal.speedUp"))
            AppTextButton("Skip", onSkip, Modifier.testTag("reveal.skip"))
        }
    }
}

/** The race: puzzle [index]'s card under the score, scrolling if the card is taller than the room. */
@Composable
private fun RaceBody(plan: RevealPlan, state: RevealState, index: Int, brands: GamePlayRegistry, wide: Boolean) {
    Column(
        modifier = Modifier
            .widthIn(max = if (wide) TabletMaxWidth else PhoneMaxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
    ) {
        key(index) {
            RaceCard(plan, plan.cards[index], state, position = index + 1, brands.brandOf(plan.cards[index].leg.gameId), wide)
        }
    }
}

// Previews: static end states (a preview shows each entrance settled); the tickers' motion is the machine's.

private val previewPlan = RevealPlan(
    me = Player("ann", "Ann", null),
    opponent = Player("bob", "Bob", null),
    cards = listOf(
        RevealCard(
            LegReveal(GameId("zip"), DuelSideState.Time(58_000, 0), DuelSideState.Time(72_000, 1), LegVerdict.Win(LegBasis.Time)),
            "Zip #401", RankOrder.TimeThenHints, "14 s faster", "Ann wins",
        ),
        RevealCard(
            LegReveal(GameId("queens"), DuelSideState.NotPlayed, DuelSideState.Time(95_000, 0), LegVerdict.Loss(LegBasis.Time)),
            "Queens #88", RankOrder.TimeThenHints, "You didn't play · Bob wins this puzzle", "Bob wins",
        ),
    ),
)

private val previewRaceState = RevealState(RevealPhase.Leg(0), 58_000, 72_000, Side.Mine, 1, 1, 0)

@PuzzleBoxPreviews
@Composable
internal fun RevealIntroPreview() = PuzzleBoxTheme {
    RevealStage(previewPlan, RevealState(RevealPhase.VsIntro, 0, 0, null, 1, 0, 0), GamePlayRegistry(emptyList()), false, {}, {})
}

@PuzzleBoxPreviews
@Composable
internal fun RevealRacePreview() = PuzzleBoxTheme {
    RevealStage(previewPlan, previewRaceState, GamePlayRegistry(emptyList()), false, {}, {})
}
