package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.bleedSides
import org.koin.compose.currentKoinScope

/** The MainTabsLargeBar flag as feature/play's DI read it once per graph (backlog #1597); no composable reads the flag itself. */
internal class MainTabsLargeBar(val enabled: Boolean)

/**
 * Whether Games and Community open on Today's large top bar: the graph's [MainTabsLargeBar]. A graph that binds none (an
 * isolated route test, a golden host) reads as off, so those screens keep the bars they had.
 */
@Composable
internal fun rememberMainTabsLargeBar(): Boolean {
    val scope = currentKoinScope()
    return remember(scope) { scope.getOrNull<MainTabsLargeBar>()?.enabled == true }
}

/**
 * Where a large top bar over a list reaches. On the tablet ([phone] false) it fills the panes' [Spacing.xl] start gutter,
 * so its title sits where Today's does, and the [Spacing.lg] past the list's end, so its actions end with the list.
 */
internal fun Modifier.mainTabBarBleed(phone: Boolean): Modifier =
    if (phone) this else bleedSides(start = Spacing.xl, end = Spacing.lg)
