package com.ivandsky.puzzlebox.domain

/** How a [SeatAction] ended; the claim failures keep the repository's names. */
sealed interface SeatResult {
    data object Done : SeatResult
    data object Taken : SeatResult
    data object Cancelled : SeatResult
    data object Closed : SeatResult
    data object Offline : SeatResult
    data object SignedOut : SeatResult
    data class Rejected(val message: String?) : SeatResult
}
