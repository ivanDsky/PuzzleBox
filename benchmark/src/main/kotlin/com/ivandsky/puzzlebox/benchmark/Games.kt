package com.ivandsky.puzzlebox.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import java.util.regex.Pattern

/** The game page's primary button: "Play" for a fresh day, "Keep going" for a started one. */
private val PlayButton = By.text(Pattern.compile("Play|Keep going"))

/** Every game's action bar carries Hint, and it composes with the board, not before it. */
private val BoardReady = By.text("Hint")

private val TutorialSkip = By.text("Skip")
private val Back = By.desc("Back")

/** From the Games tab's Dailies list: the game's page. Never starts the puzzle. */
internal fun MacrobenchmarkScope.openGamePage(game: BenchmarkGame) {
    tap(By.text(game.card))
    waitFor(PlayButton)
}

/** From a game page: the board, ready to take input. */
internal fun MacrobenchmarkScope.startBoard() {
    tap(PlayButton)
    waitFor(BoardReady)
}

/** From a board: the game page, then the Games tab's list. */
internal fun MacrobenchmarkScope.leaveGame() {
    tap(Back)
    waitFor(PlayButton)
    tap(Back)
    waitFor(BenchmarkTab.Games.ready)
}

/**
 * Plays the first-run tutorial out of the way: a game's first Play shows a tutorial with Skip, then
 * remembers it. Run once per game before measuring, from the Games tab; a game already past its
 * tutorial opens straight to the board.
 */
internal fun MacrobenchmarkScope.skipTutorial(game: BenchmarkGame) {
    selectTab(BenchmarkTab.Games)
    openGamePage(game)
    tap(PlayButton)
    poll("the ${game.card} board or its tutorial") {
        when {
            isShown(BoardReady) -> true
            else -> device.findObject(TutorialSkip)?.also { it.click() }?.let { true }
        }
    }
    waitFor(BoardReady)
    leaveGame()
    selectTab(BenchmarkTab.Today)
}
