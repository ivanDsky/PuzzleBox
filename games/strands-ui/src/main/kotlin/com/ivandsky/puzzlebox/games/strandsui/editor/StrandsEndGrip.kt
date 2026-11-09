package com.ivandsky.puzzlebox.games.strandsui.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strandsui.TraceStep

/**
 * When a touch is a [StrandsEndDrag]. A touch going down on the first or last cell of the
 * selected path arms that end; the touch's first join (the board's drag beginning) starts the
 * drag there, any other step disarms. From then on the drag owns the touch: its trace steps are
 * swallowed, the finger moves the end, the lift ([drop]) ends it. A tap stays a tap: it never
 * joins.
 */
internal class StrandsEndGrip {
    private var armed: Int? = null

    /** The running drag, null while none runs. */
    var drag: StrandsEndDrag? = null
        private set

    /**
     * Takes [step] of the board's touch on [draft], [selected] the selected path: the live trace
     * when the step is the end drag's (it starts one, or one runs), null when the step is not.
     */
    fun take(step: TraceStep, draft: EditorDraft<StrandsDraft>, selected: Int?): List<Int>? {
        drag?.let { return it.trace }
        val end = armed
        armed = null
        val cells = (step as? TraceStep.Updated)?.trace?.path?.map(::cellOf) ?: return null
        if (end != null && selected != null && cells.size == 2 && cells[0] == end) {
            return StrandsEndDrag(selected, draft, end).also { drag = it }.trace
        }
        val path = selected?.let { draft.state.draft.paths.getOrNull(it) }?.cells
        val lone = cells.singleOrNull()
        if (path != null && (lone == path.first() || lone == path.last())) armed = lone
        return null
    }

    /** Ends the drag, or disarms: the lift, or a new draft adopted. */
    fun drop() {
        armed = null
        drag = null
    }
}
