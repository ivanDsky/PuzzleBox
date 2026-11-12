package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository

/** Finds the replay bytes of one side of a duel leg. Answers only MyLeg and TheirLeg; every other ref is null before any read. */
class LoadLegReplay(
    matches: MatchRepository,
    outbox: MatchResultOutbox,
    identity: IdentityService,
    daily: DailyRepository,
    received: ReceivedPuzzleRepository,
    loadShared: LoadSharedPuzzle,
) {
    /** Null = no replay.
     *  Inert until the replay source is filled: always null. */
    suspend operator fun invoke(ref: SolveReplayRef): ReplaySource? = null
}
