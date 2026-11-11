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
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.designsystem.LocalNavLift
import com.ivandsky.puzzlebox.designsystem.NavLift
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.HINT_COOLDOWN
import com.ivandsky.puzzlebox.gameui.LocalHintCooldown
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.DuelsSlot
import com.ivandsky.puzzlebox.play.PlaySessionStore
import org.koin.compose.koinInject

/**
 * Hosts [NavDisplay] over [navigator]'s visible per-tab entries (decorated by
 * [rememberTabEntries]); every move takes its [NavMotion] from [navMotion] (NavTransitions.kt)
 * for the window's [NavForm], gated by the live reduce-motion flag, and publishes it to the
 * scenes' scrims ([LocalNavMotion]). On the tablet form a push of a game whose card is on
 * screen lifts out of that card ([NavLift]), and its pop shrinks back into it. System back is
 * [Navigator.pop]: it pops the current tab, returns a Games/History/Create root to Today, and at the
 * Home root returns false so the back press falls through and the app exits.
 *
 * Play lifetime: whenever the stacks change the shell hands every key they
 * hold to [PlaySessionStore.retain], so a popped, replaced or reselected-away game folds
 * and saves; at activity stop it [PlaySessionStore.flush]es every live play, so a pending
 * throttled save is never lost.
 *
 * [codeDraft] and [onCodeDraftTaken] reach the Play library: a draft opens its code sheet.
 * [libraryRequests] is the one-shot Games request: Games reads its pending value and takes it.
 * [duels] (null with the Competitions flag off) adds the Duels tab and
 * lets Enter code open a duel. The shell draws the
 * avatar ([ShellAvatar]) over [identityService]'s signed-in player: in Today's header on the
 * phone, at the rail's bottom on the tablet. Settings is reached through Profile's gear.
 * [linkedIn] is read once per launch through [rememberLinkedIn] and gates the LinkedIn entries.
 * Every entry sees [LocalHintCooldown] ([withHintCooldown]).
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
    duels: DuelsSlot? = null,
    libraryRequests: LibraryRequests = rememberLibraryRequests(),
    linkedIn: Boolean = rememberLinkedIn(),
    store: PlaySessionStore = koinInject(),
    identityService: IdentityService = koinInject(),
) {
    val reduceMotion = rememberReducedMotion()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val form = if (homeLayout == HomeLayout.TwoPane) NavForm.Tablet else NavForm.Phone
    val state = navigator.state
    LaunchedEffect(state.allKeys) { store.retain(state.allKeys) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { store.flush() }
    val shellChrome = remember(navigator, identityService, form) {
        shellChromeDecorator(navigator, identityService.current, avatarInTodayHeader = form == NavForm.Phone)
    }
    val entries = rememberTabEntries(
        state = navigator.state,
        entryProvider = puzzleBoxEntryProvider(
            navigator = navigator,
            homeLayout = homeLayout,
            playLayout = playLayout,
            historyLayout = historyLayout,
            codeDraft = codeDraft,
            onCodeDraftTaken = onCodeDraftTaken,
            duels = duels,
            libraryRequests = libraryRequests,
            linkedIn = linkedIn,
        ).withHintCooldown(),
        shellChrome = shellChrome,
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

/**
 * Gives each entry's content [LocalHintCooldown] = [HINT_COOLDOWN], so the core action bar gates
 * every game's Hint button the same way.
 */
internal fun ((NavKey) -> NavEntry<NavKey>).withHintCooldown(): (NavKey) -> NavEntry<NavKey> = { key ->
    val entry = this(key)
    NavEntry(key, entry.contentKey, entry.metadata) {
        CompositionLocalProvider(LocalHintCooldown provides HINT_COOLDOWN) { entry.Content() }
    }
}
