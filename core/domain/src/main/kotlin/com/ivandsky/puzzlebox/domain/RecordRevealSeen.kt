package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant

/**
 * Records on this device that the reveal of a duel was shown, stamped by [clock], so it opens at its summary from
 * then on. A reveal already seen keeps its first stamp ([MatchLocalRepository.markRevealSeen]).
 */
class RecordRevealSeen(private val local: MatchLocalRepository, private val clock: () -> Instant) {
    suspend operator fun invoke(code: ShareCode) = local.markRevealSeen(code, clock())
}
