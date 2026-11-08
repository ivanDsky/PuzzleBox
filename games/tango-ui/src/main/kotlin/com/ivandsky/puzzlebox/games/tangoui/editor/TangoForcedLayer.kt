package com.ivandsky.puzzlebox.games.tangoui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoForcedCells
import kotlinx.coroutines.withContext

/** The dispatchers the forced-cells walk runs on; tests swap in their own. */
internal val LocalForcedDispatchers = staticCompositionLocalOf<DispatcherProvider> {
    DefaultDispatcherProvider()
}

/**
 * The cells the hint ladder forces from [board]'s givens while [enabled], else none. Each
 * committed edit restarts the walk off the main thread and clears the last answer first, so a
 * symbol never shows against givens it was not derived from.
 */
@Composable
internal fun rememberForcedCells(board: TangoDraft, enabled: Boolean): State<Map<Int, TangoCell>> {
    val dispatchers = LocalForcedDispatchers.current
    return produceState(emptyMap(), board, enabled) {
        value = emptyMap()
        if (enabled) value = withContext(dispatchers.default) { TangoForcedCells.of(board) }
    }
}

/**
 * Marks the forced cells: `tango.editor.forced` wraps one `tango.editor.cell.<idx>.forced`
 * per cell; the faint symbols themselves come from the play renderer's ghost drawing.
 */
@Composable
internal fun BoxScope.TangoForcedLayer(forced: Set<Int>) {
    if (forced.isEmpty()) return
    BoxWithConstraints(Modifier.matchParentSize().testTag("tango.editor.forced")) {
        val pitch = maxWidth / TangoDraft.N
        forced.forEach { idx ->
            Box(
                Modifier
                    .offset(pitch * (idx % TangoDraft.N), pitch * (idx / TangoDraft.N))
                    .size(pitch)
                    .testTag("tango.editor.cell.$idx.forced"),
            )
        }
    }
}
