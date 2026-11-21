package com.ivandsky.puzzlebox.app.widget

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * The widget's app-start work. It enables the receiver once per start (the manifest ships it disabled), redraws the
 * widget when [changes] emits (a burst within one second is one redraw) and each time an activity starts, so opening the app
 * never shows a stale widget behind it, and arms the rollover wake-up at the start and at each activity start. The widget is
 * a convenience: a failed enable, redraw or arm is logged, never a reason to fail the start or stop the collection.
 */
class WidgetAppHooks(
    private val componentSwitch: WidgetComponentSwitch,
    private val changes: Flow<Unit>,
    private val refresher: WidgetRefresher,
    private val armRollover: ArmWidgetRollover,
    private val application: Application,
    private val scope: CoroutineScope,
) {

    fun start() {
        scope.launch { enableComponent() }
        scope.launch { armRollover() }
        scope.launch { collectChanges() }
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    scope.launch { refresh() }
                    scope.launch { armRollover() }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    private fun enableComponent() {
        try {
            componentSwitch.enable()
        } catch (e: Exception) {
            Log.w(TAG, "widget component not enabled: ${e.message}")
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun collectChanges() {
        try {
            changes.debounce(DEBOUNCE_MS).collect { refresh() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A throwing read of the sources ends their flow; the next activity start still redraws.
            Log.w(TAG, "widget changes not followed: ${e.message}")
        }
    }

    private suspend fun refresh() {
        try {
            refresher.refreshAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "widgets not refreshed: ${e.message}")
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 1_000L
        const val TAG = "WidgetAppHooks"
    }
}
