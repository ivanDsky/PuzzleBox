package com.ivandsky.puzzlebox.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class WidgetReceiversTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val receiver = WidgetAlarmReceiver()
    private val widgetReceiver = PuzzleBoxWidgetReceiver()
    private val alarm = FakeWidgetRolloverAlarm()
    private var refreshes = 0
    private var failure: Exception? = null

    @Before
    fun startGraph() {
        val refresher = WidgetRefresher {
            refreshes++
            failure?.let { throw it }
        }
        startKoin {
            modules(
                module {
                    single { refresher }
                    single { PuzzleBoxWidget(WidgetStateReader({ flow { error("no data") } }, { flow { error("no data") } }, testScoreboard(), BuildWidgetState())) }
                    single<WidgetRolloverAlarm> { alarm }
                    single {
                        ArmWidgetRollover({ listOf(ZoneId.of("America/Los_Angeles")) }, { true }, alarm, Clock.fixed(NOW, ZoneOffset.UTC))
                    }
                    single<CoroutineScope> { scope }
                },
            )
        }
        context.registerReceiver(receiver, IntentFilter(ACTION), Context.RECEIVER_NOT_EXPORTED)
    }

    @After
    fun stopGraph() {
        context.unregisterReceiver(receiver)
        scope.cancel()
        stopKoin()
    }

    private fun broadcast() {
        context.sendBroadcast(Intent(ACTION).setPackage(context.packageName))
        shadowOf(Looper.getMainLooper()).idle()
    }

    // A real broadcast for the widget callbacks, because Glance runs them async and needs the receiver's pending result.
    private fun broadcastToWidget(action: String, extras: Intent.() -> Unit = {}) {
        context.registerReceiver(widgetReceiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        try {
            context.sendBroadcast(Intent(action).setPackage(context.packageName).apply(extras))
            shadowOf(Looper.getMainLooper()).idle()
            shadowOf(widgetReceiver).originalPendingResult?.let { shadowOf(it).future.get(5, TimeUnit.SECONDS) }
        } finally {
            context.unregisterReceiver(widgetReceiver)
        }
    }

    private fun pendingResultFinished(): Boolean {
        val pending = checkNotNull(shadowOf(receiver).originalPendingResult) { "the receiver never called goAsync" }
        return shadowOf(pending).future.isDone
    }

    @Test
    fun `a broadcast refreshes the widgets, arms the next rollover once and finishes the pending result`() {
        broadcast()

        assertEquals(1, refreshes)
        assertEquals(listOf(NEXT_ROLLOVER), alarm.scheduled)
        assertTrue(shadowOf(receiver).wentAsync())
        assertTrue(pendingResultFinished())
    }

    @Test
    fun `a failing refresh is swallowed to a log line and the pending result still finishes`() {
        failure = IllegalStateException("glance update failed")

        broadcast()

        assertEquals(1, refreshes)
        assertTrue(ShadowLog.getLogsForTag("WidgetAlarmReceiver").any { it.type == Log.WARN })
        assertTrue(pendingResultFinished())
    }

    @Test
    fun `a failing refresh still arms the next rollover`() {
        failure = IllegalStateException("glance update failed")

        broadcast()

        assertEquals(listOf(NEXT_ROLLOVER), alarm.scheduled)
    }

    @Test
    fun `a failing arm is swallowed and the pending result still finishes`() {
        stopKoin()
        val failing = FakeWidgetRolloverAlarm(IllegalStateException("alarm service gone"))
        startKoin {
            modules(
                module {
                    single { WidgetRefresher { refreshes++ } }
                    single<CoroutineScope> { scope }
                    single { ArmWidgetRollover({ listOf(ZoneId.of("UTC")) }, { true }, failing, Clock.fixed(NOW, ZoneOffset.UTC)) }
                },
            )
        }

        broadcast()

        assertEquals(1, refreshes)
        assertTrue(pendingResultFinished())
    }

    @Test
    fun `a boot broadcast to the widget receiver arms the rollover`() {
        widgetReceiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(listOf(NEXT_ROLLOVER), alarm.scheduled)
    }

    @Test
    fun `an app update broadcast to the widget receiver arms the rollover`() {
        widgetReceiver.onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))

        assertEquals(listOf(NEXT_ROLLOVER), alarm.scheduled)
    }

    @Test
    fun `any other broadcast to the widget receiver arms nothing`() {
        widgetReceiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))

        assertTrue(alarm.scheduled.isEmpty())
        assertEquals(0, alarm.cancels)
    }

    @Test
    fun `adding a widget arms the rollover`() {
        broadcastToWidget(AppWidgetManager.ACTION_APPWIDGET_ENABLED)

        assertEquals(listOf(NEXT_ROLLOVER), alarm.scheduled)
    }

    @Test
    fun `updating a widget arms the rollover`() {
        broadcastToWidget(AppWidgetManager.ACTION_APPWIDGET_UPDATE) { putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(1)) }

        // Robolectric also delivers the broadcast to the manifest's own instance, so it may arm twice here; both set the same instant.
        assertTrue(alarm.scheduled.isNotEmpty() && alarm.scheduled.all { it == NEXT_ROLLOVER })
    }

    @Test
    fun `removing the last widget cancels the rollover`() {
        broadcastToWidget(AppWidgetManager.ACTION_APPWIDGET_DISABLED)

        assertEquals(1, alarm.cancels)
    }

    private companion object {
        const val ACTION = "com.ivandsky.puzzlebox.test.WIDGET_ALARM"
        val NOW: Instant = Instant.parse("2026-11-20T23:00:00Z")
        val NEXT_ROLLOVER: Instant = Instant.parse("2026-11-21T08:00:00Z")
    }
}
