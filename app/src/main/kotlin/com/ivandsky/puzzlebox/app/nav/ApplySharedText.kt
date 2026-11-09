package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.navigation.Navigator

/**
 * Puts shared text in front of Create's root ViewModel (M5-09): selects Create and pops it to
 * its root, from any tab, so a Studio left open on Create never hides under the sheet that
 * consumes the text. Same move as [applyFriendLink], through the reducer's own verbs. The text
 * itself is not carried in the state; the root's ViewModel takes it from the store on resume
 * (`MainActivity` puts it there).
 */
internal fun Navigator.applySharedText() {
    if (state.tab != CreateKey) selectTab(CreateKey)
    // Reselecting the selected tab pops it to its root.
    if (state.currentStack.size > 1) selectTab(CreateKey)
}
