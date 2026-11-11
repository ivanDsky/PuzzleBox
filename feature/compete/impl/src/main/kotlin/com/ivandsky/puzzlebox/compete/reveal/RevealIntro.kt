package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.resultBeat
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import kotlinx.coroutines.delay

private val PhoneAvatar = 112.dp
private val TabletAvatar = 160.dp
private val PhoneRowWidth = 448.dp
private val TabletRowWidth = 640.dp
private val PhoneMarkWidth = 64.dp
private val TabletMarkWidth = 96.dp
private val PhoneGamesWidth = 320.dp
private val TabletGamesWidth = 420.dp
private const val VsStampDelayMs = 160L

/** A transition that is already at its target in a preview, and enters on first composition otherwise. */
@Composable
private fun rememberEnterState(): MutableTransitionState<Boolean> {
    val settled = LocalInspectionMode.current
    return remember { MutableTransitionState(settled).apply { targetState = true } }
}

/**
 * The intro: the two avatar discs slide in from opposite edges and settle close to a big "VS" that stamps in between
 * them, each over its name and "You" / "Opponent"; then the "Best of {N}" eyebrow fades in over the puzzles' glyphs,
 * staggered. Every motion is finite and fits inside the machine's intro.
 */
@Composable
internal fun VsIntro(plan: RevealPlan, brands: GamePlayRegistry, wide: Boolean, modifier: Modifier = Modifier) {
    val settled = LocalInspectionMode.current
    var stamped by remember { mutableStateOf(settled) }
    LaunchedEffect(Unit) {
        delay(VsStampDelayMs)
        stamped = true
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg).testTag("reveal.intro"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
    ) {
        Row(Modifier.widthIn(max = if (wide) TabletRowWidth else PhoneRowWidth).fillMaxWidth(), verticalAlignment = Alignment.Top) {
            IntroPlayer(plan.me, "You", DuelTone.Me, wide, fromStart = true, Modifier.weight(1f))
            Box(
                modifier = Modifier.width(if (wide) TabletMarkWidth else PhoneMarkWidth).height(if (wide) TabletAvatar else PhoneAvatar),
                contentAlignment = Alignment.Center,
            ) {
                Stamp(visible = stamped) { VsMark(wide) }
            }
            IntroPlayer(plan.opponent, "Opponent", DuelTone.Them, wide, fromStart = false, Modifier.weight(1f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                text = "BEST OF ${plan.cards.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.resultBeat(ResultBeats.Time),
            )
            GameGlyphs(plan, brands, if (wide) TabletGamesWidth else PhoneGamesWidth)
        }
    }
}

@Composable
private fun GameGlyphs(plan: RevealPlan, brands: GamePlayRegistry, maxWidth: Dp) {
    FlowRow(
        modifier = Modifier.widthIn(max = maxWidth),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        plan.cards.forEachIndexed { index, card ->
            GameGlyph(brands.brandOf(card.leg.gameId), GameGlyphSize.Chip, Modifier.resultBeat(ResultBeats.Time, index))
        }
    }
}

/** The "VS" mark: display type, no disc, in the ink colour. */
@Composable
private fun VsMark(wide: Boolean) {
    val size = if (wide) 72.sp else 56.sp
    Text(
        text = "VS",
        style = MaterialTheme.typography.displayLarge.copy(fontSize = size, lineHeight = size, letterSpacing = (-0.5).sp),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
    )
}

/** One avatar disc over its name (two lines at most) and its "You" / "Opponent" eyebrow, sliding in from its own edge. */
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
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            PlayerAvatar(player.name, player.photoUrl, size = if (wide) TabletAvatar else PhoneAvatar, tone = tone)
            Text(
                text = player.name,
                style = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
