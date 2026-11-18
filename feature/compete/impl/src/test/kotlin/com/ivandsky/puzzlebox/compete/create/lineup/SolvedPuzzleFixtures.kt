package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.PickablePuzzle
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

internal fun solvedDaily(game: GameId, day: LocalDate, number: Int, title: String): PickablePuzzle =
    PickablePuzzle(ExactPuzzle(LegRef.Daily(game, day, number), null, title, null, PuzzleSource.PastDay), isNew = false, played = true, meta = "Nov ${day.dayOfMonth}, ${day.year}", code = null)

internal val ZIP_DAILY = solvedDaily(ZIP, LocalDate.of(2026, 11, 17), 12, "Zip #12")
internal val QUEENS_DAILY = solvedDaily(QUEENS, LocalDate.of(2026, 11, 16), 9, "Queens #9")
internal val FRIEND_TANGO = pickable(TANGO, "K7Q2MX", "Crown Tango", "Bo", PuzzleSource.Friend, played = true)

/** What the Past you add sheet reads, newest first solve first: two solved dailies and one friend's puzzle. */
internal val SOLVED_ROWS = listOf(ZIP_DAILY, FRIEND_TANGO, QUEENS_DAILY)
