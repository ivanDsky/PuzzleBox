package com.ivandsky.puzzlebox.create.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict

/**
 * The strip's Finish slot for the open [draft] and its [verdict] (E1): null while the tool is not
 * the game has no completer. A running completion keeps the tool pressable, so a tap cancels it.
 */
@Composable
internal fun rememberFinishSlot(finish: EditorViewModelFinish, draft: EditorDraft<*>, verdict: Verdict): FinishSlot? {
    val busy by finish.busy.collectAsStateWithLifecycle()
    val repeating by finish.repeating.collectAsStateWithLifecycle()
    val level by finish.level.collectAsStateWithLifecycle()
    if (!draft.canFinish) return null
    return FinishSlot(
        enabled = busy || repeating || finish.enabled(draft, verdict),
        busy = busy,
        level = level,
        onFinish = finish::press,
        onCancel = finish::cancel,
    )
}
