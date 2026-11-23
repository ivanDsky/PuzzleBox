package com.ivandsky.puzzlebox.play

import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.bleedSides

/**
 * Where a large top bar over a list reaches. On the tablet ([phone] false) it fills the panes' [Spacing.xl] start gutter,
 * so its title sits where Today's does, and the [Spacing.lg] past the list's end, so its actions end with the list.
 */
internal fun Modifier.mainTabBarBleed(phone: Boolean): Modifier =
    if (phone) this else bleedSides(start = MainTabBarBleedStart, end = Spacing.lg)

/** How far a large top bar reaches into the start gutter on the tablet; whatever covers the list must cover it too. */
internal val MainTabBarBleedStart = Spacing.xl
