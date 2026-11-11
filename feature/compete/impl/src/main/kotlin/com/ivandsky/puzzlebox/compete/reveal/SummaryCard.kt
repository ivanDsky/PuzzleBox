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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberCountUp
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.PlayerAvatar

private val WinBorder = 3.dp
private val HeadAvatar = 40.dp
private val HeadAvatarOverlap = (-12).dp
private val NameAvatar = 24.dp
private val NumeralSize = 64.sp

/**
 * The phone's final-score card: who played, the outcome pill, the two numerals over their names, the verdict, the three
 * tiles, Rematch and Share / Watch again. A win stamps in with a sweep and a burst, in the primary accent; a loss or a
 * draw fades in slowly in the outline colour, with no burst, so a loss stays quiet. The numerals count up from 0.
 */
@Composable
internal fun SummaryCard(summary: DuelSummary, onRematch: () -> Unit, onWatchAgain: () -> Unit, modifier: Modifier = Modifier) {
    val win = summary.kind == VerdictKind.Win
    SummaryFrame(win, WinBorder, modifier) {
        CardBody(summary, win, onRematch, onWatchAgain)
    }
}

@Composable
private fun CardBody(summary: DuelSummary, win: Boolean, onRematch: () -> Unit, onWatchAgain: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CardHead(summary, win)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            ScoreSide(summary.myScore, summary.me, summary.myPhotoUrl, DuelTone.Me, win, "reveal.summary.mine", Modifier.weight(1f))
            Text(
                text = "–",
                style = MaterialTheme.typography.displayMedium.copy(fontSize = NumeralSize, lineHeight = NumeralSize),
                color = scheme.onSurfaceVariant,
            )
            ScoreSide(summary.theirScore, summary.opponent, summary.theirPhotoUrl, DuelTone.Them, win, "reveal.summary.theirs", Modifier.weight(1f))
        }
        Text(
            text = summary.verdict,
            style = MaterialTheme.typography.headlineMedium,
            color = if (win) LocalPrimaryText.current else scheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().testTag("reveal.summary.verdict"),
        )
        Tiles(summary.tiles)
        Actions(summary, onRematch, onWatchAgain)
    }
}

/** Two overlapping avatars, "{Me} vs {Opponent}" over the best-of line, and the outcome pill. */
@Composable
private fun CardHead(summary: DuelSummary, win: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(HeadAvatarOverlap)) {
            val ring = Modifier.border(2.dp, scheme.surface, CircleShape)
            PlayerAvatar(summary.me, summary.myPhotoUrl, ring, HeadAvatar, DuelTone.Me)
            PlayerAvatar(summary.opponent, summary.theirPhotoUrl, ring, HeadAvatar, DuelTone.Them)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = "${summary.me} vs ${summary.opponent}",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("reveal.summary.title"),
            )
            Text(summary.bestOf, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        OutcomePill(summary, win)
    }
}

/** One big numeral over its player's avatar and name. */
@Composable
private fun ScoreSide(score: Int, name: String, photoUrl: String?, tone: DuelTone, win: Boolean, tag: String, modifier: Modifier) {
    val shown = rememberCountUp(score.toLong(), durationMs = MotionTokens.ScoreCountUpDuration)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = shown.toString(),
            style = MaterialTheme.typography.displayLarge.copy(fontSize = NumeralSize, lineHeight = NumeralSize).tabular(),
            color = if (win && tone == DuelTone.Me) LocalPrimaryText.current else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(tag),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PlayerAvatar(name, photoUrl, size = NameAvatar, tone = tone)
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
