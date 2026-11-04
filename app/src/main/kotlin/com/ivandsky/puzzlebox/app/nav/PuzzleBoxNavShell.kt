package com.ivandsky.puzzlebox.app.nav

import android.provider.Settings
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.ResultKey

/**
 * Hosts [NavDisplay] over [navigator]'s back stack with the shell decorators (saveable
 * state outermost, then the opaque scene canvas) and the NavTransitions.kt specs.
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
) {
    // Every scene root paints an opaque canvas (SceneCanvas) — applied once here as an
    // entry decorator so feature entries stay shell-agnostic. The saveable-state holder
    // stays outermost (NavDisplay's default), the same tree each entry had wrapping itself.
    val sceneCanvas = remember { NavEntryDecorator<NavKey> { entry -> SceneCanvas { entry.Content() } } }
    val reduceMotion = rememberReduceMotion()
    NavDisplay(
        backStack = navigator.backStack,
        modifier = modifier,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), sceneCanvas),
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
        entryProvider = puzzleBoxEntryProvider(
            navigator = navigator,
            homeLayout = homeLayout,
            playLayout = playLayout,
            historyLayout = historyLayout,
            themeMode = themeMode,
            onSetThemeMode = onSetThemeMode,
        ),
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

// Opaque canvas behind every nav scene: without it the AnimatedContent layers blend and
// screens read as ghosts of each other during the transition.
@Composable
private fun SceneCanvas(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        content()
    }
}
