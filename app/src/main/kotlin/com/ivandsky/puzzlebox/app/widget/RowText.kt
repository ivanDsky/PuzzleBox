package com.ivandsky.puzzlebox.app.widget

import android.content.res.Resources
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.TodayStatus

/**
 * What a list row says besides its game's name: the [status] line (the [solved] time, in the strong color, or where the
 * game stands), the [rank] ("#3 of 41", a dash, or nothing; the dash is [rankMuted]) and the [best] time. Each is empty when
 * the row has nothing to say, so a row keeps its layout.
 */
internal data class RowText(val status: String, val solved: Boolean, val rank: String, val rankMuted: Boolean, val best: String)

/**
 * A rank belongs to a solved game only: with a place it reads "#3 of 41", with none (not known, hidden, timed out) a dash,
 * signed out nothing. The best time shows for every status, since it is local and the board is not.
 */
internal fun rowText(ring: WidgetRing, resources: Resources): RowText {
    val best = ring.bestMs?.let { resources.getString(R.string.widget_best, formatDuration(it)) }.orEmpty()
    return when (val status = ring.status) {
        is TodayStatus.Solved -> {
            val (rank, muted) = when (val place = ring.rank) {
                is WidgetRank.Ranked -> resources.getString(R.string.widget_rank, place.position, place.count) to false
                WidgetRank.Missing -> resources.getString(R.string.widget_rank_none) to true
                WidgetRank.SignedOut -> "" to false
            }
            RowText(formatDuration(status.timeMs), solved = true, rank = rank, rankMuted = muted, best = best)
        }
        is TodayStatus.InProgress ->
            RowText(resources.getString(R.string.widget_in_progress, formatDuration(status.elapsedMs)), false, "", false, best)
        TodayStatus.NotStarted -> RowText(resources.getString(R.string.widget_not_started), false, "", false, best)
        TodayStatus.Offline -> RowText(resources.getString(R.string.widget_chip_retry), false, "", false, best)
    }
}
