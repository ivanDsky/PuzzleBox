package com.ivandsky.puzzlebox.app.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The seam over turning the widget on: the manifest ships the receiver disabled, so it is not in the picker until the app enables it. */
fun interface WidgetComponentSwitch {
    fun enable()
}

/** The package manager's component setting for [PuzzleBoxWidgetReceiver]; `DONT_KILL_APP` keeps the running app alive. */
internal class AndroidWidgetComponentSwitch(private val context: Context) : WidgetComponentSwitch {

    override fun enable() {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, PuzzleBoxWidgetReceiver::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
