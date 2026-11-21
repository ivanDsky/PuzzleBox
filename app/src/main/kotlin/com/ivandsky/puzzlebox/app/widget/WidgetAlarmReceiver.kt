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
 * The widget's own wake-up, not exported, sent by the rollover alarm: redraws every placed widget, arms the next rollover,
 * and keeps the process alive until both are done.
 */
class WidgetAlarmReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        get<CoroutineScope>().launch {
            try {
                refresh()
                // After the redraw, so a failed redraw still leaves the next rollover armed.
                get<ArmWidgetRollover>()()
            } finally {
                pending?.finish()
            }
        }
    }

    private suspend fun refresh() {
        try {
            get<WidgetRefresher>().refreshAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed redraw must not end the process from a receiver; the next wake-up draws again.
            Log.w(TAG, "widgets not refreshed: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "WidgetAlarmReceiver"
    }
}
