package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.CommunityKey

/**
 * Puts shared text where it is imported: Community's root, from any tab, popped to the root so a Studio left open there
 * never hides under the sheet that consumes the text: the Custom body imports it. An open Profile layer closes first,
 * also over the destination itself. Same move as [applyFriendLink], through the reducer's own verbs. The text itself is
 * not carried in the state; the importer takes it from the store on resume (`MainActivity` puts it there).
 */
internal fun Navigator.applySharedText() {
    val root = CommunityKey
    if (state.tab != root || state.layer.isNotEmpty()) selectTab(root)
    // Reselecting the selected tab pops it to its root.
    if (state.currentStack.size > 1) selectTab(root)
}
