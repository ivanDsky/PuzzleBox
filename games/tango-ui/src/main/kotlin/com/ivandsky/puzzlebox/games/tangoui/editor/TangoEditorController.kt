package com.ivandsky.puzzlebox.games.tangoui.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.narrowTo
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraftCommand
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import com.ivandsky.puzzlebox.games.tango.editor.cycleCell
import com.ivandsky.puzzlebox.games.tango.editor.cycleMarker
import com.ivandsky.puzzlebox.gameui.GridEdge
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Tango editor screen's live draft. Each committed step goes out through [onEdit]; the
 * VM hands replacement drafts back through [adopt]. A press over a square or an edge shows
 * in [preview]; [release] commits its cycle step (empty, sun, moon; or none, `=`, `×`) as
 * one undoable command and [cancelPress] drops it. The verdict card's witness choice arrives
 * through [adoptOverlay] and the board draws it from [overlay]. [tapZones] outlines the edge
 * bands while on; [forcedView] shows the cells the hint ladder forces from the givens.
 */
class TangoEditorController(
    initial: EditorDraft<*>,
    private val onEdit: (EditorDraft<*>) -> Unit,
) : GameEditorController {

    private val current = MutableStateFlow(typed(initial))
    private val pressed = MutableStateFlow<TangoEditorTarget?>(null)
    private val chosen = MutableStateFlow(EditorOverlay())
    private val zones = MutableStateFlow(false)
    private val forced = MutableStateFlow(false)

    override val draft: StateFlow<EditorDraft<*>> = current.asStateFlow()

    override val overlay: StateFlow<EditorOverlay> = chosen.asStateFlow()

    /** What the finger is down on, or null; the board shows [commandFor] of it applied. */
    val preview: StateFlow<TangoEditorTarget?> = pressed.asStateFlow()

    /** Whether the board outlines where a tap lands. */
    val tapZones: StateFlow<Boolean> = zones.asStateFlow()

    fun toggleTapZones() {
        zones.value = !zones.value
    }

    /** Whether the board shows the cells the hint ladder forces; not persisted. */
    val forcedView: StateFlow<Boolean> = forced.asStateFlow()

    fun toggleForcedView() {
        forced.value = !forced.value
    }

    override fun adopt(draft: EditorDraft<*>) {
        pressed.value = null
        current.value = typed(draft)
    }

    override fun adoptOverlay(overlay: EditorOverlay) {
        chosen.value = overlay
    }

    fun press(target: TangoEditorTarget) {
        pressed.value = target
    }

    /** Commits the pressed target's next state as one step; no-op without a press. */
    fun release() {
        val target = pressed.value ?: return
        pressed.value = null
        val next = current.value.apply(commandFor(target))
        current.value = next
        onEdit(next)
    }

    fun cancelPress() {
        pressed.value = null
    }

    /** The current draft's width; the board reads it per gesture. */
    internal val n: Int get() = current.value.state.draft.n

    /** The cycle step a lift on [target] would commit against the current draft. */
    internal fun commandFor(target: TangoEditorTarget): TangoDraftCommand {
        val board = current.value.state.draft
        return when (target) {
            is TangoEditorTarget.Square -> board.cycleCell(target.cell)
            is TangoEditorTarget.Edge -> {
                val cell = target.edge.cell
                val side = when (target.edge.side) {
                    GridEdge.Side.Right -> TangoEdgeSide.RIGHT
                    GridEdge.Side.Bottom -> TangoEdgeSide.DOWN
                }
                board.cycleMarker(cell.row * board.n + cell.col, side)
            }
        }
    }

    private companion object {
        /** The registry hands this controller Tango drafts only; anything else is a wiring bug. */
        fun typed(draft: EditorDraft<*>): EditorDraft<TangoDraft> {
            return requireNotNull(draft.narrowTo(TangoEditor)) { "not a Tango draft: ${draft.gameId.value}" }
        }
    }
}
