package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource

// An extension rather than a member because PuzzleIcons.kt sits at the file-length cap; call
// sites still read `PuzzleIcons.Hidden`.

/** Kept out of sight for now: `visibility_off`, an eye struck through. */
val PuzzleIcons.Hidden: ImageVector
    @Composable get() = ImageVector.vectorResource(R.drawable.ic_visibility_off)
