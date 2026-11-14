package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.replay.DailyReplayRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.SolveReplay

/** Finds another player's published replay of a daily. Answers only [SolveReplayRef.Player]; every other ref is null before any read. */
class LoadPlayerReplay(
    private val replays: DailyReplayRepository,
    private val daily: DailyRepository,
) {
    /** Null = no replay, for any cause (A13): absent, denied by the owner's tier, offline, a day the cache dropped. */
    suspend operator fun invoke(ref: SolveReplayRef): ReplaySource? {
        if (ref !is SolveReplayRef.Player) return null
        val bytes = replays.fetch(ref.board, ref.uid)?.let(SolveReplay::of) ?: return null
        val puzzle = daily.cachedPuzzle(GameId(ref.board.gameId), ref.board.day) ?: return null
        return ReplaySource(puzzle, bytes)
    }
}
