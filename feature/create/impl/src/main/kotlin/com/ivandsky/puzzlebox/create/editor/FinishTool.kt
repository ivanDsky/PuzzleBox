package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

private const val BusyRingProgress = 0.66f

/**
 * Finish for me on the strip: the dice, whose tap opens or closes the level picker ([picking],
 * [onPicking]); while [finish] is busy, a progress ring that a tap cancels (a determinate ring
 * under reduced motion).
 */
@Composable
internal fun FinishTool(finish: FinishSlot, picking: Boolean, onPicking: (Boolean) -> Unit, modifier: Modifier) {
    val reduced = rememberReducedMotion()
    Tool(
        "Finish",
        enabled = finish.enabled || finish.busy,
        active = picking,
        onClick = { if (finish.busy) finish.onCancel() else onPicking(!picking) },
        tag = "create.tool.finish",
        modifier = modifier,
        stateDescription = if (finish.busy) "Finishing, tap to cancel" else null,
    ) { ink ->
        if (finish.busy) {
            val ring = Modifier.size(ToolIconSize)
            if (reduced) {
                CircularProgressIndicator(progress = { BusyRingProgress }, modifier = ring, color = ink, strokeWidth = 2.dp)
            } else {
                CircularProgressIndicator(modifier = ring, color = ink, strokeWidth = 2.dp)
            }
        } else {
            Icon(PuzzleIcons.Dice, contentDescription = null, tint = ink, modifier = Modifier.size(ToolIconSize))
        }
    }
}
