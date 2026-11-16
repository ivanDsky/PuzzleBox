package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc

/**
 * The most puzzles one community list returns.
 * backend/firebase/firestore.rules refuses a list that asks for more.
 */
const val COMMUNITY_LIST_LIMIT = 30

/** How far back the community list reaches, measured on the device clock. */
const val COMMUNITY_WINDOW_MS = 24L * 60 * 60 * 1000

/** The outcome of [SharedPuzzleRepository.listPublic]. */
sealed interface CommunityListResult {
    /** Newest first; empty when nothing public was published in the window. */
    data class Listed(val puzzles: List<SharedPuzzleDoc>) : CommunityListResult

    /** The server was not reached; a retry can succeed. */
    data object Offline : CommunityListResult

    /** The server was reached and refused the read. */
    data object Refused : CommunityListResult
}
