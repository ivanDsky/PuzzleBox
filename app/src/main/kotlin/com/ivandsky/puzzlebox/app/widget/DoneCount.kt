package com.ivandsky.puzzlebox.app.widget

import android.content.res.Resources
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.domain.TodayStatus

/** "5 of 5 done", only when the read is known: with every game offline there is no count to claim. */
internal fun doneCount(state: WidgetState, resources: Resources): String? {
    if (!state.known) return null
    val done = state.rings.count { it.status is TodayStatus.Solved }
    return resources.getString(R.string.widget_done_count, done, state.rings.size)
}
