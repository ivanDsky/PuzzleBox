package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.app.shortcut.ShortcutLanding
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey

/**
 * Puts a launcher shortcut's [landing] on the back stack, through the reducer's own verbs.
 *
 * - A board selects the Games tab without popping it, then pushes that game's today once, so Back from it returns to the
 *   Games list. A board of another game stays alive beneath, and a board already open is not stacked again. An open
 *   Profile layer closes first, also over the Games tab itself.
 * - Today selects the Today tab at its root, whichever tab or layer it came from.
 */
internal fun Navigator.applyShortcutLanding(landing: ShortcutLanding) {
    when (landing) {
        is ShortcutLanding.Board -> {
            if (state.tab != PlayKey || state.layer.isNotEmpty()) selectTab(PlayKey)
            val board = GameKey(landing.game.value)
            if (board !in state.currentStack) pushOnce(board)
        }
        ShortcutLanding.Today -> {
            selectTab(HomeKey)
            // Selecting another tab leaves its stack as it was, and a layer's close left the stack alone: reselect to pop.
            if (state.currentStack.size > 1) selectTab(HomeKey)
        }
    }
}
