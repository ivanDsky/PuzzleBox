package com.ivandsky.puzzlebox.domain.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInAccount
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.WEEK_DAYS
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.PuzzleKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The Profile summary of the linked account, built from what the phone already holds: the stored owner row, the
 * stored boards, and the results imported from LinkedIn. Observing never sends a call.
 */
class ObserveLinkedInAccount(
    private val account: LinkedInAccount,
    private val completions: CompletionRepository,
    private val puzzleDay: GetPuzzleDay,
    private val registry: GameRegistry,
) {

    operator fun invoke(): Flow<LinkedInAccountView> =
        combine(account.observe(), completions.observeImports()) { stored, imports ->
            // A day is one game on one date; a play-again of the same day, or a re-import, is still one day.
            val days = imports.mapNotNull { record ->
                val key = record.puzzleRef.key as? PuzzleKey.Daily ?: return@mapNotNull null
                if (registry[record.puzzleRef.gameId] == null) null else record.puzzleRef.gameId to key.date
            }.toSet()
            val today = puzzleDay()
            val weekStart = today.minusDays(WEEK_DAYS - 1L)
            val played = days.mapTo(mutableSetOf()) { it.first }
            LinkedInAccountView(
                name = stored.owner?.name,
                photoPath = stored.owner?.photoPath,
                connectionsSeen = stored.connectionsSeen,
                daysThisWeek = days.count { it.second in weekStart..today },
                daysTotal = days.size,
                games = registry.all.map { it.definition }.filter { it.id in played }.map { it.displayName },
                updatedAtMs = stored.lastFetchedAtMs,
            )
        }
}
