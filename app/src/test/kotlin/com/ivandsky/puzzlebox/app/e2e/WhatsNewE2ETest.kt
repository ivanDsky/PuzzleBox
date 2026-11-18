package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real shell and the real Koin graph: with WhatsNew on, Settings' About row opens the What's new page (the shell's own
 * `onOpenWhatsNew`, not a stand-in), the bundled notes are listed newest first, and Back returns to Settings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class WhatsNewE2ETest {

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the row opens the page with the bundled releases and Back returns to Settings`() = openAndBack()

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet the same row opens the same page`() = openAndBack()

    private fun openAndBack() = shellRobot(graph = shellGraph(ReleaseFlag.WhatsNew)) {
        tapAvatar()
        tapGear()
        assertRoute(SETTINGS)

        tap("settings.whatsNew")
        assertRoute(WHATS_NEW)
        awaitTag("whatsNew.release.0.36.2")
        assertTrue("the bundled releases are listed", shows("whatsNew.release.0.36.2"))

        tap("whatsNew.back")
        assertRoute(SETTINGS)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `with the flag off Settings has no What's new row`() = shellRobot(graph = shellGraph()) {
        tapAvatar()
        tapGear()
        assertRoute(SETTINGS)

        assertFalse(shows("settings.whatsNew"))
    }

    private companion object {
        const val PHONE = "w411dp-h891dp"
        const val RAIL = "w1280dp-h800dp-land"
        const val SETTINGS = "route.settings"
        const val WHATS_NEW = "route.whatsNew"
    }
}
