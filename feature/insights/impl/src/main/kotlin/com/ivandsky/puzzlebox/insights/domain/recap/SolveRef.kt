package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * A counted solve a fact came from, by the ids that open it: the [game] and puzzle [day], and the [puzzleNumber] a
 * replay is looked up by. [hasReplay] is false for an imported result, which carries a time and nothing else to watch (A5).
 */
internal data class SolveRef(
    val game: GameId,
    val day: LocalDate,
    val puzzleNumber: Int?,
    val hasReplay: Boolean,
) {
    companion object {
        fun of(day: GameHistoryDay): SolveRef = SolveRef(
            game = day.gameId,
            day = day.day,
            puzzleNumber = day.puzzleNumber,
            hasReplay = day.record?.source != CompletionSource.LinkedIn,
        )
    }
}
