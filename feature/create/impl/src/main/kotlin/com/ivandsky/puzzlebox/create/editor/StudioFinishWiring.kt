package com.ivandsky.puzzlebox.create.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict

/**
 * The strip's Finish slot for the open [draft] and its [verdict] (E1): null while the tool is not
 * offered (FinishForMe off) or the game has no completer, so the strip, the screens and the routes
 * never see the flag. A running completion keeps the tool pressable, so a second tap cancels it.
 */
@Composable
internal fun rememberFinishSlot(finish: EditorViewModelFinish, draft: EditorDraft<*>, verdict: Verdict): FinishSlot? {
    val offered by finish.offered.collectAsStateWithLifecycle()
    val busy by finish.busy.collectAsStateWithLifecycle()
    val repeating by finish.repeating.collectAsStateWithLifecycle()
    if (!offered || !draft.canFinish) return null
    return FinishSlot(
        enabled = busy || repeating || finish.enabled(draft, verdict),
        busy = busy,
        onPress = finish::press,
    )
}
