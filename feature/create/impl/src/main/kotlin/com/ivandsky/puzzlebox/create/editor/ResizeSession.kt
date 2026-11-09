package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.DraftResize
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Size picker's resize behind its "Start a blank board?" confirm: [request] resizes the
 * open [board] at once through [apply], unless that would start a blank board over content
 * (an editor without [DraftResize], a board that isn't blank); then [pending] holds the size
 * until [confirm] applies it or [cancel] drops it. A [DraftResize] game (Zip) never asks: its
 * resize keeps content, and Undo brings back what a shrink dropped. While [locked] (published)
 * or for the open size nothing happens. Confined to the main thread, like the Studio's other holders.
 */
internal class ResizeSession(
    private val board: () -> EditorDraft<*>?,
    private val locked: () -> Boolean,
    private val apply: (VariantId) -> Unit,
) {

    private val asking = MutableStateFlow<VariantId?>(null)

    /** The size the dialog asks about; null while it is closed. */
    val pending: StateFlow<VariantId?> = asking.asStateFlow()

    fun request(variant: VariantId) {
        val open = board() ?: return
        if (locked() || open.variant == variant) return
        if (open.resizeDiscards()) asking.value = variant else apply(variant)
    }

    fun confirm() {
        val variant = asking.value ?: return
        asking.value = null
        apply(variant)
    }

    fun cancel() {
        asking.value = null
    }
}

/** The resize starts the editor's blank board ([EditorDraft.resize]'s fallback) over a board with content. */
private fun EditorDraft<*>.resizeDiscards(): Boolean = editor !is DraftResize<*> && !isBlank
