package com.ivandsky.puzzlebox.insights.stats

import com.ivandsky.puzzlebox.insights.domain.recap.SolveRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.api.ReplayKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** A record opens its replay when it has one and its day in History when it has not (A13). */
class StatsOpenSolveTest {

    private val zip = GameId("zip")
    private val day = LocalDate.of(2026, 11, 3)
    private val replays = mutableListOf<ReplayKey>()
    private val days = mutableListOf<Pair<GameId, LocalDate>>()

    private fun open(solve: SolveRef) = openSolve(solve, { replays += it }, { game, d -> days += game to d })

    @Test
    fun `a solve with a replay opens its first win's replay`() {
        open(SolveRef(zip, day, puzzleNumber = 212, hasReplay = true))

        assertEquals(listOf(ReplayKey.daily("zip", "2026-11-03", 212, latest = false)), replays)
        assertEquals(emptyList<Pair<GameId, LocalDate>>(), days)
    }

    @Test
    fun `an imported solve opens its day`() {
        open(SolveRef(zip, day, puzzleNumber = 212, hasReplay = false))

        assertEquals(emptyList<ReplayKey>(), replays)
        assertEquals(listOf(zip to day), days)
    }
}
