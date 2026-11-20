package com.ivandsky.puzzlebox.app.widget

import android.content.Context
import android.text.format.DateFormat
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget
import com.ivandsky.puzzlebox.domain.TodayStatus
import java.time.Instant
import java.util.Date

/**
 * What the widget's pill says and asks for. It never names a game in the request: the app picks one when the pill is
 * tapped, so a widget that is hours stale still opens what is up next now. A [tonal] pill is the quiet one: nothing is left
 * to play.
 */
internal data class UpNextButton(val label: String, val target: ShortcutTarget, val tonal: Boolean)

/**
 * "Play Queens" or "Continue Queens" (the game's play is saved), "New puzzles at 9:00 AM" when everything is solved, and
 * "Open PuzzleBox" when nothing is known: the read failed, every game is offline, or Today is empty. A clock time, not a
 * countdown: the widget cannot tick, so a countdown would be wrong between draws. [formatTime] writes the rollover as a local
 * short time.
 */
internal fun upNextButton(
    state: WidgetState?,
    context: Context,
    formatTime: (Instant) -> String = { deviceTime(context, it) },
): UpNextButton {
    val open = UpNextButton(context.getString(R.string.widget_open), ShortcutTarget.UpNext, tonal = false)
    if (state == null || !state.known) return open
    return when (val upNext = state.upNext) {
        is WidgetUpNext.Play -> {
            val playing = state.rings.any { it.game == upNext.game && it.status is TodayStatus.InProgress }
            val words = if (playing) R.string.widget_continue_game else R.string.widget_play_game
            UpNextButton(context.getString(words, upNext.gameName), ShortcutTarget.UpNext, tonal = false)
        }
        WidgetUpNext.Done -> state.nextRollover
            ?.let { UpNextButton(context.getString(R.string.widget_new_puzzles_at, formatTime(it)), ShortcutTarget.Today, tonal = true) }
            ?: open.copy(target = ShortcutTarget.Today)
        WidgetUpNext.Unknown -> open
    }
}

/** [at] as the device's short local time, in its 12 or 24 hour setting. */
internal fun deviceTime(context: Context, at: Instant): String = DateFormat.getTimeFormat(context).format(Date.from(at))
