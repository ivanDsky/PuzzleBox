package com.ivandsky.puzzlebox.app.widget

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorRes
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider

/**
 * Turns the widget's color resources into Glance providers that carry both the day and the night value, so the launcher picks
 * one by the phone's setting. Glance's own provider for a resource id is restricted to its library group, so each value is
 * read here, once from a day context and once from a night one; the widget follows the phone, not the app's Appearance row.
 */
internal class WidgetColors(context: Context) {
    private val day = context.withNight(Configuration.UI_MODE_NIGHT_NO)
    private val night = context.withNight(Configuration.UI_MODE_NIGHT_YES)

    fun of(@ColorRes id: Int): ColorProvider = ColorProvider(day = Color(day.getColor(id)), night = Color(night.getColor(id)))
}

private fun Context.withNight(night: Int): Context = createConfigurationContext(Configuration().apply { uiMode = night })
