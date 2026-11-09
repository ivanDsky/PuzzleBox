package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The shell's own top-bar actions (the avatar and the gear), drawn into a [RowScope].
 * The app provides it per entry; no feature module knows the actions exist. [LargeTopBar]
 * and [CompactTopBar] draw it after the screen's own `actions`, in the same trailing row,
 * so a screen that passes no `actions` still gets the shell's. The default is empty.
 *
 * It is a static local, so a changed value recomposes everything under its provider. The
 * provided value must therefore be a stable remembered lambda that reads changing state
 * (identity, avatar photo) inside its body: then a sign-in or a photo load recomposes only
 * the slot, not every screen below.
 */
val LocalShellActions = staticCompositionLocalOf<@Composable RowScope.() -> Unit> { {} }
