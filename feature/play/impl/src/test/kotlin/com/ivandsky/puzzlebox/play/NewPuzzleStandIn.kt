package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.model.GameId

/** The app's New puzzle host, as a stand-in: an open call that shows a "New puzzle sheet" text, and the clearance it was told. */
internal class NewPuzzleStandIn {
    /** The clearance the route told the host; null until the host composes. */
    var clearance: Dp? = null

    /** The game of each create call the content made, in order; null for a New puzzle sheet call. */
    val calls = mutableListOf<GameId?>()

    val slot = CustomSlot(
        yours = previewYoursSlot(count = 0).yours,
        yoursSummary = previewYoursSlot(count = 0).yoursSummary,
        newPuzzle = { clearance, content ->
            this.clearance = clearance
            var open by remember { mutableStateOf(false) }
            Box(Modifier) {
                content { game ->
                    calls += game
                    open = true
                }
                if (open) Text("New puzzle sheet", Modifier.testTag("new.sheet"))
            }
        },
    )
}
