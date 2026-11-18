package com.ivandsky.puzzlebox.app.reminder

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Starts the [runner] with the app and reconciles it each time an activity starts, so allowing notifications in system
 * settings, a time-zone change, a restart or a force-stop is corrected the next time the app opens. No debounce: a
 * reconcile is cheap and replaces the one pending alarm. Disabled runner, nothing registered.
 */
class ReminderAppHooks(
    private val application: Application,
    private val runner: ReminderRunner,
) {

    fun start() {
        if (!runner.enabled) return
        runner.start()
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) = runner.refresh()
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
