package com.ivandsky.puzzlebox.app.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The seam over turning the widget on and off: a disabled widget is not in the picker and a placed one goes away. */
fun interface WidgetComponentSwitch {
    fun setEnabled(enabled: Boolean)
}

/** The package manager's component setting for [PuzzleBoxWidgetReceiver]; `DONT_KILL_APP` keeps the running app alive. */
internal class AndroidWidgetComponentSwitch(private val context: Context) : WidgetComponentSwitch {

    override fun setEnabled(enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, PuzzleBoxWidgetReceiver::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
