package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoMove
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import java.time.Instant

/**
 * The legacy counted hint (spec hints bullet, Zip parity): AUTO-PLACES the forced move as an
 * ordinary, undoable placement, highlights its cell and captions its WHY until the next input.
 * Undo refunds nothing. Extracted from [TangoPlayController] to keep it under its size limit;
 * deleted with the legacy hint path (38b).
 */
internal class TangoLegacyHint {
    /** The cell the last hint auto-placed (§7 HintReveal highlight). */
    var cell: Int? by mutableStateOf(null)
        private set

    /** The last hint's WHY (§6 hint caption — the reveal names the deduction class). */
    var why: TangoHint.Why? by mutableStateOf(null)
        private set

    /**
     * Resolves one hint for [session]: hands the next session to [commit] (which clears this
     * reveal, as every commit does), THEN records the highlighted cell and reason — a hint
     * highlights the cell its own commit just cleared. A [TangoHint.Stuck] answer costs nothing (I7).
     */
    fun resolve(session: TangoSession, now: Instant, commit: (TangoSession) -> Unit) {
        val state = TangoState(session.puzzle, session.grid())
        when (val resolved = TangoRules.hint(state)) {
            is TangoHint.Forced -> {
                val move = TangoMove(resolved.idx, resolved.value)
                if (TangoRules.validate(move, state) is TangoVerdict.Valid) {
                    commit(session.withMove(move, now).withHintApplied(resolved))
                } else {
                    commit(session.withHintApplied(resolved))
                }
                cell = resolved.idx
                why = resolved.why
            }

            TangoHint.Stuck -> commit(session.withHintApplied(resolved))
        }
    }

    /** The hint's WHY sentence (§6), shown by the shared chrome until the next input. */
    fun caption(session: TangoSession): String? {
        val why = why ?: return null
        return TangoSemantics.hintPhrase(
            TangoRendererModel.of(TangoState(session.puzzle, session.grid()), hint = null),
            why,
        )
    }

    /** The hint riding the renderer model for the cell highlight; its WHY is shown by [caption], not the board. */
    fun forced(session: TangoSession): TangoHint.Forced? =
        cell?.let { idx -> why?.let { why -> session.grid()[idx]?.let { value -> TangoHint.Forced(idx, value, why) } } }

    fun clear() {
        cell = null
        why = null
    }
}
