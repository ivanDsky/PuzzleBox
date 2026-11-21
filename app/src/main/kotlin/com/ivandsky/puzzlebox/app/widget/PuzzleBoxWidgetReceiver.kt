package com.ivandsky.puzzlebox.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Glance's receiver for [PuzzleBoxWidget], the manifest's entry point; the widget itself comes from the Koin graph. It also
 * keeps the rollover wake-up: armed when a widget is added or updated and after a reboot or an app update, cancelled with
 * the last widget. Any other broadcast is left to Glance.
 */
class PuzzleBoxWidgetReceiver : GlanceAppWidgetReceiver(), KoinComponent {

    override val glanceAppWidget: PuzzleBoxWidget
        get() = get()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            get<ArmWidgetRollover>()()
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        get<ArmWidgetRollover>()()
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        get<ArmWidgetRollover>()()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        get<WidgetRolloverAlarm>().cancel()
    }
}
