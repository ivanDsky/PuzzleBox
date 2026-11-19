package com.ivandsky.puzzlebox.resultui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.resultBeat
import com.ivandsky.puzzlebox.domain.moments.Moment
import com.ivandsky.puzzlebox.gameui.GameBrand

private val RowMinHeight = 32.dp
private val RowIconSize: Dp = 20.dp

/**
 * The moment card (L1, tag `result.moments`): one card under the hero (above the side card on two panes) with a row per [moments] entry, strongest first
 * (tags `result.moment.0`, `result.moment.1`, ...). With no moments it draws nothing and takes no room, [gap] included,
 * so a Result without moments (a duel leg) is laid out without it. [gap] is the space kept above the card, or below it when
 * [gapBelow] (the card sits first in the two-pane side column).
 *
 * The rows of the first draw are simply there (the card itself lands with the hero's blocks beat). A moment that
 * arrives later, a friend-board one, slides in: the card grows by a row and the row fades in. Reduced motion skips the
 * slide. The buzz of the strongest moment fires from here, once ([ResultMomentHapticsEffect]).
 */
@Composable
internal fun ResultMomentCard(
    moments: List<HeroMoment>,
    brand: GameBrand,
    modifier: Modifier = Modifier,
    gap: Dp = Spacing.lg,
    gapBelow: Boolean = false,
) {
    ResultMomentHapticsEffect(moments.firstOrNull()?.moment)
    val reduced = rememberReducedMotion()
    val shownOnFirstDraw = remember { moments.isNotEmpty() }
    val firstDraw = remember { moments.map { it.key }.toSet() }
    AnimatedVisibility(
        visible = moments.isNotEmpty(),
        modifier = modifier,
        enter = if (reduced) EnterTransition.None else fadeIn() + expandVertically(),
        exit = ExitTransition.None,
    ) {
        OutlinedCard(
            modifier = Modifier
                .padding(top = if (gapBelow) 0.dp else gap, bottom = if (gapBelow) gap else 0.dp)
                .fillMaxWidth()
                .then(if (shownOnFirstDraw) Modifier.resultBeat(ResultBeats.Blocks) else Modifier)
                .testTag("result.moments"),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                moments.forEachIndexed { index, moment ->
                    key(moment.key) {
                        val instant = reduced || !shownOnFirstDraw || moment.key in firstDraw
                        MomentRow(moment, brand, instant, Modifier.semantics(mergeDescendants = true) {}.testTag("result.moment.$index"))
                    }
                }
            }
        }
    }
}

@Composable
private fun MomentRow(moment: HeroMoment, brand: GameBrand, instant: Boolean, modifier: Modifier) {
    val visible = remember { MutableTransitionState(instant) }.apply { targetState = true }
    AnimatedVisibility(
        visibleState = visible,
        modifier = modifier,
        enter = fadeIn() + expandVertically(),
        exit = ExitTransition.None,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = moment.moment.icon(),
                contentDescription = null,
                tint = brand.accent.colors().accent,
                modifier = Modifier.size(RowIconSize),
            )
            Text(
                text = moment.text,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Moment.icon(): ImageVector = when (this) {
    Moment.PersonalBest, is Moment.FirstSubX, is Moment.TopThreeFriends, is Moment.PassedFriend -> PuzzleIcons.Trophy
    is Moment.FullSetDone -> PuzzleIcons.Check
    is Moment.Streak -> PuzzleIcons.Flame
}
