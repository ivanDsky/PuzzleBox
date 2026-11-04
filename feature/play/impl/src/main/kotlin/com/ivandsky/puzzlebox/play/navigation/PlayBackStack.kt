package com.ivandsky.puzzlebox.play.navigation

import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey

// Ticket 23 nav contract — solving: everything above Home unwinds and the result takes
// its place, so the system back (a plain NavDisplay pop) lands on Home — the same
// surface the flow entered from (senior-UX wayfinding: back never surprises). The Play
// tab root sits directly on the stack with no Home beneath it; the unwind supplies one
// so the pop always has somewhere predictable to go.
internal fun Navigator.solveToResult(solved: ResultKey) {
    if (backStack.lastOrNull() == solved) return
    while (backStack.isNotEmpty() && backStack.lastOrNull() != HomeKey) backStack.removeLastOrNull()
    if (backStack.isEmpty()) backStack.add(HomeKey)
    backStack.add(solved)
}

// Ticket 23 nav contract — "Play again": the result pops and a fresh play entry takes
// its place. The result route has already reset the live play's session to a fresh play of
// the SAME puzzle (ResultRoute → ActivePlay.replay), which the play route keeps.
internal fun Navigator.playAgainFromResult() {
    backStack.removeLastOrNull()
    if (backStack.lastOrNull() != PlayKey) backStack.add(PlayKey)
}

// Back out of a play entry. Reached from Home ("Play today") the stack is [Home, Play] and a
// pop lands on Home; reached from the Play tab the entry is the sole stack element (tab
// selection clears), and popping it would leave NavDisplay an empty stack — it throws. Home
// is the only sensible landing for a tab root's back, same as the solve unwind above.
internal fun Navigator.leavePlay() {
    if (backStack.size > 1) {
        goBack()
    } else {
        navigateToTopLevel(HomeKey)
    }
}
