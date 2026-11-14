package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import kotlinx.coroutines.delay

/** How long a board on screen waits for the next day's before the hub says Loading; the game page's board waits as long. */
internal const val BOARD_HOLD_CAP_MS = 1_500L

/**
 * What the hub draws for [next] while [drawn] is on screen. A day or window pick leaves the board loading for a moment:
 * until it answers, [drawn]'s board stays (so its title, rows and note change in one step and the rows re-rank in
 * place instead of the list being torn down), and so does each place that is still loading. Only the same board is
 * held, so a swipe shows the new one at once; everything else is [next]'s. Returns [next] itself when nothing is held.
 */
internal fun heldBoard(drawn: RanksUiState?, next: RanksUiState): RanksUiState {
    if (drawn == null || drawn.position != next.position) return next
    val loading = next.board.state == DailyBoardState.Loading && drawn.board.state != DailyBoardState.Loading
    val places = drawn.places.heldOver(next.places)
    return if (!loading && places === next.places) next else next.copy(board = if (loading) drawn.board else next.board, places = places)
}

private fun List<BoardPlace>.heldOver(next: List<BoardPlace>): List<BoardPlace> {
    if (size != next.size) return next
    val waits = { i: Int -> next[i].standing == Standing.Pending && this[i].standing != Standing.Pending && this[i].board == next[i].board }
    return if (indices.none(waits)) next else next.mapIndexed { i, place -> if (waits(i)) this[i] else place }
}

/**
 * [state] as the hub draws it: [heldBoard] over what it drew last. A pick that has not answered within
 * [BOARD_HOLD_CAP_MS] lets go, and the hub shows [state] as it is (Loading) from then on.
 */
@Composable
internal fun rememberHeldBoard(state: RanksUiState): RanksUiState {
    val hold = remember { BoardHold() }
    val shown = hold.over(state)
    SideEffect { hold.drawn = shown }
    if (shown !== state) {
        LaunchedEffect(state.position, state.window, state.calendar.selectedDay) {
            delay(BOARD_HOLD_CAP_MS)
            hold.release()
        }
    }
    return shown
}

private class BoardHold {
    // Not snapshot state: what was drawn last is read by the next composition only, and must not cause one.
    var drawn: RanksUiState? = null
    private var releases by mutableIntStateOf(0)

    fun over(next: RanksUiState): RanksUiState {
        releases // read, so letting go recomposes
        return heldBoard(drawn, next)
    }

    fun release() {
        drawn = null
        releases++
    }
}
