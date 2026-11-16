package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.COMMUNITY_WINDOW_MS
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.visibility.HiddenPuzzles

/**
 * New today (C1): the repository's community list without the player's own puzzles, the ones
 * they reported and anything created before [COMMUNITY_WINDOW_MS] ago on this device's [clock].
 * The server already limits the window by the same clock; this applies it again so a list
 * that stayed on screen drops what has aged out. The repository's newest-first order is kept.
 * Offline and refused results pass through unchanged.
 */
class ListCommunityPuzzles(
    private val shared: SharedPuzzleRepository,
    private val hidden: HiddenPuzzles,
    private val identity: IdentityService,
    private val clock: () -> Long,
) {
    suspend operator fun invoke(filter: CommunityFilter = CommunityFilter()): CommunityListResult {
        val result = shared.listPublic(filter)
        if (result !is CommunityListResult.Listed) return result
        val me = identity.current.value?.uid
        val hiddenCodes = hidden.codes()
        val oldest = clock() - COMMUNITY_WINDOW_MS
        return CommunityListResult.Listed(
            result.puzzles.filter { it.authorUid != me && it.code !in hiddenCodes && it.createdAtMs >= oldest },
        )
    }
}
