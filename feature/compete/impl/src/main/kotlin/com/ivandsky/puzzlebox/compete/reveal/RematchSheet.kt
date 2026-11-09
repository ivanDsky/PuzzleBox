package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.CreateOutcome
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * "Rematch {Opponent}?" for [match]: Send new invite creates the duel again through [CreateMatch] with the same
 * settings and a fresh draw, and hands the created duel to [onCreated]; Not now (or a dismissal, once nothing is in
 * flight) calls [onDismiss]. A refusal stays on the sheet as a note under the buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RematchSheet(
    match: Match,
    opponent: String,
    onCreated: (CreateOutcome.Created) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val createMatch = koinInject<CreateMatch>()
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = { if (!sending) onDismiss() },
        modifier = modifier.testTag("reveal.rematch.sheet"),
    ) {
        RematchContent(
            opponent = opponent,
            count = match.legs.size,
            sending = sending,
            note = note,
            onSend = {
                sending = true
                note = null
                scope.launch {
                    when (val outcome = createMatch(rematchSettings(match), rematchOf = match)) {
                        is CreateOutcome.Created -> onCreated(outcome)
                        else -> {
                            sending = false
                            note = refusalNote(outcome)
                        }
                    }
                }
            },
            onDismiss = onDismiss,
        )
    }
}

/** The sheet's body alone: the question, the settings line, Send new invite, Not now and any [note]. */
@Composable
internal fun RematchContent(
    opponent: String,
    count: Int,
    sending: Boolean,
    note: String?,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Rematch $opponent?", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "Same settings: best of $count, fresh draw, same opponent, new invite.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(
            text = "Send new invite",
            onClick = onSend,
            modifier = Modifier.fillMaxWidth().testTag("reveal.rematch.send"),
            enabled = !sending,
            loading = sending,
        )
        AppTextButton("Not now", onDismiss, Modifier.fillMaxWidth().testTag("reveal.rematch.cancel"), enabled = !sending)
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("reveal.rematch.note"),
            )
        }
    }
}

private fun refusalNote(outcome: CreateOutcome): String? = when (outcome) {
    is CreateOutcome.Created -> null
    CreateOutcome.Offline -> "You're offline. Check the connection and try again."
    CreateOutcome.DailyCapReached -> "You've reached today's limit on new duels. Try again tomorrow."
    CreateOutcome.SignedOut -> "Sign in to send a rematch."
    CreateOutcome.NoPuzzles -> "There are no puzzles left to draw. Try again later."
    is CreateOutcome.Failed -> outcome.message ?: "Couldn't send the invite. Try again."
}
