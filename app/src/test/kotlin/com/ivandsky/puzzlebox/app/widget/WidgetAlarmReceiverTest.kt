package com.ivandsky.puzzlebox.app.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
class WidgetAlarmReceiverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val receiver = WidgetAlarmReceiver()
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

    private fun pendingResultFinished(): Boolean {
        val pending = checkNotNull(shadowOf(receiver).originalPendingResult) { "the receiver never called goAsync" }
        return shadowOf(pending).future.isDone
    }

    @Test
    fun `a broadcast refreshes the widgets once and finishes the pending result`() {
        broadcast()

        assertEquals(1, refreshes)
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

    private companion object {
        const val ACTION = "com.ivandsky.puzzlebox.test.WIDGET_ALARM"
    }
}
