package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId

/** One game's ring on the home-screen widget: its id, name and Today's [status]. */
data class WidgetRing(val game: GameId, val displayName: String, val status: TodayStatus)
