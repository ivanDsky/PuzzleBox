package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

/** How a compare lays out its two boards. */
enum class CompareLayout {
    /** Both boards side by side. */
    Twin,

    /** One board at a time with a peek of the other. */
    FlipPeek,
}

/**
 * Twin at Expanded width in either orientation: two boards beside each other need the width, not the height.
 * FlipPeek at Compact and Medium.
 */
fun compareLayoutFor(widthSizeClass: WindowWidthSizeClass): CompareLayout =
    if (widthSizeClass == WindowWidthSizeClass.Expanded) CompareLayout.Twin else CompareLayout.FlipPeek
