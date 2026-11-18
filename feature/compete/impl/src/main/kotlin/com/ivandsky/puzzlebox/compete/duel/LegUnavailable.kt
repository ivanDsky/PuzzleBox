package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing

internal const val TAKEN_DOWN_TEXT = "This puzzle was taken down by its author"
internal const val OFFLINE_TEXT = "You're offline. Connect and try again."

/**
 * What each friend's puzzle of the duel has answered so far, by leg index, and how to ask one again. A leg with no answer
 * yet is still being read; the page never waits for it.
 */
internal class LegReads(private val answers: Map<Int, LegBoard>, val retry: (Int) -> Unit) {
    /** [row] as the page should offer it: Start / Resume is off while its puzzle is taken down or cannot be read now. */
    fun shown(row: LegRowModel): LegRowModel =
        if (answers[row.legIndex].let { it == LegBoard.TakenDown || it == LegBoard.Offline }) row.copy(actionEnabled = false) else row

    fun answer(legIndex: Int): LegBoard? = answers[legIndex]

    companion object {
        val None = LegReads(emptyMap(), retry = {})
    }
}

/**
 * Reads the puzzle of every custom leg in [legs], each in its own effect, so one slow or offline puzzle never holds up the
 * others or the page. [retry] reads one leg again. Nothing is read for [legs] empty.
 */
@Composable
internal fun rememberLegReads(legs: List<Int>, board: suspend (Int) -> LegBoard): LegReads {
    val answers = remember { mutableStateMapOf<Int, LegBoard>() }
    val attempts = remember { mutableStateMapOf<Int, Int>() }
    legs.forEach { index ->
        key(index) {
            LaunchedEffect(index, attempts[index]) { answers[index] = board(index) }
        }
    }
    return remember(answers, attempts) {
        LegReads(answers) { index ->
            answers.remove(index)
            attempts[index] = (attempts[index] ?: 0) + 1
        }
    }
}

/** Why a friend's puzzle cannot be started: its author took it down, or this device is offline and never saved it (with Retry). */
@Composable
internal fun LegUnavailable(read: LegBoard?, legIndex: Int, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when (read) {
        LegBoard.TakenDown -> Note(TAKEN_DOWN_TEXT, modifier.testTag("duel.legs.takenDown.$legIndex"))
        LegBoard.Offline -> Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Note(OFFLINE_TEXT, Modifier.testTag("duel.legs.offline.$legIndex"))
            SecondaryButton(text = "Retry", onClick = onRetry, modifier = Modifier.testTag("duel.legs.retry.$legIndex"))
        }
        else -> Unit
    }
}

@Composable
private fun Note(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
