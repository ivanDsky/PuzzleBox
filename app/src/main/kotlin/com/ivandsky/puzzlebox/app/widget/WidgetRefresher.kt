package com.ivandsky.puzzlebox.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/** The only way anything asks for a redraw: every placed instance of the widget draws again from today's data. */
fun interface WidgetRefresher {
    suspend fun refreshAll()
}

/** The only caller of Glance's `updateAll`. */
internal class AndroidWidgetRefresher(
    private val context: Context,
    private val widget: PuzzleBoxWidget,
) : WidgetRefresher {

    override suspend fun refreshAll() = widget.updateAll(context)
}
