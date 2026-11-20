package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.domain.ResultRank
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A [ReadWidgetScoreboard] over canned reads: no best, no records, signed out, on [day]. */
fun testScoreboard(
    best: suspend (GameId) -> BestScore? = { null },
    records: suspend () -> List<CompletionRecord> = { emptyList() },
    rank: (GameId, LocalDate) -> Flow<ResultRank> = { _, _ -> flowOf(ResultRank.SignedOut) },
    day: LocalDate = LocalDate.of(2026, 11, 20),
    nextRollover: () -> Instant = { Instant.parse("2026-11-21T08:00:00Z") },
) = ReadWidgetScoreboard(best, records, rank, puzzleDay = { day }, rankOrder = { RankOrder.TimeThenHints }, nextRollover = nextRollover)
