package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository

/** Finds the replay bytes of a daily solve. Answers only Record and Latest of a daily puzzle; every other ref is null before any read. */
class LoadDailyReplay(completions: CompletionRepository, daily: DailyRepository) {
    /** Null = no replay.
     *  Inert until the replay source is filled: always null. */
    suspend operator fun invoke(ref: SolveReplayRef): ReplaySource? = null
}
