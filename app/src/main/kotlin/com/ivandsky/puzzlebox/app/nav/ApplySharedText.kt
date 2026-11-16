package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.navigation.Navigator

/**
 * Puts shared text where it is imported. With a Create tab in [tabSet] (M5-09) that is Create's
 * root ViewModel: selects Create and pops it to its root, from any tab, so a Studio left open
 * on Create never hides under the sheet that consumes the text. Without one (Custom in Games)
 * the text goes to Games' Custom section ([LibraryRequests.openCustom]), where the Custom body
 * imports it. An open Profile layer closes first either way, also over the destination itself.
 * Same move as [applyFriendLink], through the reducer's own verbs. The text itself is not
 * carried in the state; the importer takes it from the store on resume (`MainActivity` puts it
 * there).
 */
internal fun Navigator.applySharedText(tabSet: TabSet, libraryRequests: LibraryRequests) {
    if (!tabSet.hasCreateTab) {
        libraryRequests.openCustom(this, tabSet)
        return
    }
    if (state.tab != CreateKey || state.layer.isNotEmpty()) selectTab(CreateKey)
    // Reselecting the selected tab pops it to its root.
    if (state.currentStack.size > 1) selectTab(CreateKey)
}

/** Whether Create is a tab of this set; when it is not, making and importing live in Games' Custom section. */
internal val TabSet.hasCreateTab: Boolean get() = destinations.any { it.key == CreateKey }
