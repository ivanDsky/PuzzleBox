package com.ivandsky.puzzlebox.compete.create.lineup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private val HANDLE_SIZE = 48.dp
private val DOT_RADIUS = 2.dp
private val DOT_PITCH = 7.dp

/** What a row's handle reports while it is dragged: the finger went down and moved, by [onDelta] pixels vertically, and let go. */
internal class RowDrag(val onStart: () -> Unit, val onDelta: (Float) -> Unit, val onEnd: () -> Unit)

/** The grip of a row, a 48 dp target: pressing it and moving lifts the row, with no long press, since the grip does nothing else. */
@Composable
internal fun LineupDragHandle(drag: RowDrag, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(drag)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .size(HANDLE_SIZE)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { current.onStart() },
                    onDrag = { change, amount ->
                        change.consume()
                        current.onDelta(amount.y)
                    },
                    onDragEnd = { current.onEnd() },
                    onDragCancel = { current.onEnd() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(DOT_PITCH * 2, DOT_PITCH * 3)) {
            val radius = DOT_RADIUS.toPx()
            val pitch = DOT_PITCH.toPx()
            for (column in 0..1) for (line in 0..2) {
                drawCircle(color, radius, Offset(pitch * (column + 0.5f), pitch * (line + 0.5f)))
            }
        }
    }
}
