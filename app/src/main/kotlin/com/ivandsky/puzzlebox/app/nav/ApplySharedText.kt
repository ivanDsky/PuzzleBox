package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.CommunityKey

/**
 * Puts shared text where it is imported. With a Community tab in [tabSet] (M10-1) that is Community's root, from any
 * tab, popped to the root so a Studio left open there never hides under the sheet that consumes the text: the Custom
 * body imports it. Without one the text goes to Games' Custom section ([LibraryRequests.openCustom]), where the
 * Custom body imports it. An open Profile layer closes first in every case, also over the destination
 * itself. Same move as [applyFriendLink], through the reducer's own verbs. The text itself is not
 * carried in the state; the importer takes it from the store on resume (`MainActivity` puts it
 * there).
 */
internal fun Navigator.applySharedText(tabSet: TabSet, libraryRequests: LibraryRequests) {
    if (!tabSet.hasCommunityTab) {
        libraryRequests.openCustom(this, tabSet)
        return
    }
    val root = CommunityKey
    if (state.tab != root || state.layer.isNotEmpty()) selectTab(root)
    // Reselecting the selected tab pops it to its root.
    if (state.currentStack.size > 1) selectTab(root)
}

/** Whether Community is a tab of this set (CommunityPuzzles on): Custom's list is its root. */
internal val TabSet.hasCommunityTab: Boolean get() = destinations.any { it.key == CommunityKey }
