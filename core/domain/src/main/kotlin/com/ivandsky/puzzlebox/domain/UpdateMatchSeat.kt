package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.ClaimResult
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.WriteResult
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.first

/**
 * The invite screen's buttons. Claim takes the open seat as the signed-in player and remembers the duel as mine,
 * named after the challenger. Cancel withdraws my unclaimed invite. Decline refuses a rematch invite addressed to
 * me on the server, then hides it; a link invite was never written to, so it is only hidden locally. An offline read decides nothing and is Offline. The player's
 * name is trimmed and cut to the rules' 64 characters, a blank one becoming the anonymous label.
 */
class UpdateMatchSeat(
    private val repo: MatchRepository,
    private val local: MatchLocalRepository,
    private val identity: IdentityService,
) {
    suspend operator fun invoke(code: ShareCode, action: SeatAction): SeatResult = when (action) {
        SeatAction.Claim -> claim(code)
        SeatAction.Cancel -> repo.cancel(code).toSeat { SeatResult.Done }
        SeatAction.Decline -> decline(code)
    }

    private suspend fun claim(code: ShareCode): SeatResult {
        val me = identity.current.value ?: return SeatResult.SignedOut
        val challengerName = (repo.observe(code).first() as? MatchFetch.Found)?.match?.challenger?.name
        return when (val result = repo.claim(code, me.asPlayer())) {
            ClaimResult.Claimed -> {
                local.remember(code, LocalRole.Participant, challengerName)
                SeatResult.Done
            }
            ClaimResult.Taken -> SeatResult.Taken
            ClaimResult.Cancelled -> SeatResult.Cancelled
            ClaimResult.Closed -> SeatResult.Closed
            ClaimResult.Offline -> SeatResult.Offline
            ClaimResult.SignedOut -> SeatResult.SignedOut
            is ClaimResult.Rejected -> SeatResult.Rejected(result.message)
        }
    }

    private suspend fun decline(code: ShareCode): SeatResult {
        val me = identity.current.value?.uid
        val fetch = repo.observe(code).first()
        if (fetch == MatchFetch.Offline) return SeatResult.Offline
        val invitedUid = (fetch as? MatchFetch.Found)?.match?.invitedUid
        if (me == null || invitedUid != me) {
            local.dismiss(code)
            return SeatResult.Done
        }
        return repo.decline(code).toSeat {
            local.dismiss(code)
            SeatResult.Done
        }
    }

    private inline fun WriteResult.toSeat(onDone: () -> SeatResult): SeatResult = when (this) {
        WriteResult.Done -> onDone()
        WriteResult.Offline -> SeatResult.Offline
        is WriteResult.Rejected -> SeatResult.Rejected(message)
    }

    private fun Identity.asPlayer() = Player(uid, displayName.trim().ifEmpty { ANONYMOUS_NAME }.take(MAX_NAME_LENGTH), photoUrl)

    private companion object {
        const val MAX_NAME_LENGTH = 64
        const val ANONYMOUS_NAME = "Player"
    }
}
