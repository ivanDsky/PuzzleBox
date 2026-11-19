package com.ivandsky.puzzlebox.play.domain

import com.ivandsky.puzzlebox.data.repository.ReportResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.visibility.HiddenPuzzles
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * Reports a community puzzle and hides it on this device (C1, C2). The code is hidden on
 * [ReportResult.Reported] and on [ReportResult.AlreadyReported], which the server also answers
 * for the author's own puzzle and a code with no puzzle; hiding those is harmless. Signed out
 * ([ReportResult.SignedOut]) and every other failure hide nothing, so the player can try again.
 */
class ReportPuzzle(
    private val shared: SharedPuzzleRepository,
    private val hidden: HiddenPuzzles,
) {
    suspend operator fun invoke(code: ShareCode): ReportResult {
        val result = shared.report(code)
        if (result == ReportResult.Reported || result == ReportResult.AlreadyReported) hidden.add(code)
        return result
    }
}
