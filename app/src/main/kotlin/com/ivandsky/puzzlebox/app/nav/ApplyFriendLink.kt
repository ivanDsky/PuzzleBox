package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.PlayKey

/**
 * Puts a tapped [link] on the back stack, through the reducer's own verbs: it selects Play
 * without popping it, so a daily in play beneath stays alive and back returns to it. An open
 * Profile layer closes first, also over Play itself, so the link's screen is never hidden
 * under it.
 *
 * - A code pushes its card once; when that card is already on Play's stack (the friend is
 *   mid-puzzle) selecting Play is the whole move, since a key appears once per stack.
 * - A match pushes its duel once the same way.
 * - Invalid text returns Play to its root, the library that hosts the code sheet; a board
 *   popped on the way saves like any other. Without a Create tab in [tabSet] (Custom in Games)
 *   it also asks Games for its Custom section ([LibraryRequests.openCustom]), where Enter code
 *   lives.
 *
 * With a Community tab in [tabSet] (M10-1) Custom's list, its code sheet included, is Community's
 * root: a code and invalid text go there instead of Play, the same way (a code's card on Community's
 * stack, invalid text at its root, no Games section asked for). A match is a duel and stays on Play.
 */
internal fun Navigator.applyFriendLink(link: FriendLink, tabSet: TabSet, libraryRequests: LibraryRequests) {
    val owner = if (tabSet.hasCommunityTab && link !is FriendLink.Match) CommunityKey else PlayKey
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
        // Reselecting the selected tab pops it to its root.
        is FriendLink.Invalid ->
            if (!tabSet.hasCreateTab && !tabSet.hasCommunityTab) libraryRequests.openCustom(this, tabSet)
            else if (state.currentStack.size > 1) selectTab(owner)
    }
}
