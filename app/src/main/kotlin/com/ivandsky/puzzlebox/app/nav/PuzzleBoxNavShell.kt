package com.ivandsky.puzzlebox.app.nav

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.designsystem.LocalNavLift
import com.ivandsky.puzzlebox.designsystem.NavLift
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.PlaySessionStore
import org.koin.compose.koinInject

/**
 * Hosts [NavDisplay] over [navigator]'s visible per-tab entries (decorated by
 * [rememberTabEntries]); every move takes its [NavMotion] from [navMotion] (NavTransitions.kt)
 * for the window's [NavForm], gated by the live reduce-motion flag, and publishes it to the
 * scenes' scrims ([LocalNavMotion]). On the tablet form a push of a game whose card is on
 * screen lifts out of that card ([NavLift]), and its pop shrinks back into it. System back is
 * [Navigator.pop]: it pops the current tab, returns a Play/Profile root to Home, and at the
 * Home root returns false so the back press falls through and the app exits.
 *
 * Play lifetime (A10, SC5): whenever the stacks change the shell hands every key they
 * hold to [PlaySessionStore.retain], so a popped, replaced or reselected-away game folds
 * and saves; at activity stop it [PlaySessionStore.flush]es every live play, so a pending
 * throttled save is never lost.
 *
 * [codeDraft] and [onCodeDraftTaken] reach the Play library: a draft opens its code sheet.
 */
@Composable
internal fun PuzzleBoxNavShell(
    navigator: Navigator,
    homeLayout: HomeLayout,
    playLayout: PlayLayout,
    historyLayout: HistoryLayout,
    modifier: Modifier = Modifier,
    codeDraft: () -> String? = { null },
    onCodeDraftTaken: () -> Unit = {},
    store: PlaySessionStore = koinInject(),
) {
    val reduceMotion = rememberReducedMotion()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val form = if (homeLayout == HomeLayout.TwoPane) NavForm.Tablet else NavForm.Phone
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
            codeDraft = codeDraft,
            onCodeDraftTaken = onCodeDraftTaken,
        ),
    )
    val motion = remember { mutableStateOf(NavMotion.Idle) }
    // The board that lifted in last: only it shrinks back into a card on its pop.
    val lifted = remember { LiftedKey() }
    SharedTransitionLayout(modifier) {
        val lift = remember(this) { NavLift(this) }
        // NavDisplay evaluates these specs inside its scenes' composition, so the scrims and
        // the shared bounds read the chosen move in the same frame it starts.
        fun play(chosen: NavMotion) = chosen.also {
            motion.value = it
            lift.lifting = it.kind == NavMotionKind.Lift
            lift.liftMillis = it.durationMillis
        }.transform(layoutDirection, density)
        CompositionLocalProvider(
            LocalNavMotion provides motion,
            LocalNavLift provides lift.takeIf { form == NavForm.Tablet },
        ) {
            NavDisplay(
                entries = entries,
                transitionSpec = {
                    val to = targetState.metadata
                    val liftable = form == NavForm.Tablet && lift.isOnScreen(to[ENTRY_KEY])
                    val chosen = navMotion(initialState.metadata, to, pop = false, reduceMotion, form, liftable)
                    lifted.key = to[ENTRY_KEY].takeIf { chosen.kind == NavMotionKind.Lift }
                    play(chosen)
                },
                popTransitionSpec = {
                    val from = initialState.metadata
                    val liftable = from[ENTRY_KEY] != null && from[ENTRY_KEY] == lifted.key &&
                        lift.isOnScreen(lifted.key)
                    play(navMotion(from, targetState.metadata, pop = true, reduceMotion, form, liftable))
                },
                predictivePopTransitionSpec = { swipeEdge ->
                    play(predictivePopMotion(swipeEdge, reduceMotion))
                },
                onBack = { navigator.pop() },
            )
        }
    }
}

/** Plain holder: written while a spec is evaluated, never observed. */
private class LiftedKey {
    var key: Any? = null
}
