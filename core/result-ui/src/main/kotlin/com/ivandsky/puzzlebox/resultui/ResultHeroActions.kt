package com.ivandsky.puzzlebox.resultui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import java.time.Instant

/**
 * The daily result's action row for [ResultHeroScreen]'s `actions` slot: the [primary] action, then Share and, when
 * [onPlayAgain] is given, Play again as labelled pills; with everything solved the countdown to [rolloverAt] stands in
 * for the primary. A null [primary] (not known yet) keeps the row in place, invisible and inert. Under the row, a
 * "Challenge <name>" button for [challenge] when [onChallenge] is given, unless the [primary] already is that challenge.
 */
@Composable
fun ResultHeroActions(
    primary: ResultPrimaryAction?,
    onPrimary: () -> Unit,
    onShare: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    modifier: Modifier = Modifier,
    rolloverAt: Instant? = null,
    challenge: ResultPrimaryAction.ChallengeFriend? = null,
    onChallenge: (() -> Unit)? = null,
) {
    val pending = primary == null
    ResultActions(
        primary = primary ?: ResultPrimaryAction.Done,
        onPrimary = { if (!pending) onPrimary() },
        onShare = { if (!pending) onShare() },
        onPlayAgain = onPlayAgain?.let { again -> { if (!pending) again() } },
        rolloverAt = rolloverAt,
        modifier = modifier.then(if (pending) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier),
        challenge = visibleChallenge(primary, challenge),
        onChallenge = onChallenge?.let { challenged -> { if (!pending) challenged() } },
    )
}
