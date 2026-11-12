package com.ivandsky.puzzlebox.gameui.replay

import com.ivandsky.puzzlebox.game.replay.Timeline

/**
 * What a screen knows about its replay. A view model maps its own gating (write pending,
 * write failed, no timeline) onto this; the section never learns why it is hidden.
 */
sealed interface ReplaySectionState {
    data object Hidden : ReplaySectionState

    class Ready(val timeline: Timeline) : ReplaySectionState
}
