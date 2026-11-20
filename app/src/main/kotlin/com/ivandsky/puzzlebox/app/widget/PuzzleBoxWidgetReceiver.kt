package com.ivandsky.puzzlebox.app.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/** Glance's receiver for [PuzzleBoxWidget], the manifest's entry point; the widget itself comes from the Koin graph. */
class PuzzleBoxWidgetReceiver : GlanceAppWidgetReceiver(), KoinComponent {

    override val glanceAppWidget: PuzzleBoxWidget
        get() = get()
}
