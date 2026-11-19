package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource

// Extensions rather than members because PuzzleIcons.kt sits at the file-length cap; call sites still read
// `PuzzleIcons.Globe`. The filter rows' other icons are the existing ones (Hidden, Community, Profile, Level*).

/** Community source: Material Symbols `public`, a globe. */
val PuzzleIcons.Globe: ImageVector
    @Composable get() = ImageVector.vectorResource(R.drawable.ic_public)

/** Past days source: Material Symbols `calendar_today`. */
val PuzzleIcons.Calendar: ImageVector
    @Composable get() = ImageVector.vectorResource(R.drawable.ic_calendar_today)

/** New freshness: Material Symbols `auto_awesome`, a sparkle. */
val PuzzleIcons.Sparkle: ImageVector
    @Composable get() = ImageVector.vectorResource(R.drawable.ic_auto_awesome)
