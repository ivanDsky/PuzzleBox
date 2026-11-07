package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * Refreshes the author's Published rows' solve counts (spec A20). Each count is one `count()`
 * read billed per thousand entries, so a row is asked again only once its cached count is
 * [REFRESH_EVERY] old; the stale rows are asked together. A count that can't be read leaves
 * the cached one and its instant, so the next showing tries again.
 */
class SyncSolvesCounts(
    private val published: PublishedPuzzleRepository,
    private val boards: CustomBoardRepository,
    private val clock: () -> Instant,
) {
    /** Every row whose solvesCountAt is null or older than REFRESH_EVERY: solvesCount(code) → updateSolves; null (offline) keeps the cache (A20). */
    suspend operator fun invoke() {
        val now = clock()
        val stale = published.observeAll().first().filter { it.isStaleAt(now) }
        coroutineScope {
            stale.map { row ->
                async { boards.solvesCount(row.code)?.let { count -> published.updateSolves(row.code, count, now) } }
            }.awaitAll()
        }
    }

    private fun PublishedPuzzle.isStaleAt(now: Instant): Boolean =
        solvesCountAt?.let { !it.plus(REFRESH_EVERY.toJavaDuration()).isAfter(now) } ?: true

    companion object {
        val REFRESH_EVERY: Duration = 10.minutes
    }
}
