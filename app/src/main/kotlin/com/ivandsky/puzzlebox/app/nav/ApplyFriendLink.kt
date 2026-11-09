package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.PlayKey

/**
 * Puts a tapped [link] on the back stack, through the reducer's own verbs: it selects Play
 * without popping it, so a daily in play beneath stays alive and back returns to it.
 *
 * - A code pushes its card once; when that card is already on Play's stack (the friend is
 *   mid-puzzle) selecting Play is the whole move, since a key appears once per stack.
 * - A match pushes its duel once the same way; with [matchesEnabled] off it changes nothing.
 * - Invalid text returns Play to its root, the library that hosts the code sheet; a board
 *   popped on the way saves like any other.
 */
internal fun Navigator.applyFriendLink(link: FriendLink, matchesEnabled: Boolean) {
    if (link is FriendLink.Match && !matchesEnabled) return
    if (state.tab != PlayKey) selectTab(PlayKey)
    when (link) {
        is FriendLink.Code -> {
            val card = FriendPuzzleKey(link.code.value)
            if (card !in state.currentStack) pushOnce(card)
        }
        is FriendLink.Match -> {
            val duel = DuelKey(link.code.value)
            if (duel !in state.currentStack) pushOnce(duel)
        }
        // Reselecting the selected tab pops it to its root.
        is FriendLink.Invalid -> if (state.currentStack.size > 1) selectTab(PlayKey)
    }
}
