package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberCountUp
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayerAvatar

private val HeroBorder = 2.dp
private val HeroPadding = Spacing.xxl
private val HeroAvatar = 48.dp
private val HeroAvatarOverlap = (-14).dp
private val NameAvatar = 24.dp
private val NumeralSize = 150.sp
private const val IdentityWeight = 1.15f
private const val ScoreWeight = 1f
private const val ActionsWeight = 1f

/**
 * The tablet's final page: one full-width hero card (who played, the outcome and verdict and the three tiles on the
 * left, the score in big numerals in the middle, Rematch and Share / Watch again on the right) with the aligned
 * puzzles table under it. The hero keeps the phone card's win and quiet entrances.
 */
@Composable
internal fun StageSummary(
    summary: DuelSummary,
    brands: GamePlayRegistry,
    onRematch: () -> Unit,
    onWatchAgain: () -> Unit,
    modifier: Modifier = Modifier,
    compares: Set<Int> = emptySet(),
    onCompare: (Int) -> Unit = {},
) {
    val win = summary.kind == VerdictKind.Win
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        SummaryFrame(win, HeroBorder) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(HeroPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxl),
            ) {
                Identity(summary, win, Modifier.weight(IdentityWeight))
                Score(summary, win, Modifier.weight(ScoreWeight))
                Actions(summary, onRematch, onWatchAgain, Modifier.weight(ActionsWeight))
            }
        }
        SummaryPuzzleList(summary, brands, wide = true, compares = compares, onCompare = onCompare)
    }
}

/** Two overlapping avatars beside "{Me} vs {Opponent}" and the best-of line, then the outcome pill, the verdict and the tiles. */
@Composable
private fun Identity(summary: DuelSummary, win: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(HeroAvatarOverlap)) {
                val ring = Modifier.border(2.dp, scheme.surface, CircleShape)
                PlayerAvatar(summary.me, summary.myPhotoUrl, ring, HeroAvatar, DuelTone.Me)
                PlayerAvatar(summary.opponent, summary.theirPhotoUrl, ring, HeroAvatar, DuelTone.Them)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${summary.me} vs ${summary.opponent}",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("reveal.summary.title"),
                )
                Text(summary.bestOf, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1)
            }
        }
        OutcomePill(summary, win)
        Text(
            text = summary.verdict,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
            color = if (win) LocalPrimaryText.current else scheme.onSurface,
            modifier = Modifier.testTag("reveal.summary.verdict"),
        )
        Tiles(summary.tiles)
    }
}

/** The two scores as big numerals either side of a dash, each over its player's avatar and name. */
@Composable
private fun Score(summary: DuelSummary, win: Boolean, modifier: Modifier) {
    val loss = summary.kind == VerdictKind.Loss
    val scheme = MaterialTheme.colorScheme
    val mine = if (win) LocalPrimaryText.current else scheme.onSurface
    val theirs = if (loss) secondaryText() else scheme.onSurface
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
        ScoreSide(summary.myScore, summary.me, summary.myPhotoUrl, DuelTone.Me, mine, "reveal.summary.mine", Modifier.weight(1f))
        Text(
            text = "–",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = NumeralSize, lineHeight = NumeralSize),
            color = scheme.outline,
        )
        ScoreSide(summary.theirScore, summary.opponent, summary.theirPhotoUrl, DuelTone.Them, theirs, "reveal.summary.theirs", Modifier.weight(1f))
    }
}

@Composable
private fun ScoreSide(score: Int, name: String, photoUrl: String?, tone: DuelTone, color: Color, tag: String, modifier: Modifier) {
    val shown = rememberCountUp(score.toLong(), durationMs = MotionTokens.ScoreCountUpDuration)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = shown.toString(),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = NumeralSize, lineHeight = NumeralSize).tabular(),
            color = color,
            modifier = Modifier.testTag(tag),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PlayerAvatar(name, photoUrl, size = NameAvatar, tone = tone)
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
