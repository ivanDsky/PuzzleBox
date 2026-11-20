package com.ivandsky.puzzlebox.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * The widget's own wake-up, not exported: redraws every placed widget and keeps the process alive until that finishes.
 * Nothing sends it yet; the midnight alarm that does, and its re-arm from here, come with ticket 07.
 */
class WidgetAlarmReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        get<CoroutineScope>().launch {
            try {
                get<WidgetRefresher>().refreshAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A failed redraw must not end the process from a receiver; the next wake-up draws again.
                Log.w(TAG, "widgets not refreshed: ${e.message}")
            } finally {
                pending?.finish()
            }
        }
    }

    private companion object {
        const val TAG = "WidgetAlarmReceiver"
    }
}
