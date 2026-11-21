package com.ivandsky.puzzlebox.app.widget

import java.time.LocalDate

/** The last picture the widget drew from a good read, and the puzzle [day] it was read on. */
data class WidgetSnapshot(val day: LocalDate, val state: WidgetState)
