package com.ivandsky.puzzlebox.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The game page's primary button: "Play" for a fresh day, "Keep going" for a started one. */
private val PlayButton = By.text(Pattern.compile("Play|Keep going"))

/** Every game's action bar carries Hint, and it composes with the board, not before it. */
private val BoardReady = By.text("Hint")

private val TutorialSkip = By.text("Skip")
private val Back = By.desc("Back")

/**
 * From the Games tab's Dailies list: the game's page. Never starts the puzzle. Two-pane keeps the
 * previously selected game's page beside the list, with its own Play button, so the page is only
 * the game's once its name shows a second time: on the card and as the page title.
 */
internal fun MacrobenchmarkScope.openGamePage(game: BenchmarkGame) {
    tap(By.text(game.card))
    if (isTwoPane()) poll("the ${game.card} page beside the list") { device.findObjects(By.text(game.card)).takeIf { it.size >= 2 } }
    waitFor(PlayButton)
}

/** From a game page: the board, ready to take input. */
internal fun MacrobenchmarkScope.startBoard() {
    tap(PlayButton)
    waitFor(BoardReady)
}

/**
 * From a board: the game page, then the Games tab's list. Single-pane stacks the page over the list,
 * so it takes a second Back; two-pane shows the page beside the list and has none. Back is tapped
 * only once the board is gone: until then the board's own Back is still the one on screen.
 */
internal fun MacrobenchmarkScope.leaveGame() {
    tap(Back)
    device.wait(Until.gone(BoardReady), WAIT_MS)
    waitFor(PlayButton)
    if (!isTwoPane()) tap(Back)
    waitFor(BenchmarkTab.Games.ready)
}

/** How long the board stays up with no tutorial before the tutorial counts as seen. */
private const val TUTORIAL_SETTLE_MS = 1_000L

/**
 * Plays the first-run tutorial out of the way: a game's first Play shows a tutorial with Skip, then
 * remembers it. Runs before every measured run, from any tab; a game already past its tutorial opens
 * straight to the board. The tutorial gate opens only after its seen flag is read, so the board can
 * show briefly before the tutorial covers it: the board alone counts only once it has stayed up for
 * [TUTORIAL_SETTLE_MS] with no Skip.
 */
internal fun MacrobenchmarkScope.skipTutorial(game: BenchmarkGame) {
    selectTab(BenchmarkTab.Games)
    openGamePage(game)
    tap(PlayButton)
    val skipped = poll("the ${game.card} board or its tutorial") {
        device.findObject(TutorialSkip)?.let { it.click(); true }
            ?: if (isShown(BoardReady)) false else null
    }
    if (!skipped) device.wait(Until.findObject(TutorialSkip), TUTORIAL_SETTLE_MS)?.click()
    waitFor(BoardReady)
    leaveGame()
    selectTab(BenchmarkTab.Today)
}
