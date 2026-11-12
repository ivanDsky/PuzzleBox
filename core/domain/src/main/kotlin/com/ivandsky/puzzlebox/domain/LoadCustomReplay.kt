package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository

/** Finds the replay bytes of a custom-puzzle solve. Answers only Record and Latest of a custom puzzle; every other ref is null before any read. */
class LoadCustomReplay(
    completions: CompletionRepository,
    received: ReceivedPuzzleRepository,
    loadShared: LoadSharedPuzzle,
) {
    /** Null = no replay.
     *  Inert until the replay source is filled: always null. */
    suspend operator fun invoke(ref: SolveReplayRef): ReplaySource? = null
}
