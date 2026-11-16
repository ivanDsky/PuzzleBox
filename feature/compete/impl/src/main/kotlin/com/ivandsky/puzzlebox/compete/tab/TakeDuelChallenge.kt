package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.ivandsky.puzzlebox.compete.create.DuelChallenge

/**
 * Takes a pending [challenge] once: [open] runs with it, then [onTaken] tells the host to stop offering it, so a
 * recomposition or a later return to this screen does not open it again. No challenge does nothing.
 */
@Composable
internal fun TakeDuelChallenge(challenge: DuelChallenge?, onTaken: () -> Unit, open: (DuelChallenge) -> Unit) {
    val currentOpen by rememberUpdatedState(open)
    val currentTaken by rememberUpdatedState(onTaken)
    LaunchedEffect(challenge) {
        if (challenge != null) {
            currentOpen(challenge)
            currentTaken()
        }
    }
}
