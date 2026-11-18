package com.ivandsky.puzzlebox.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the reminder needs from the manifest: two permissions, a private alarm receiver and a boot receiver that hears only boot and update. */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication for DiGraphTest's reason: the real one would startKoin on its own.
@Config(sdk = [34], application = DrainTestApplication::class)
class ReminderManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pm = context.packageManager
    private val alarm = ComponentName(context.packageName, "${PACKAGE}.ReminderAlarmReceiver")
    private val reboot = ComponentName(context.packageName, "${PACKAGE}.ReminderRebootReceiver")

    @Test
    fun `both reminder permissions are declared`() {
        val requested = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue("android.permission.POST_NOTIFICATIONS" in requested)
        assertTrue("android.permission.RECEIVE_BOOT_COMPLETED" in requested)
    }

    @Test
    fun `the alarm receiver is not exported and answers no broadcast action`() {
        assertFalse(pm.getReceiverInfo(alarm, 0).exported)
        for (action in ALL_ACTIONS) assertFalse(action, alarm in receiversFor(action))
    }

    @Test
    fun `the reboot receiver is exported and answers boot-completed and package-replaced only`() {
        assertTrue(pm.getReceiverInfo(reboot, 0).exported)
        assertEquals(
            listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED),
            ALL_ACTIONS.filter { reboot in receiversFor(it) },
        )
    }

    private fun receiversFor(action: String): List<ComponentName> =
        pm.queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }

    private companion object {
        const val PACKAGE = "com.ivandsky.puzzlebox.app.reminder"
        val ALL_ACTIONS = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MAIN,
        )
    }
}
