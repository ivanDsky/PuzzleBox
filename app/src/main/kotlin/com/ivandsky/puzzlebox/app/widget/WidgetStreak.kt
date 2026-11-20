package com.ivandsky.puzzlebox.app.widget

/** The widget's streak line: current and best run in days, and the freezes held ([freezesHeld] null shows no count). */
data class WidgetStreak(val days: Int, val best: Int, val freezesHeld: Int?)
