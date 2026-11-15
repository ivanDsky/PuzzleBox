package com.ivandsky.puzzlebox.benchmark

import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Open each game from the Games tab and back: card, game page, Play, board ready, back to the list.
 * The first-run tutorial is skipped before measuring; no puzzle is ever finished, so a day's
 * session stays "in progress" and the next run opens it with Keep going.
 */
@RunWith(AndroidJUnit4::class)
class GameOpenBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private var tutorialSkipped = false

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
        setup = {
            if (!tutorialSkipped) {
                skipTutorial(game)
                tutorialSkipped = true
            }
            selectTab(BenchmarkTab.Games)
        },
    ) {
        openGamePage(game)
        startBoard()
        leaveGame()
        selectTab(BenchmarkTab.Today)
    }
}
