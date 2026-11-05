package com.ivandsky.puzzlebox.app.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.PlaySessionStore
import org.koin.compose.koinInject

/**
 * Hosts [NavDisplay] over [navigator]'s visible per-tab entries (decorated by
 * [rememberTabEntries]); push, pop and predictive back each take their transform from
 * NavTransitions.kt, gated by the live reduce-motion flag. System back is [Navigator.pop]:
 * it pops the current tab, returns a Play/Profile root to Home, and at the Home root
 * returns false so the back press falls through and the app exits.
 *
 * Play lifetime (A10, SC5): whenever the stacks change the shell hands every key they
 * hold to [PlaySessionStore.retain], so a popped, replaced or reselected-away game folds
 * and saves; at activity stop it [PlaySessionStore.flush]es every live play, so a pending
 * throttled save is never lost.
 */
@Composable
internal fun PuzzleBoxNavShell(
    navigator: Navigator,
    homeLayout: HomeLayout,
    playLayout: PlayLayout,
    historyLayout: HistoryLayout,
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    store: PlaySessionStore = koinInject(),
) {
    val reduceMotion = rememberReducedMotion()
    val layoutDirection = LocalLayoutDirection.current
    val state = navigator.state
    LaunchedEffect(state.allKeys) { store.retain(state.allKeys) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { store.flush() }
    val entries = rememberTabEntries(
        state = navigator.state,
        entryProvider = puzzleBoxEntryProvider(
            navigator = navigator,
            homeLayout = homeLayout,
            playLayout = playLayout,
            historyLayout = historyLayout,
            themeMode = themeMode,
            onSetThemeMode = onSetThemeMode,
        ),
    )
    NavDisplay(
        entries = entries,
        modifier = modifier,
        transitionSpec = {
            navTransition(initialState.metadata, targetState.metadata, pop = false, reduceMotion, layoutDirection)
        },
        popTransitionSpec = {
            navTransition(initialState.metadata, targetState.metadata, pop = true, reduceMotion, layoutDirection)
        },
        predictivePopTransitionSpec = { swipeEdge ->
            predictivePopTransition(swipeEdge, reduceMotion)
        },
        onBack = { navigator.pop() },
    )
}
