package com.ivandsky.puzzlebox.train.practice

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** How long a finished board stays on screen, with its Done band, before the next board (or the chapter's result) opens. */
internal const val BoardEndHoldMillis = 1_500L

/** How often the hold's remaining time is republished; also how coarsely time spent in the background is lost. */
private const val HoldTickMillis = 100L

/**
 * A finished board's hold: [remainingMs] of [BoardEndHoldMillis] left, counted down in ticks. With [waitsForTap] there
 * is no countdown at all (a screen reader is on): the board stays until the player taps the Done band's button.
 */
internal data class BoardHold(val remainingMs: Long, val waitsForTap: Boolean)

/**
 * Times the board-end hold. [await] runs it: it returns when the countdown ends or [skip] is called, and with a screen
 * reader on ([screenReader], asked once per hold) only on [skip]. The countdown stands still while the app is in the
 * background ([setForeground]), so a player who looks away does not lose the board's end frame.
 */
internal class BoardEndHold(private val screenReader: ScreenReaderCheck) {

    private val mutableHold = MutableStateFlow<BoardHold?>(null)
    private val foreground = MutableStateFlow(true)
    private var skipped: CompletableDeferred<Unit>? = null

    /** The hold now running, or null while no board has ended. */
    val hold: StateFlow<BoardHold?> = mutableHold.asStateFlow()

    fun setForeground(foreground: Boolean) {
        this.foreground.value = foreground
    }

    /** Ends the hold now; nothing when none is running. */
    fun skip() {
        skipped?.complete(Unit)
    }

    /** Forgets the hold: its board is gone. */
    fun clear() {
        mutableHold.value = null
        skipped = null
    }

    suspend fun await() = coroutineScope {
        val taps = screenReader.on()
        val done = CompletableDeferred<Unit>().also { skipped = it }
        mutableHold.value = BoardHold(BoardEndHoldMillis, taps)
        if (!taps) {
            launch {
                var left = BoardEndHoldMillis
                while (left > 0) {
                    foreground.first { it }
                    delay(HoldTickMillis)
                    // A tick that ended in the background is not spent.
                    if (foreground.value) left -= HoldTickMillis
                    mutableHold.value = BoardHold(left, false)
                }
                done.complete(Unit)
            }
        }
        done.await()
        coroutineContext.cancelChildren()
    }
}
