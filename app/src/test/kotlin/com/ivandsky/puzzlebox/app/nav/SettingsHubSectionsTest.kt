package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.e2e.ShellRobot
import com.ivandsky.puzzlebox.app.e2e.shellGraph
import com.ivandsky.puzzlebox.app.e2e.shellRobot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings through the real app graph: the screen has the Visibility section and the Ranks scope section. The sections'
 * own behaviour is pinned in the profile module; this pins the app's wiring of them.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class SettingsHubSectionsTest {

    @Test
    fun `Settings has the Visibility and the Ranks scope sections`() = settings {
        HUB_SECTIONS.forEach { awaitTag(it) }
    }

    /** Opens Settings the way a player does (avatar, then Profile's gear) and waits for the section that follows the hub's. */
    private fun settings(block: ShellRobot.() -> Unit) = shellRobot(
        graph = shellGraph(),
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
