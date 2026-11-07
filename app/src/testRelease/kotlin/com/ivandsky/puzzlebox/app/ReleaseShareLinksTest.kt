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

/** Release declares no share-link filter while friend sharing is dark (ADR-0022): no link opens the app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ReleaseShareLinksTest {

    @Test
    fun `release routes no share link to the app`() {
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("${ShareCode.LINK_PREFIX}K7Q2MX")).addCategory(Intent.CATEGORY_BROWSABLE)
        val handlers = RuntimeEnvironment.getApplication().packageManager.queryIntentActivities(link, 0)
        assertEquals(emptyList<String>(), handlers.map { it.activityInfo.name })
    }
}
