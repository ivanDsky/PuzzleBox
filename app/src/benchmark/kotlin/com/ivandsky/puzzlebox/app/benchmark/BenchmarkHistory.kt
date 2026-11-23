package com.ivandsky.puzzlebox.app.benchmark

import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import java.time.YearMonth

/** How many months the seeded history spans: more than two calendar years. */
internal const val SEEDED_MONTHS = 26

/**
 * The newest months left unseeded: the current month and the three before it. Today's puzzle must stay
 * unsolved (a solved page shows "See result", not Play) and the archive journey walks three months back
 * from today, so those days keep the state the other journeys expect.
 */
internal const val UNSEEDED_RECENT_MONTHS = 4

/**
 * The solved history the Insights hub journeys run on: every listed game solves a few days of each of
 * [SEEDED_MONTHS] months, ending [UNSEEDED_RECENT_MONTHS] months before today's, so the hub lists two past
 * years and every month, each with a mosaic of several games. Deterministic from `today`'s month, so a second
 * seeding finds the same refs and skips them.
 */
internal object BenchmarkHistory {

    fun records(registry: GameRegistry, today: LocalDate): List<CompletionRecord> {
        val thisMonth = YearMonth.from(today)
        return (0 until SEEDED_MONTHS).flatMap { back ->
            val month = thisMonth.minusMonths((back + UNSEEDED_RECENT_MONTHS).toLong())
            registry.all.flatMapIndexed { gameIndex, plugin ->
                val days = 1 + (back + gameIndex) % DAYS_PER_GAME
                (0 until days).map { n ->
                    val day = month.atDay(1 + (n * STEP_DAYS + gameIndex * 2).coerceAtMost(month.lengthOfMonth() - 1))
                    CompletionRecord(
                        puzzleRef = plugin.refOf(PuzzleKey.Daily(day, puzzleNumber = day.toEpochDay().toInt())),
                        time = BASE_TIME_MS + n * TIME_STEP_MS,
                        hints = 0,
                        stats = RecordStats(schemaVersion = 1, blob = "{}"),
                    )
                }
            }
        }
    }

    private const val DAYS_PER_GAME = 4
    private const val STEP_DAYS = 6
    private const val BASE_TIME_MS = 45_000L
    private const val TIME_STEP_MS = 7_000L
}
