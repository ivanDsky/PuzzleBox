package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant

/**
 * Records on this device that the reveal of a duel was shown, stamped by [clock] and with the two scores it showed,
 * so it opens at its summary from then on and the Duels list can keep the finished duel without asking the server.
 * A reveal already seen keeps its first stamp and scores ([MatchLocalRepository.markRevealSeen]).
 */
class RecordRevealSeen(private val local: MatchLocalRepository, private val clock: () -> Instant) {
    suspend operator fun invoke(code: ShareCode, myScore: Int, theirScore: Int) =
        local.markRevealSeen(code, clock(), myScore, theirScore)
}
