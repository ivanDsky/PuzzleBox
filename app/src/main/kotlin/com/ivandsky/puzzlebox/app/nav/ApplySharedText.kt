package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.CommunityKey

/**
 * Puts shared text where it is imported: Community's root (M10-1), where the Custom body imports it. Selects
 * Community and pops it to its root, from any tab, so a Studio left open on Community never hides under the sheet
 * that consumes the text. An open Profile layer closes first, also over Community itself. Same move as
 * [applyFriendLink], through the reducer's own verbs. The text itself is not carried in the state; the importer
 * takes it from the store on resume (`MainActivity` puts it there).
 */
internal fun Navigator.applySharedText() {
    if (state.tab != CommunityKey || state.layer.isNotEmpty()) selectTab(CommunityKey)
    // Reselecting the selected tab pops it to its root.
    if (state.currentStack.size > 1) selectTab(CommunityKey)
}
