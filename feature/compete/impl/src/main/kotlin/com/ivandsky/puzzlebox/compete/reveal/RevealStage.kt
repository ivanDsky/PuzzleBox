package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
private val ScoreMaxWidth = 880.dp
private val CardMaxWidth = 1080.dp
private val TipRoom = 72.dp

/**
 * The reveal at one instant of the machine's [state]: the bar, the running score, then the VS intro or the
 * last race card's puzzle, and the tap hint with Skip at the foot. The route shows the summary at End instead.
 * [wide] is the tablet's Stage: the score, the card and a strip of puzzle chips are one block centred in the room, the
 * card scaled up with much bigger figures. [onBack] and [onSkip] both jump to the summary. It draws [state] and
 * animates only its own entrances.
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
    val index = (phase as? RevealPhase.Leg)?.index ?: plan.cards.lastIndex
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).testTag("reveal.stage"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RevealTopBar(plan.me.name, plan.opponent.name, count, onBack)
        if (wide) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                StageBlock(plan, state, eyebrow, index, brands)
                Tip(state, onSkip, Modifier.align(Alignment.BottomCenter))
            }
        } else {
            RunningScore(plan, state, eyebrow, PhoneMaxWidth, wide = false, modifier = Modifier.padding(horizontal = Spacing.lg))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                when (phase) {
                    RevealPhase.VsIntro -> VsIntro(plan, brands, wide = false, modifier = Modifier.align(Alignment.Center))
                    else -> RaceBody(plan, state, index, brands)
                }
            }
            Tip(state, onSkip)
        }
    }
}

/** The tablet's centred block: the running score, the VS intro or the race card, and the chip strip under a race. */
@Composable
private fun StageBlock(plan: RevealPlan, state: RevealState, eyebrow: String, index: Int, brands: GamePlayRegistry) {
    val phase = state.phase
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewport)
                .padding(start = Spacing.xxl, end = Spacing.xxl, top = Spacing.xs, bottom = TipRoom),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.CenterVertically),
        ) {
            RunningScore(plan, state, eyebrow, ScoreMaxWidth, wide = true)
            when (phase) {
                RevealPhase.VsIntro -> VsIntro(plan, brands, wide = true)
                else -> {
                    key(index) {
                        RaceCard(
                            plan, plan.cards[index], state, position = index + 1,
                            brand = brands.brandOf(plan.cards[index].leg.gameId), wide = true,
                            modifier = Modifier.widthIn(max = CardMaxWidth),
                        )
                    }
                    RaceStrip(plan, state, index, brands, Modifier.widthIn(max = ScoreMaxWidth))
                }
            }
        }
    }
}

/** The phone's race: puzzle [index]'s card under the score, scrolling if the card is taller than the room. */
@Composable
private fun RaceBody(plan: RevealPlan, state: RevealState, index: Int, brands: GamePlayRegistry) {
    Column(
        modifier = Modifier
            .widthIn(max = PhoneMaxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
    ) {
        key(index) {
            RaceCard(plan, plan.cards[index], state, position = index + 1, brand = brands.brandOf(plan.cards[index].leg.gameId), wide = false)
        }
    }
}

/** The "Tap to speed up" hint (until the race has been sped up) beside Skip. */
@Composable
private fun Tip(state: RevealState, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (state.speed == 1) Pill("Tap to speed up", Modifier.testTag("reveal.speedUp"))
        AppTextButton("Skip", onSkip, Modifier.testTag("reveal.skip"))
    }
}

// Previews: static end states (a preview shows each entrance settled); the tickers' motion is the machine's.

internal val previewPlan = RevealPlan(
    me = Player("ann", "Ann", null),
    opponent = Player("bob", "Bob", null),
    cards = listOf(
        RevealCard(
            LegReveal(GameId("zip"), DuelSideState.Time(58_000, 0), DuelSideState.Time(72_000, 1), LegVerdict.Win(LegBasis.Time)),
            "Zip #401", "#401", RankOrder.TimeThenHints, "14 s faster", "Ann wins", 0,
        ),
        RevealCard(
            LegReveal(GameId("queens"), DuelSideState.NotPlayed, DuelSideState.Time(95_000, 0), LegVerdict.Loss(LegBasis.Time)),
            "Queens #88", "#88", RankOrder.TimeThenHints, "You didn't play · Bob wins this puzzle", "Bob wins", 1,
        ),
    ),
)

internal val previewRaceState = RevealState(RevealPhase.Leg(0), 58_000, 72_000, Side.Mine, 1, 1, 0)

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

@PuzzleBoxPreviews
@Composable
internal fun RevealRaceWidePreview() = PuzzleBoxTheme {
    RevealStage(previewPlan, previewRaceState, GamePlayRegistry(emptyList()), true, {}, {})
}
