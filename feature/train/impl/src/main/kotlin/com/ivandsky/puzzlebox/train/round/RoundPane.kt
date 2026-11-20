package com.ivandsky.puzzlebox.train.round

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightOverlay
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.Snippet
import com.ivandsky.puzzlebox.train.domain.RecordAnswer

/** The smallest size a control may be touched at. */
private val TouchTarget = 48.dp

/** What a round needs from the game it trains: the board a snippet is played on and the Spotlight that answers it. */
internal class RoundBoard(
    val board: @Composable (snippet: Snippet, modifier: Modifier) -> Unit,
    val spotlight: (Snippet) -> Spotlight,
)

/**
 * A technique's round: [name] as the title, then by [state] a spinner while it builds or loads, the empty message, the
 * needs-a-connection or can't-load message, the snippet with its board and controls, or the summary. [showBack] adds a
 * back arrow to the title row (the phone, where the round covers the list); [onClose] is what back and the summary's
 * and the empty message's action do. [onTryAgain] is the needs-a-connection action, which fetches a puzzle,
 * [onTryAnother] the can't-load action, which opens a puzzle already on the device, and [onDrill] the summary's Play a
 * full puzzle.
 */
@Composable
internal fun RoundPane(
    name: String,
    state: RoundState,
    showBack: Boolean,
    onClose: () -> Unit,
    board: RoundBoard,
    onShowMe: (Snippet) -> Unit,
    onNext: () -> Unit,
    onTryAgain: () -> Unit,
    onTryAnother: () -> Unit,
    onDrill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(bottom = Spacing.lg).testTag("train.round")) {
        RoundHeader(name, (state as? RoundState.Question)?.let { it.index + 1 to it.total }, showBack, onClose)
        Spacer(Modifier.size(Spacing.sm))
        when (state) {
            RoundState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            RoundState.Empty -> RoundEmpty(name, onClose)
            RoundState.NeedsConnection -> RoundNeedsConnection(name, onTryAgain)
            is RoundState.CantLoad -> RoundCantLoad(name, onTryAnother)
            is RoundState.Question -> RoundQuestion(state, board, onShowMe, onNext)
            is RoundState.Summary -> RoundSummary(state, onClose, onDrill)
        }
    }
}

/** The title row of a technique's round or drill: the back arrow if [showBack], then [name] and the snippet [position] as (n, of), if any. */
@Composable
internal fun RoundHeader(name: String, position: Pair<Int, Int>?, showBack: Boolean, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showBack) {
            IconButton(onClick = onClose, Modifier.size(TouchTarget).testTag("train.back")) {
                Icon(PuzzleIcons.Back, contentDescription = "Back to techniques")
            }
            Spacer(Modifier.width(Spacing.xs))
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            if (position != null) {
                Text(
                    "Snippet ${position.first} of ${position.second}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.RoundQuestion(state: RoundState.Question, board: RoundBoard, onShowMe: (Snippet) -> Unit, onNext: () -> Unit) {
    val snippet = state.snippet
    Text(snippet.prompt, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = Spacing.md), contentAlignment = Alignment.Center) {
        // One key per snippet, so the next board starts from its own state.
        key(state.index) {
            Box(Modifier.aspectRatio(1f)) {
                board.board(snippet, Modifier.fillMaxSize())
                if (state.answered) SpotlightOverlay(remember(snippet) { board.spotlight(snippet) })
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = TouchTarget),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val answer = state.answer
        if (answer == null) {
            SecondaryButton("Show me", onClick = { onShowMe(snippet) }, Modifier.testTag("train.showMe"))
        } else {
            VerdictLabel(answer, Modifier.weight(1f))
            PrimaryButton("Next", onClick = onNext, Modifier.testTag("train.next"))
        }
    }
}

@Composable
private fun VerdictLabel(answer: RecordAnswer.Answer, modifier: Modifier) {
    val (text, color) = verdictText(answer)
    Text(
        text,
        modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("train.verdict"),
        style = MaterialTheme.typography.titleMedium,
        color = color,
    )
}

@Composable
private fun verdictText(answer: RecordAnswer.Answer): Pair<String, Color> = when (answer) {
    RecordAnswer.Answer.ShowMe -> "Here is the step" to MaterialTheme.colorScheme.onSurface
    is RecordAnswer.Answer.Verdict -> when (answer.verdict) {
        TrainerVerdict.Right -> "Right" to MaterialTheme.colorScheme.primary
        TrainerVerdict.AnotherWay -> "True but another way" to MaterialTheme.colorScheme.onSurface
        TrainerVerdict.Wrong -> "Wrong" to MaterialTheme.colorScheme.error
    }
}
