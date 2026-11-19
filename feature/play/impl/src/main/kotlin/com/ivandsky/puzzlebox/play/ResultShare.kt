package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.model.DailyLink
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.resultui.replay.ReplaySectionState
import com.ivandsky.puzzlebox.resultui.resultShareText
import com.ivandsky.puzzlebox.resultui.solveStrip

/**
 * A daily result's Share text: the header, the solve strip when the replay is already read and the game has one, and
 * the link to [slot]'s day unless the puzzle is practice; Share never waits on the replay, so a replay that is
 * hidden, unavailable or still loading gives no strip.
 */
internal class ResultShare(private val slot: PlaySlot.Daily) {
    fun textOf(state: ResultUiState, timeMs: Long, hints: Int): String {
        val timeline = (state.replay as? ReplaySectionState.Ready)?.timeline?.takeIf { slot.gameId !in WithoutStrip }
        return resultShareText(
            game = state.game,
            number = state.puzzleNumber,
            timeMs = timeMs,
            hints = hints,
            link = DailyLink.of(slot.gameId, slot.day).takeUnless { state.practice },
            strip = timeline?.let { solveStrip(it.moveTimesMs, it.moveKindsAt, it.endMs) },
        )
    }

    private companion object {
        // Leaving Strands out is a product choice (spec A4), not missing data: its replay marks hints too.
        val WithoutStrip = setOf(GameId("strands"))
    }
}
