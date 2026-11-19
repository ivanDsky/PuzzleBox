package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** [highlighted] is the row to fill (null is the "all" row); it moves at once on a tap, whatever the caller does with [onSelect]. */
@Stable
internal class SingleFilterPick<T>(val highlighted: T?, val onAll: () -> Unit, val onOption: (T) -> Unit)

/**
 * A tap reports [onSelect] at once and starts [onDone] 150 ms ([MotionTokens.DurationExit]) later; taps are ignored until [onDone]
 * has returned.
 */
@Composable
internal fun <T> rememberSingleFilterPick(selected: T?, onSelect: (T?) -> Unit, onDone: suspend () -> Unit): SingleFilterPick<T> {
    val scope = rememberCoroutineScope()
    val report by rememberUpdatedState(onSelect)
    val done by rememberUpdatedState(onDone)
    var tapped by remember { mutableStateOf<Tap<T>?>(null) }

    val pick = { value: T? ->
        if (tapped == null) {
            tapped = Tap(value)
            report(value)
            scope.launch {
                delay(MotionTokens.DurationExit.toLong())
                done()
                tapped = null
            }
        }
    }
    val tap = tapped
    return SingleFilterPick(
        highlighted = if (tap != null) tap.value else selected,
        onAll = { pick(null) },
        onOption = { pick(it) },
    )
}

/** A tap's value; a holder so the "all" row's null is told from no tap. */
private class Tap<T>(val value: T?)
