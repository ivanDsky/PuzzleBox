package com.ivandsky.puzzlebox.data.match

import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.contract.MatchResultsContract
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import java.time.Instant

/**
 * Both halves of the match contract through [RoutingMatchRepository] over an empty self store: with no self duel
 * on the device, the app's repository is the remote one, case for case.
 */
class RoutingMatchRepositoryContractTest : MatchResultsContract() {

    private val remote = FakeMatchRepository()

    override val repository: MatchRepository = RoutingMatchRepository(FakeSelfMatchRepository(), remote)

    override fun signInAs(uid: String) {
        remote.signedInUid = uid
    }

    override fun signOut() {
        remote.signedInUid = null
    }

    override fun goOffline() {
        remote.online = false
    }

    override fun setNow(now: Instant) {
        remote.now = now
    }

    override fun occupyPuzzleCode(code: ShareCode) = remote.occupyPuzzleCode(code)
}
