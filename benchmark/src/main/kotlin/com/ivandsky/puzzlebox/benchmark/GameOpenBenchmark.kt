package com.ivandsky.puzzlebox.benchmark

import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Open each game from the Games tab and back: card, game page, Play, board ready, back to the list.
 * Each run starts with the game opened once (its puzzle stored, its tutorial seen), so the measured
 * open is the stored-puzzle open a returning player sees. No puzzle is ever finished, so a day's
 * session stays "in progress" and the next run opens it with Keep going.
 */
@RunWith(AndroidJUnit4::class)
class GameOpenBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun zip() = open(BenchmarkGame.Zip)

    @Test
    fun tango() = open(BenchmarkGame.Tango)

    @Test
    fun queens() = open(BenchmarkGame.Queens)

    @Test
    fun strands() = open(BenchmarkGame.Strands)

    @Test
    fun miniSudoku() = open(BenchmarkGame.MiniSudoku)

    private fun open(game: BenchmarkGame) = rule.journey(
        data = DataState.Opened(game),
        setup = { selectTab(BenchmarkTab.Games) },
    ) {
        openGamePage(game)
        startBoard()
        leaveGame()
        selectTab(BenchmarkTab.Today)
    }
}
