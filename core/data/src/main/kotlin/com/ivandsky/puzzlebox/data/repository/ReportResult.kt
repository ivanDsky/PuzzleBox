package com.ivandsky.puzzlebox.data.repository

/** The outcome of [SharedPuzzleRepository.report]. */
sealed interface ReportResult {
    data object Reported : ReportResult

    /**
     * The server refused the report and recorded nothing new. It answers a second report by the
     * same player, the author's report of their own puzzle and a code with no puzzle the same way,
     * so they share this result.
     */
    data object AlreadyReported : ReportResult

    /** Nobody is signed in; the server was not asked. */
    data object SignedOut : ReportResult
    data object Offline : ReportResult
    data class Rejected(val message: String?) : ReportResult
}
