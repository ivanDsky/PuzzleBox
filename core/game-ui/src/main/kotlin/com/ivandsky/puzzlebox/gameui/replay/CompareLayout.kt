package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.gameui.PanePolicy

/** How a compare lays out its two boards. */
enum class CompareLayout {
    /** Both boards side by side. */
    Twin,

    /** One board at a time with a peek of the other. */
    FlipPeek,
}

/**
 * Twin at expanded width in either orientation, when the window is also tall enough for two boards
 * ([PanePolicy.MinHeight]); FlipPeek otherwise, so a phone in landscape stacks them.
 */
fun compareLayoutFor(width: Dp, height: Dp): CompareLayout =
    if (width >= PanePolicy.ExpandedMinWidth && height >= PanePolicy.MinHeight) CompareLayout.Twin else CompareLayout.FlipPeek
