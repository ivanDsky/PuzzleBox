package com.ivandsky.puzzlebox.resultui.board

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A way for something outside a board to ask for the viewer's own row on it to be scrolled into view: the same jump
 * the docked row makes when tapped. The caller provides one through [LocalSelfRowJump] around the board; the board's
 * [DockedSelfRow] attaches its row to it, and [request] is the ask.
 */
@Stable
class SelfRowJump {
    private var row: BringIntoViewRequester? = null

    /** Scrolls the viewer's row into view; false when the board under this has no such row (nothing moves). */
    suspend fun request(): Boolean {
        val requester = row ?: return false
        requester.bringIntoView()
        return true
    }

    internal fun attach(requester: BringIntoViewRequester) {
        row = requester
    }

    internal fun detach(requester: BringIntoViewRequester) {
        if (row === requester) row = null
    }
}

/** The jump for the board being composed; null when nothing outside it asks to jump to the viewer's row. */
val LocalSelfRowJump = staticCompositionLocalOf<SelfRowJump?> { null }
