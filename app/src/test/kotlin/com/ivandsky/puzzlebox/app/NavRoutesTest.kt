package com.ivandsky.puzzlebox.app

import android.app.Application
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.nav.CatalogKey
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import java.util.Base64
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ticket 17 nav-route regression: [puzzleBoxEntryProvider] builds the route table as a
 * plain key→entry lookup, so these tests resolve every key the wired flow pushes and
 * fail on a missing entry — the exact gap that would strand a tap on an unregistered
 * destination (Home→Play, Home→History, history→Replay, Home account→Profile,
 * Profile→Catalog) with no composition or Koin graph needed. The result-route stack
 * contract (ticket 23) lives with its owner, play's PlayBackStackTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NavRoutesTest {

    /** A key a future surface might push before its entry is wired. */
    private data object Unregistered : NavKey

    private fun provider(): (NavKey) -> NavEntry<NavKey> =
        puzzleBoxEntryProvider(
            navigator = Navigator(NavBackStack(HomeKey)),
            homeLayout = HomeLayout.SinglePane,
            playLayout = PlayLayout.SinglePane,
            historyLayout = HistoryLayout.SinglePane,
            themeMode = ThemeMode.SYSTEM,
            onSetThemeMode = {},
        )

    @Test
    fun `every primary-flow key resolves to a registered entry`() {
        val session = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))
        val keys = listOf<NavKey>(
            HomeKey, PlayKey, HistoryKey, ReplayKey(session), ResultKey(95_000, 1), ProfileKey, CatalogKey,
        )

        keys.forEach { key ->
            val entry = provider().invoke(key)
            // contentKey derives from the resolved key — prove the requested key, not a
            // stand-in, reached the entry.
            assertTrue(
                "expected entry for $key, got contentKey=${entry.contentKey}",
                entry.contentKey.toString().contains(key.toString()),
            )
        }
    }

    @Test
    fun `replay resolution keeps the day's session blob intact`() {
        // The history route base64-encodes the replay session into the key; the play
        // entry decodes it back. Resolution must not mangle the carrier string.
        val blob = byteArrayOf(0, 1, -1, 42, 7)
        val encoded = Base64.getEncoder().encodeToString(blob)

        val entry = provider().invoke(ReplayKey(encoded))

        assertTrue(entry.contentKey.toString().contains(encoded))
    }

    @Test
    fun `unregistered key fails fast instead of rendering a blank scene`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            provider().invoke(Unregistered)
        }
        assertTrue(
            "expected an unknown-key failure, got: ${exception.message}",
            exception.message.orEmpty().contains("Unregistered"),
        )
    }
}
