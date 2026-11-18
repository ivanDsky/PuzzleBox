package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.repository.SharedFetchResult
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Create's online check (spec A17): one read of the public puzzle behind each exact custom entry, side by side and each
 * given [READ_LIMIT_MS]. A puzzle that was taken down, can't be read in this version or doesn't answer in time is
 * [Result.Unavailable] at its position; the first such entry wins. A daily entry has no public puzzle to lose, so it is
 * not read. With no connection the answer is [Result.Offline], never a claim that a puzzle is gone.
 */
class ValidateExactPuzzles(private val shared: SharedPuzzleRepository) {

    sealed interface Result {
        data object Available : Result
        data object Offline : Result

        /** [position] counts from 1, as the lineup's rows read. */
        data class Unavailable(val position: Int) : Result
    }

    suspend operator fun invoke(entries: List<LineupEntry>): Result = coroutineScope {
        val reads = entries.mapIndexedNotNull { index, entry ->
            val code = ((entry as? ExactPuzzle)?.ref as? LegRef.Custom)?.code ?: return@mapIndexedNotNull null
            async { withTimeoutOrNull(READ_LIMIT_MS) { shared.fetch(code) }.let { index + 1 to it } }
        }
        reads.awaitAll().firstNotNullOfOrNull { (position, fetched) ->
            when {
                fetched == SharedFetchResult.Offline -> Result.Offline
                fetched is SharedFetchResult.Found && !fetched.doc.unpublished -> null
                else -> Result.Unavailable(position)
            }
        } ?: Result.Available
    }

    private companion object {
        /** A hung read is an unreachable puzzle; the whole check takes this long at most, whatever the lineup holds. */
        const val READ_LIMIT_MS = 5_000L
    }
}
