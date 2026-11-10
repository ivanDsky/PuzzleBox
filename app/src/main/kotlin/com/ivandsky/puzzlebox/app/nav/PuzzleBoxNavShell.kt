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
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag.HintParity
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.compete.api.DuelKey
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
 * [Navigator.pop]: it pops the current tab, returns a Play/Profile root to Home, and at the
 * Home root returns false so the back press falls through and the app exits.
 *
 * Play lifetime (A10, SC5): whenever the stacks change the shell hands every key they
 * hold to [PlaySessionStore.retain], so a popped, replaced or reselected-away game folds
 * and saves; at activity stop it [PlaySessionStore.flush]es every live play, so a pending
 * throttled save is never lost.
 *
 * [codeDraft] and [onCodeDraftTaken] reach the Play library: a draft opens its code sheet.
 * [libraryRequests] is the one-shot Games request: Games reads its pending value and takes it.
 * [duels] and [onOpenDuel] (both null with the Competitions flag off) add the Duels tab and
 * let Enter code open a duel. [navV2] is the NavV2 value the app read once at launch
 * ([rememberNavV2]); the shell never reads the flag itself. With it on, Browse screens get
 * the avatar and gear ([shellChromeDecorator]) over [identityService]'s signed-in player.
 * Every entry sees [LocalHintCooldown] from [flags] ([withHintCooldown]).
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
    onOpenDuel: ((DuelKey) -> Unit)? = null,
    navV2: Boolean = false,
    libraryRequests: LibraryRequests = rememberLibraryRequests(),
    store: PlaySessionStore = koinInject(),
    identityService: IdentityService = koinInject(),
    flags: ReleaseFlags = koinInject(),
) {
    val reduceMotion = rememberReducedMotion()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val form = if (homeLayout == HomeLayout.TwoPane) NavForm.Tablet else NavForm.Phone
    val state = navigator.state
    LaunchedEffect(state.allKeys) { store.retain(state.allKeys) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { store.flush() }
    val shellChrome = if (navV2) {
        remember(navigator, identityService) { shellChromeDecorator(navigator, identityService.current) }
    } else {
        null
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
            onOpenDuel = onOpenDuel,
            navV2 = navV2,
            libraryRequests = libraryRequests,
        ).withHintCooldown(flags),
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
 * Gives each entry's content [LocalHintCooldown]: [HINT_COOLDOWN] while HintParity is on, else
 * nothing (A20, today's composition). Read once when the entry first composes, so a debug
 * override applies from the next board and never mid-play, like
 * [com.ivandsky.puzzlebox.gameui.hint.HintModeSource.current].
 */
internal fun ((NavKey) -> NavEntry<NavKey>).withHintCooldown(flags: ReleaseFlags): (NavKey) -> NavEntry<NavKey> = { key ->
    val entry = this(key)
    NavEntry(key, entry.contentKey, entry.metadata) {
        val cooldown = remember { HINT_COOLDOWN.takeIf { flags.isEnabled(HintParity) } }
        if (cooldown == null) {
            entry.Content()
        } else {
            CompositionLocalProvider(LocalHintCooldown provides cooldown) { entry.Content() }
        }
    }
}
