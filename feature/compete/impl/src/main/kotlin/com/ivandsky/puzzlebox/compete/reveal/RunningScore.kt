package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.PlayerAvatar

private val BarHeight = 44.dp
private val BarAvatar = 28.dp
private val WideBarHeight = 48.dp
private val WideBarAvatar = 22.dp
private const val AvatarVeilAlpha = 0.7f

/**
 * The running score above the race: the [eyebrow] ("Best of N", "Puzzle i of N", "Final") over two score bars side by
 * side, mine in the primary container and the opponent's in the secondary, each with avatar, name and score. The
 * bars span the race column, at most [maxWidth] wide; [wide] is the tablet's slightly taller bar with larger text.
 */
@Composable
internal fun RunningScore(
    plan: RevealPlan,
    state: RevealState,
    eyebrow: String,
    maxWidth: Dp,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = maxWidth).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth().testTag("reveal.score"), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ScoreBar(plan.me, state.myScore, DuelTone.Me, wide, Modifier.weight(1f))
            ScoreBar(plan.opponent, state.theirScore, DuelTone.Them, wide, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RowScope.ScoreBar(player: Player, score: Int, tone: DuelTone, wide: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        DuelTone.Me -> scheme.primaryContainer to scheme.onPrimaryContainer
        DuelTone.Them -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    Row(
        modifier = modifier
            .height(if (wide) WideBarHeight else BarHeight)
            .background(container, PillShape)
            .padding(start = Spacing.sm, end = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PlayerAvatar(
            name = player.name,
            photoUrl = player.photoUrl,
            size = if (wide) WideBarAvatar else BarAvatar,
            tone = tone,
            fill = scheme.surface.copy(alpha = AvatarVeilAlpha),
            onFill = content,
        )
        Text(
            text = player.name,
            style = if (wide) {
                MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.titleSmall
            },
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = score.toString(),
            style = if (wide) {
                MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold).tabular()
            } else {
                MaterialTheme.typography.titleMedium.tabular()
            },
            color = content,
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun RunningScorePreview() = PuzzleBoxTheme {
    val plan = RevealPlan(
        me = Player("ivan", "Ivan", null),
        opponent = Player("sam", "Sam", null),
        cards = emptyList(),
    )
    RunningScore(plan, RevealState(RevealPhase.Score, 0, 0, null, 1, 2, 1), "Final", maxWidth = 448.dp, wide = false, modifier = Modifier.padding(Spacing.lg))
}
