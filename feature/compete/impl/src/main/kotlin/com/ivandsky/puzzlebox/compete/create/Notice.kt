package com.ivandsky.puzzlebox.compete.create

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Why Create did not make the duel, each in its own words; the choices above stay as they were. */
@Composable
internal fun Notice(notice: DuelNotice) {
    val text = when (notice) {
        DuelNotice.Offline -> "You're offline. Connect and try again."
        DuelNotice.Busy -> "Duels are busy today. Try again later."
        DuelNotice.NoPuzzles -> "There are no puzzles to draw for these games. Pick more games."
        is DuelNotice.EntryUndrawable ->
            if (notice.pastYou) "No solved ${notice.game} puzzle left for Past you. Change puzzle ${notice.position}."
            else "No unplayed ${notice.game} puzzle left for you both. Change puzzle ${notice.position}."
        is DuelNotice.EntryUnavailable ->
            if (notice.pastYou) "Puzzle ${notice.position} isn't one you've solved any more (its author may have taken it down). Change or remove it."
            else "Puzzle ${notice.position} is no longer available. Change or remove it."
        is DuelNotice.Failed -> notice.message ?: "Couldn't create the duel. Please try again."
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("duel.notice"))
}
