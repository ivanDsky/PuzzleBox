package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import java.time.Instant

/** The header's puzzle line, as the play top bar names it: "Queens · #918", "Tango · Practice", or just the name. */
internal fun resultTitle(game: String, number: Int?, practice: Boolean): String = when {
    number != null -> "$game · #$number"
    practice -> "$game · Practice"
    else -> game
}

/**
 * The result's actions, stacked: the primary spans the full width (the button [step] gives for it: `result.next`,
 * `result.done`, and one tag and one copy for each other next thing; once every daily is solved the [ResultCountdown]
 * to [rolloverAt] stands in its place). Under it, Share (tag `result.share`) and, unless [onPlayAgain] is null,
 * Play again (`result.playAgain`) share a row as labelled pills: Share keeps its own width and Play again takes the
 * rest, so both labels hold one line on a 360 dp phone. Under the row: "Challenge <name>" (`result.challenge`)
 * when a [challenge] and its [onChallenge] are given.
 */
@Composable
internal fun ResultActions(
    primary: ResultPrimaryAction,
    onPrimary: () -> Unit,
    onShare: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    rolloverAt: Instant?,
    modifier: Modifier = Modifier,
    challenge: ResultPrimaryAction.ChallengeFriend? = null,
    onChallenge: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ActionRow(primary, onPrimary, onShare, onPlayAgain, rolloverAt)
        if (challenge != null && onChallenge != null) {
            SecondaryButton(
                text = "Challenge ${challenge.name}",
                onClick = onChallenge,
                modifier = Modifier.fillMaxWidth().testTag("result.challenge"),
            )
        }
    }
}

@Composable
private fun ActionRow(
    primary: ResultPrimaryAction,
    onPrimary: () -> Unit,
    onShare: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    rolloverAt: Instant?,
) {
    val step = primary.step()
    val primaryButton: @Composable (Modifier) -> Unit = { modifier ->
        if (step == null) {
            ResultCountdown(rolloverAt, modifier)
        } else {
            PrimaryButton(text = step.text, onClick = onPrimary, modifier = modifier.testTag(step.tag))
        }
    }
    val shareButton: @Composable (Modifier) -> Unit = { modifier ->
        SecondaryButton(
            text = "Share",
            onClick = onShare,
            modifier = modifier.testTag("result.share").semantics { contentDescription = "Share" },
            icon = PuzzleIcons.Share,
            maxLines = 1,
        )
    }
    val playAgainButton: @Composable (Modifier, () -> Unit) -> Unit = { modifier, onClick ->
        SecondaryButton(
            text = "Play again",
            onClick = onClick,
            modifier = modifier.testTag("result.playAgain").semantics { contentDescription = "Play again" },
            icon = PuzzleIcons.Retry,
            maxLines = 1,
        )
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        primaryButton(Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            // Share keeps its own width; Play again takes the rest, so both labels fit a phone row.
            shareButton(if (onPlayAgain == null) Modifier.weight(1f) else Modifier)
            onPlayAgain?.let { playAgainButton(Modifier.weight(1f), it) }
        }
    }
}
