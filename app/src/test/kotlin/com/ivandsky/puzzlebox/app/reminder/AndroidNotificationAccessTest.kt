package com.ivandsky.puzzlebox.app.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class AndroidNotificationAccessTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val access = AndroidNotificationAccess(context)

    // The manifest does not declare POST_NOTIFICATIONS yet (the manifest ticket adds it), so the player's grant is played here.
    private fun grantPermission() = shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    @Test
    fun `allowed when notifications are on, the permission is granted and the channel is not yet created`() {
        grantPermission()

        assertTrue(access.isAllowed())
    }

    @Test
    fun `allowed when the channel exists at default importance`() {
        grantPermission()
        manager.createNotificationChannel(NotificationChannel(ReminderChannel.ID, ReminderChannel.NAME, NotificationManager.IMPORTANCE_DEFAULT))

        assertTrue(access.isAllowed())
    }

    @Test
    fun `not allowed when app notifications are switched off`() {
        grantPermission()
        shadowOf(manager).setNotificationsEnabled(false)

        assertFalse(access.isAllowed())
    }

    @Test
    fun `not allowed on API 34 when the permission is denied`() {
        shadowOf(context as android.app.Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertFalse(access.isAllowed())
    }

    @Test
    fun `not allowed when the Daily reminder channel is blocked`() {
        grantPermission()
        manager.createNotificationChannel(NotificationChannel(ReminderChannel.ID, ReminderChannel.NAME, NotificationManager.IMPORTANCE_NONE))

        assertFalse(access.isAllowed())
    }

    @Test
    @Config(sdk = [32])
    fun `below Android 13 no permission is asked, only the enabled and channel checks apply`() {
        assertTrue(access.isAllowed())

        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(access.isAllowed())
    }
}
