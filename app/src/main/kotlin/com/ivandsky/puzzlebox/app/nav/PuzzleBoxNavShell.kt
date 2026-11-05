package com.ivandsky.puzzlebox.app.nav

import android.provider.Settings
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.ResultKey
import org.koin.compose.koinInject

/**
 * Hosts [NavDisplay] over [navigator]'s visible per-tab entries (decorated by
 * [rememberTabEntries]) with the NavTransitions.kt specs. System back is [Navigator.pop]:
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
    val reduceMotion = rememberReduceMotion()
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
        // Fade-through: the outgoing screen fades out in 90ms BEFORE the incoming
        // one starts, so the two contents never superimpose mid-flight (Nav3's
        // simultaneous crossfade showed both screens' text over each other).
        // Slide is a residual 1/24 — tab switches aren't hierarchical.
        // Ticket 23: entering the RESULT route is the run's only new animation —
        // opacity 0→1 + scale 0.92→1 on one critically damped ~0.3s spring
        // (spec A3); reduced motion keeps the opacity half only.
        transitionSpec = {
            // Ticket 23: the shared result route (every game) takes the run's
            // one celebratory entrance.
            if (targetState.key is ResultKey) {
                resultEnterTransform(reduceMotion) togetherWith screenExitTransform()
            } else {
                screenEnterTransform() togetherWith screenExitTransform()
            }
        },
        popTransitionSpec = {
            screenEnterTransform() togetherWith screenExitTransform()
        },
        onBack = { navigator.pop() },
    )
}

// Accessibility (spec A3): animator scale 0 is the system's reduced-motion signal —
// the result fade-through collapses to opacity-only.
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
