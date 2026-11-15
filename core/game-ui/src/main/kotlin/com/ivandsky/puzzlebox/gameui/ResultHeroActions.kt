package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.ivandsky.puzzlebox.designsystem.Spacing
import java.time.Instant

/**
 * The daily result's action row for [ResultHeroScreen]'s `actions` slot: the [primary] action, then the Share and,
 * when [onPlayAgain] is given, Play again icon buttons; with everything solved the countdown to [rolloverAt] stands in for
 * the primary. A null [primary] (not known yet) keeps the row in place, invisible and inert.
 *
 * A non-null [linkedInPost] adds the Post to LinkedIn button above the row, calling [onLinkedInPost]; null leaves
 * the row exactly as it is without it.
 */
@Composable
fun ResultHeroActions(
    primary: ResultPrimaryAction?,
    onPrimary: () -> Unit,
    onShare: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    modifier: Modifier = Modifier,
    rolloverAt: Instant? = null,
    linkedInPost: LinkedInPostButtonState? = null,
    onLinkedInPost: () -> Unit = {},
) {
    val pending = primary == null
    val shell = modifier.then(if (pending) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier)
    val row: @Composable (Modifier) -> Unit = { rowModifier ->
        ResultActions(
            primary = primary ?: ResultPrimaryAction.Done,
            onPrimary = { if (!pending) onPrimary() },
            onShare = { if (!pending) onShare() },
            onPlayAgain = onPlayAgain?.let { again -> { if (!pending) again() } },
            rolloverAt = rolloverAt,
            modifier = rowModifier,
        )
    }
    if (linkedInPost == null) {
        row(shell)
    } else {
        Column(modifier = shell, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            LinkedInPostButton(linkedInPost, onClick = { if (!pending) onLinkedInPost() })
            row(Modifier)
        }
    }
}
