package com.ivandsky.puzzlebox.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.TypedArray
import android.util.Xml
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * What the home-screen widget needs from the manifest: an exported receiver that ships disabled (so the picker does not list
 * it) with the update, boot and package-replaced filters, a private alarm receiver, and the provider file with the minimum
 * size, resize and update period the spec names.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication for DiGraphTest's reason: the real one would startKoin on its own.
@Config(sdk = [34], application = DrainTestApplication::class)
class WidgetManifestTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pm = context.packageManager
    private val widget = ComponentName(context.packageName, "${PACKAGE}.PuzzleBoxWidgetReceiver")
    private val alarm = ComponentName(context.packageName, "${PACKAGE}.WidgetAlarmReceiver")

    @Test
    fun `the widget receiver is exported and disabled by default`() {
        val info = pm.getReceiverInfo(widget, PackageManager.MATCH_DISABLED_COMPONENTS)

        assertTrue(info.exported)
        assertFalse(info.enabled)
    }

    @Test
    fun `the widget receiver answers update, boot-completed and package-replaced`() {
        assertEquals(
            listOf(AppWidgetManager.ACTION_APPWIDGET_UPDATE, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED),
            ALL_ACTIONS.filter { widget in receiversFor(it) },
        )
    }

    @Test
    fun `the alarm receiver is not exported and answers no broadcast action`() {
        assertFalse(pm.getReceiverInfo(alarm, 0).exported)
        for (action in ALL_ACTIONS) assertFalse(action, alarm in receiversFor(action))
    }

    @Test
    fun `the widget receiver names the provider file`() {
        val info = pm.getReceiverInfo(widget, PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS)

        assertEquals(R.xml.puzzlebox_widget_info, info.metaData.getInt("android.appwidget.provider"))
    }

    @Test
    fun `the provider spans 4 by 2 cells, resizes from 4 by 1 (40dp tall) to 540 by 360dp both ways and is never timer-updated`() {
        val parser = context.resources.getXml(R.xml.puzzlebox_widget_info)
        while (parser.next() != XmlPullParser.START_TAG) Unit
        val attrs = context.obtainStyledAttributes(Xml.asAttributeSet(parser), PROVIDER_ATTRS)

        // Robolectric's default density is mdpi, so a dimension in px reads as its dp.
        assertEquals(250, attrs.dimensionOf(android.R.attr.minWidth))
        assertEquals(110, attrs.dimensionOf(android.R.attr.minHeight))
        assertEquals(250, attrs.dimensionOf(android.R.attr.minResizeWidth))
        assertEquals(40, attrs.dimensionOf(android.R.attr.minResizeHeight))
        assertEquals(540, attrs.dimensionOf(android.R.attr.maxResizeWidth))
        assertEquals(360, attrs.dimensionOf(android.R.attr.maxResizeHeight))
        assertEquals(4, attrs.intOf(android.R.attr.targetCellWidth))
        assertEquals(2, attrs.intOf(android.R.attr.targetCellHeight))
        assertEquals(AppWidgetProviderInfo.RESIZE_BOTH, attrs.intOf(android.R.attr.resizeMode))
        assertEquals(0, attrs.intOf(android.R.attr.updatePeriodMillis))
        attrs.recycle()
    }

    private fun TypedArray.dimensionOf(attr: Int) = getDimensionPixelSize(PROVIDER_ATTRS.indexOf(attr), -1)

    private fun TypedArray.intOf(attr: Int) = getInt(PROVIDER_ATTRS.indexOf(attr), -1)

    private fun receiversFor(action: String): List<ComponentName> =
        pm.queryBroadcastReceivers(Intent(action).setPackage(context.packageName), PackageManager.MATCH_DISABLED_COMPONENTS)
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }

    private companion object {
        const val PACKAGE = "com.ivandsky.puzzlebox.app.widget"

        // obtainStyledAttributes wants the attribute ids in ascending order.
        val PROVIDER_ATTRS = intArrayOf(
            android.R.attr.minWidth,
            android.R.attr.minHeight,
            android.R.attr.updatePeriodMillis,
            android.R.attr.resizeMode,
            android.R.attr.targetCellWidth,
            android.R.attr.targetCellHeight,
            android.R.attr.minResizeWidth,
            android.R.attr.minResizeHeight,
            android.R.attr.maxResizeWidth,
            android.R.attr.maxResizeHeight,
        ).apply { sort() }

        val ALL_ACTIONS = listOf(
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
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
