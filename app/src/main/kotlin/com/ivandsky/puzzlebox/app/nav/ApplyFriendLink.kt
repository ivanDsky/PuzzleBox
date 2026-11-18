package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey

/**
 * Puts a tapped [link] on the back stack, through the reducer's own verbs: it selects the owner tab
 * without popping it, so a daily in play beneath stays alive and back returns to it. An open
 * Profile layer closes first, also over the owner itself, so the link's screen is never hidden
 * under it.
 *
 * Custom's list, its code sheet included, is Community's root (M10-1): a code and invalid text go there, a match is
 * a duel and stays on Play, as does a daily's day (the Games tab, where the archive opens it).
 *
 * - A code pushes its card once; when that card is already on the owner's stack (the friend is
 *   mid-puzzle) selecting the owner is the whole move, since a key appears once per stack.
 * - A match pushes its duel once the same way.
 * - A daily pushes its game's day (`GameKey(game, day)`) once the same way.
 * - Invalid text returns Community to its root, the list that hosts the code sheet; a board
 *   popped on the way saves like any other.
 */
internal fun Navigator.applyFriendLink(link: FriendLink) {
    val owner = if (link is FriendLink.Match || link is FriendLink.Daily) PlayKey else CommunityKey
    if (state.tab != owner || state.layer.isNotEmpty()) selectTab(owner)
    when (link) {
        is FriendLink.Code -> {
            val card = FriendPuzzleKey(link.code.value)
            if (card !in state.currentStack) pushOnce(card)
        }
        is FriendLink.Match -> {
            val duel = DuelKey(link.code.value)
            if (duel !in state.currentStack) pushOnce(duel)
        }
        is FriendLink.Daily -> {
            val day = GameKey(link.ref.gameId.value, link.ref.day.toString())
            if (day !in state.currentStack) pushOnce(day)
        }
        // Reselecting the selected tab pops it to its root.
        is FriendLink.Invalid -> if (state.currentStack.size > 1) selectTab(owner)
    }
}
