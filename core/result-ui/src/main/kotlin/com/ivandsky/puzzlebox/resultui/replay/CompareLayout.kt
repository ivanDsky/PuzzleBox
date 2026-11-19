package com.ivandsky.puzzlebox.resultui.replay

import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.gameui.PanePolicy

/** How a compare lays out its two boards. */
enum class CompareLayout {
    /** Both boards side by side. */
    Twin,

    /** One board at a time, centred on the screen, with the other a swipe or a tab tap away. */
    FlipPeek,
}

/**
 * Twin at expanded width in either orientation, when the window is also tall enough for two boards
 * ([PanePolicy.MinHeight]); FlipPeek otherwise, so a phone in landscape stacks them.
 */
fun compareLayoutFor(width: Dp, height: Dp): CompareLayout =
    if (width >= PanePolicy.ExpandedMinWidth && height >= PanePolicy.MinHeight) CompareLayout.Twin else CompareLayout.FlipPeek
