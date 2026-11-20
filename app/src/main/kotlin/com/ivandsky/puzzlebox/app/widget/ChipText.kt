package com.ivandsky.puzzlebox.app.widget

import android.content.res.Resources
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.TodayStatus

/**
 * What a chip says: the [first] line and, in the two-line chip, the [second]. Both are empty for a game with nothing to
 * say, so a chip keeps its height.
 */
internal data class ChipText(val first: String, val second: String)

/**
 * A solved game with a rank reads "#3" over its time; with none (not known, hidden, signed out) it reads its time alone.
 * A chip never shows a dash: that is a row's. A game in progress reads its elapsed time, one not started "New", an offline
 * one "Retry".
 */
internal fun chipText(ring: WidgetRing, resources: Resources): ChipText = when (val status = ring.status) {
    is TodayStatus.Solved -> {
        val time = formatDuration(status.timeMs)
        when (val rank = ring.rank) {
            is WidgetRank.Ranked -> ChipText(resources.getString(R.string.widget_chip_rank, rank.position), time)
            WidgetRank.Missing, WidgetRank.SignedOut -> ChipText(time, "")
        }
    }
    is TodayStatus.InProgress -> ChipText(formatDuration(status.elapsedMs), "")
    TodayStatus.NotStarted -> ChipText(resources.getString(R.string.widget_chip_new), "")
    TodayStatus.Offline -> ChipText(resources.getString(R.string.widget_chip_retry), "")
}

/** A chip's spoken label: "Zip, solved, 1:12, rank 3 of 41", or without the rank, or "Queens, in progress, 3:12", or just the status. */
internal fun ringDescription(ring: WidgetRing, resources: Resources): String {
    val status = ring.status
    val words = resources.getString(
        when (status) {
            is TodayStatus.Solved -> R.string.widget_ring_solved
            is TodayStatus.InProgress -> R.string.widget_ring_in_progress
            TodayStatus.NotStarted -> R.string.widget_ring_not_started
            TodayStatus.Offline -> R.string.widget_ring_offline
        },
    )
    val name = ring.displayName
    return when (status) {
        is TodayStatus.Solved -> {
            val time = formatDuration(status.timeMs)
            when (val rank = ring.rank) {
                is WidgetRank.Ranked ->
                    resources.getString(R.string.widget_ring_description_rank, name, words, time, rank.position, rank.count)
                WidgetRank.Missing, WidgetRank.SignedOut -> resources.getString(R.string.widget_ring_description_time, name, words, time)
            }
        }
        is TodayStatus.InProgress ->
            resources.getString(R.string.widget_ring_description_time, name, words, formatDuration(status.elapsedMs))
        TodayStatus.NotStarted, TodayStatus.Offline -> resources.getString(R.string.widget_ring_description, name, words)
    }
}
