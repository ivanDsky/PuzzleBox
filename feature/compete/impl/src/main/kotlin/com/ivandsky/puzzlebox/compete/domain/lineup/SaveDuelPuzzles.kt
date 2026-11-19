package com.ivandsky.puzzlebox.compete.domain.lineup

import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * When a friend accepts a duel, keeps every exact (custom-leg) puzzle of it with their friends' puzzles (spec A16), so a
 * puzzle its author takes down later still plays here. [open] is the same open that stores the row ([LoadSharedPuzzle]);
 * the reads run side by side, each given [READ_LIMIT_MS], and one that fails, is taken down or does not answer is skipped:
 * the duel page explains that leg. Answers how many puzzles are now on this device.
 */
class SaveDuelPuzzles(private val open: suspend (ShareCode) -> OpenOutcome) {

    suspend operator fun invoke(codes: Collection<ShareCode>): Int = coroutineScope {
        codes.toSet()
            .map { code -> async { withTimeoutOrNull(READ_LIMIT_MS) { open(code) } } }
            .awaitAll()
            .count { it is OpenOutcome.Opened }
    }

    private companion object {
        /** A hung read must not hold the accept's follow-up; the whole save takes this long at most. */
        const val READ_LIMIT_MS = 5_000L
    }
}
