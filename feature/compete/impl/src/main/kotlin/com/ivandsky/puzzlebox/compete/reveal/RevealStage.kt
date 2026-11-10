package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.compete.duel.Pill
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.ScoreChip
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.resultBeat
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.delay

private val PhoneAvatar = 112.dp
private val TabletAvatar = 160.dp
private val PhoneMaxWidth = 560.dp
private val TabletMaxWidth = 720.dp
private val TopBarHeight = 64.dp
private val VsStampSize = 56.dp
private const val VsStampDelayMs = 160L

/**
 * The reveal at one instant of the machine's [state] (handoff 03-reveal §4–§5): the chrome, then the VS intro, or the
 * last race card's puzzle with the running score. The route shows the summary at End instead.
 * [wide] is the tablet layout. It draws [state] and animates only its own entrances.
 */
@Composable
internal fun RevealStage(
    plan: RevealPlan,
    state: RevealState,
    brands: GamePlayRegistry,
    wide: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).testTag("reveal.stage")) {
        RevealTopBar(plan, onSkip)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (val phase = state.phase) {
                RevealPhase.VsIntro -> VsIntro(plan, brands, wide)
                else -> {
                    val index = (phase as? RevealPhase.Leg)?.index ?: plan.cards.lastIndex
                    RaceBody(plan, state, index, brands, wide)
                }
            }
            if (state.speed == 1) {
                SpeedUpHint(Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xl))
            }
        }
    }
}

/** "{Me} vs {Opponent}" over "Duel · Best of {N}", and Skip. */
@Composable
private fun RevealTopBar(plan: RevealPlan, onSkip: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "${plan.me.name} vs ${plan.opponent.name}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("reveal.title"),
            )
            Text(
                text = "Duel · Best of ${plan.cards.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AppTextButton("Skip", onSkip, Modifier.testTag("reveal.skip"))
    }
}

@Composable
private fun SpeedUpHint(modifier: Modifier = Modifier) {
    Pill("Tap to speed up", modifier.testTag("reveal.speedUp"))
}

/** A transition that is already at its target in a preview, and enters on first composition otherwise. */
@Composable
private fun rememberEnterState(): MutableTransitionState<Boolean> {
    val settled = LocalInspectionMode.current
    return remember { MutableTransitionState(settled).apply { targetState = true } }
}

/**
 * The intro: the two avatar discs slide in from opposite edges, the VS stamp pops, the "Best of {N}" eyebrow fades in
 * and the puzzles' glyphs follow, staggered. Every motion is finite and fits inside the machine's intro.
 */
@Composable
private fun VsIntro(plan: RevealPlan, brands: GamePlayRegistry, wide: Boolean) {
    val settled = LocalInspectionMode.current
    var stamped by remember { mutableStateOf(settled) }
    LaunchedEffect(Unit) {
        delay(VsStampDelayMs)
        stamped = true
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg).testTag("reveal.intro"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Text(
            text = "Best of ${plan.cards.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.resultBeat(ResultBeats.Time),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IntroPlayer(plan.me, "You", DuelTone.Me, wide, fromStart = true, Modifier.weight(1f))
            Stamp(visible = stamped) { VsMark() }
            IntroPlayer(plan.opponent, "Opponent", DuelTone.Them, wide, fromStart = false, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            plan.cards.forEachIndexed { index, card ->
                GameGlyph(brands.brandOf(card.leg.gameId), GameGlyphSize.Tile, Modifier.resultBeat(ResultBeats.Time, index))
            }
        }
    }
}

@Composable
private fun VsMark() {
    Box(
        modifier = Modifier.size(VsStampSize).background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "VS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/** One avatar disc under its [label] and name (two lines at most), sliding in from its own edge. */
@Composable
private fun IntroPlayer(player: Player, label: String, tone: DuelTone, wide: Boolean, fromStart: Boolean, modifier: Modifier) {
    val offscreen = if (fromStart) -1 else 1
    AnimatedVisibility(
        visibleState = rememberEnterState(),
        modifier = modifier,
        enter = slideInHorizontally(tween(MotionTokens.DurationLong, easing = MotionTokens.EasingEmphasizedDecelerate)) { full ->
            offscreen * full * 2
        } + fadeIn(tween(MotionTokens.DurationMedium)),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PlayerAvatar(player.name, player.photoUrl, size = if (wide) TabletAvatar else PhoneAvatar, tone = tone)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = player.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The race: the running score over puzzle [index]'s card. */
@Composable
private fun RaceBody(plan: RevealPlan, state: RevealState, index: Int, brands: GamePlayRegistry, wide: Boolean) {
    Column(
        modifier = Modifier
            .widthIn(max = if (wide) TabletMaxWidth else PhoneMaxWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        ScoreRow(plan, state, "Best of ${plan.cards.size}")
        key(index) {
            RaceCard(plan, plan.cards[index], state, position = index + 1, brands.brandOf(plan.cards[index].leg.gameId), wide)
        }
    }
}

@Composable
private fun ScoreRow(plan: RevealPlan, state: RevealState, eyebrow: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.testTag("reveal.score")) {
            ScoreChip(state.myScore.toString(), label = plan.me.name, tone = DuelTone.Me)
            ScoreChip(state.theirScore.toString(), label = plan.opponent.name, tone = DuelTone.Them)
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
    RevealStage(previewPlan, RevealState(RevealPhase.VsIntro, 0, 0, null, 1, 0, 0), GamePlayRegistry(emptyList()), false, {})
}

@PuzzleBoxPreviews
@Composable
internal fun RevealRacePreview() = PuzzleBoxTheme {
    RevealStage(previewPlan, previewRaceState, GamePlayRegistry(emptyList()), false, {})
}
