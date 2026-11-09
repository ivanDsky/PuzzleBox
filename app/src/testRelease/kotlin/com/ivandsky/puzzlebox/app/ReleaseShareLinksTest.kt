package com.ivandsky.puzzlebox.app

import android.content.Intent
import android.net.Uri
import com.ivandsky.puzzlebox.model.ShareCode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The main manifest, which release builds ship, routes a share link to MainActivity
 * (ADR-0022 flip).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ReleaseShareLinksTest {

    private val packageManager get() = RuntimeEnvironment.getApplication().packageManager

    private fun handlers(url: String): List<String> {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        return packageManager.queryIntentActivities(link, 0).map { it.activityInfo.name }
    }

    @Test
    fun `release routes a share link to the app`() {
        assertEquals(
            listOf(MainActivity::class.java.name),
            handlers("${ShareCode.LINK_PREFIX}K7Q2MX"),
        )
    }

    @Test
    fun `release routes a match link to the app`() {
        assertEquals(
            listOf(MainActivity::class.java.name),
            handlers("${ShareCode.MATCH_LINK_PREFIX}K7Q2MX"),
        )
    }

    @Test
    fun `release does not route a link on another host or path`() {
        assertEquals(emptyList<String>(), handlers("https://example.com/p/K7Q2MX"))
        assertEquals(emptyList<String>(), handlers("https://puzzlebox-f3356.web.app/x/K7Q2MX"))
    }
}
