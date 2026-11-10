package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import java.time.Instant

/**
 * The daily result's action row for [ResultHeroScreen]'s `actions` slot: the [primary] action, then Share and,
 * when [onPlayAgain] is given, Play again; with everything solved the countdown to [rolloverAt] stands in for
 * the primary. A null [primary] (not known yet) keeps the row in place, invisible and inert.
 */
@Composable
fun ResultHeroActions(
    primary: ResultPrimaryAction?,
    onPrimary: () -> Unit,
    onShare: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    modifier: Modifier = Modifier,
    rolloverAt: Instant? = null,
) {
    val pending = primary == null
    ResultActions(
        primary = primary ?: ResultPrimaryAction.Done,
        onPrimary = { if (!pending) onPrimary() },
        onShare = { if (!pending) onShare() },
        onPlayAgain = onPlayAgain?.let { again -> { if (!pending) again() } },
        rolloverAt = rolloverAt,
        modifier = modifier.then(if (pending) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier),
    )
}
