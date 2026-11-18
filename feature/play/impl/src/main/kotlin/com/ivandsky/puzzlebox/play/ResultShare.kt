package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.gameui.replay.ReplaySectionState
import com.ivandsky.puzzlebox.gameui.resultShareText
import com.ivandsky.puzzlebox.gameui.solveStrip
import com.ivandsky.puzzlebox.model.DailyLink
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot

/**
 * A daily result's Share text. With [enabled] off it is the header alone. On, it adds the solve strip when the
 * replay is already read and the game has one, and the link to [slot]'s day unless the puzzle is practice; Share never
 * waits on the replay, so a replay that is hidden, unavailable or still loading gives no strip.
 */
internal class ResultShare(private val enabled: Boolean, private val slot: PlaySlot.Daily) {
    fun textOf(state: ResultUiState, timeMs: Long, hints: Int): String {
        if (!enabled) return resultShareText(state.game, state.puzzleNumber, timeMs, hints)
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
