package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.Instant
import java.time.LocalDate

/** Zip's first solves, one per past day, as the pool a self duel draws from; their times are past you's. */
internal const val SOLVED_ZIPS = 3
internal const val FIRST_SOLVE_MS = 90_000L

/**
 * Three past Zip dailies solved once each (first wins, as the record store stamps them), with their puzzles cached,
 * so a Quick self duel can draw three legs and play every one of them offline.
 */
internal fun E2eKoinGraph.seedSolvedZips(today: LocalDate): List<CompletionRecord> {
    val days = (1..SOLVED_ZIPS).map { today.minusDays(it.toLong()) }
    daily.setHistory(ZipDefinition.id, days.map { ZipPlayFixture.entry(it, numberOf(it)) })
    days.forEach { daily.setCached(ZipDefinition.id, it, ZipPlayFixture.envelope()) }
    daily.setPuzzleNumbers(ZipDefinition.id) { day -> numberOf(day).takeIf { day in days } }
    val records = days.map { day ->
        CompletionRecord(
            puzzleRef = ZipPuzzleRef(PuzzleKey.Daily(day, numberOf(day))),
            time = FIRST_SOLVE_MS,
            hints = 0,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            firstCompletedAt = Instant.EPOCH,
            isRecord = true,
        )
    }
    this.records.setRecords(records)
    return records
}

private fun numberOf(day: LocalDate): Int = day.toEpochDay().toInt() - NUMBER_OFFSET

private const val NUMBER_OFFSET = 20_000
