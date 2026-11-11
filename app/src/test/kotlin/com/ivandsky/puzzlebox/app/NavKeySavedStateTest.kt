package com.ivandsky.puzzlebox.app

import android.app.Application
import android.os.Looper
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import com.ivandsky.puzzlebox.app.nav.Home
import com.ivandsky.puzzlebox.app.nav.Tab
import com.ivandsky.puzzlebox.app.nav.TopLevelDestination
import com.ivandsky.puzzlebox.app.nav.placementOf
import com.ivandsky.puzzlebox.app.nav.puzzleBoxNavKeys
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.api.PlayKey
import kotlinx.serialization.PolymorphicSerializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Process-death restore of the per-tab state: a [NavigationState] holding every key the app
 * can push survives the saved-state round trip through [puzzleBoxNavKeys], and a 0.8
 * single-`NavBackStack` bundle does not decode as one (rememberNavigator then starts fresh,
 * the shell-level fallback is PerTabShellTest's).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NavKeySavedStateTest {

    /**
     * Every key in the placement table. A live root (a key of [topLevelDestinations]) sits at index 0 of its own stack, because a
     * root is only allowed there; every other key goes on the stack of the destination whose
     * [TopLevelDestination.tab] is the key's home tab, or on stack 0 when the key has no tab
     * (Shell, DebugOnly).
     */
    private val everyKey: NavigationState = run {
        val roots = topLevelDestinations.map { it.key }
        // A per-game History key keeps the nullable payload covered.
        val pushed = PlacementSamples.everyRow().filter { it !in roots } + HistoryKey("zip")
        val stackOf = { key: NavKey ->
            topLevelDestinations.indexOfFirst { it.tab == homeTab(key) }.coerceAtLeast(0)
        }
        NavigationState(
            tab = PlayKey,
            stacks = roots.mapIndexed { index, root -> listOf(root) + pushed.filter { stackOf(it) == index } },
        )
    }

    private fun homeTab(key: NavKey): Tab? = when (val home = placementOf(key).home) {
        is Home.TabRoot -> home.tab
        is Home.PushedOn -> home.tab
        Home.Shell, Home.DebugOnly -> null
    }

    /**
     * `NavBackStack` is snapshot state, so building one writes the global snapshot. That
     * write posts Compose's apply notification to the main looper — once per sandbox, gated
     * until it runs. Robolectric clears the looper between tests, so an unrun post would
     * silence every later global write in this sandbox (History's paged rows never arrive
     * in the E2E rows). Run it before the test ends.
     */
    @After
    fun flushSnapshotNotifications() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `every key round-trips through the saved-state configuration`() {
        val saved = encodeToSavedState(NavigationState.serializer(), everyKey, puzzleBoxNavKeys)

        val restored = decodeFromSavedState(NavigationState.serializer(), saved, puzzleBoxNavKeys)

        assertEquals(everyKey, restored)
    }

    @Test
    fun `a 0_8 single-stack bundle does not decode as navigation state`() {
        val legacy = encodeToSavedState(
            NavBackStackSerializer(PolymorphicSerializer(NavKey::class)),
            NavBackStack(HomeKey, PlayKey),
            puzzleBoxNavKeys,
        )

        val decoded = runCatching { decodeFromSavedState(NavigationState.serializer(), legacy, puzzleBoxNavKeys) }

        assertTrue("expected a decode failure, got ${decoded.getOrNull()}", decoded.isFailure)
    }
}
