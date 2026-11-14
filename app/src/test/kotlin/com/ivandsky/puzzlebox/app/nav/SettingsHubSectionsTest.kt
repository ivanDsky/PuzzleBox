package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.e2e.ShellRobot
import com.ivandsky.puzzlebox.app.e2e.shellGraph
import com.ivandsky.puzzlebox.app.e2e.shellRobot
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings under `ReleaseFlag.LeaderboardHub`, through the real app graph: with the flag off the screen
 * has neither the Visibility section nor the Ranks scope section; with it on it has both. The sections'
 * own behaviour is pinned in the profile module; this pins the app's wiring of the flag to them.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class SettingsHubSectionsTest {

    @Test
    fun `with the hub off Settings has no Visibility and no Ranks scope section`() = settings(hub = false) {
        assertEquals(emptyList<String>(), HUB_SECTIONS.filter { shows(it) })
    }

    @Test
    fun `with the hub on Settings has the Visibility and the Ranks scope sections`() = settings(hub = true) {
        HUB_SECTIONS.forEach { awaitTag(it) }
    }

    /** Opens Settings the way a player does (avatar, then Profile's gear) and waits for the section that follows the hub's. */
    private fun settings(hub: Boolean, block: ShellRobot.() -> Unit) = shellRobot(
        graph = if (hub) shellGraph(ReleaseFlag.LeaderboardHub) else shellGraph(),
        destinations = if (hub) ranksTopLevelDestinations else topLevelDestinations,
    ) {
        tapAvatar()
        tapGear()
        assertRoute(SETTINGS)
        awaitTag(ABOUT)
        rule.waitForIdle()
        block()
    }

    private companion object {
        const val SETTINGS = "route.settings"
        const val ABOUT = "settings.about"
        val HUB_SECTIONS = listOf("settings.visibility", "settings.ranks")
    }
}
