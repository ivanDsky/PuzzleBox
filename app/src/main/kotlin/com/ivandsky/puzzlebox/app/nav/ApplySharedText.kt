package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.CommunityKey

/**
 * Puts shared text where it is imported. With a Create tab in [tabSet] (M5-09) that is Create's
 * root ViewModel: selects Create and pops it to its root, from any tab, so a Studio left open
 * on Create never hides under the sheet that consumes the text. With a Community tab (M10-1) the
 * same move lands on Community's root, where the Custom body imports the text. Without either
 * (Custom in Games) the text goes to Games' Custom section ([LibraryRequests.openCustom]), where the
 * Custom body imports it. An open Profile layer closes first in every case, also over the destination
 * itself. Same move as [applyFriendLink], through the reducer's own verbs. The text itself is not
 * carried in the state; the importer takes it from the store on resume (`MainActivity` puts it
 * there).
 */
internal fun Navigator.applySharedText(tabSet: TabSet, libraryRequests: LibraryRequests) {
    val root = when {
        tabSet.hasCommunityTab -> CommunityKey
        tabSet.hasCreateTab -> CreateKey
        else -> {
            libraryRequests.openCustom(this, tabSet)
            return
        }
    }
    if (state.tab != root || state.layer.isNotEmpty()) selectTab(root)
    // Reselecting the selected tab pops it to its root.
    if (state.currentStack.size > 1) selectTab(root)
}

/** Whether Create is a tab of this set; when it is not, making and importing live in Community, or in Games' Custom section. */
internal val TabSet.hasCreateTab: Boolean get() = destinations.any { it.key == CreateKey }

/** Whether Community is a tab of this set (CustomInGames and CommunityPuzzles both on): Custom's list is its root. */
internal val TabSet.hasCommunityTab: Boolean get() = destinations.any { it.key == CommunityKey }
