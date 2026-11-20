package com.ivandsky.puzzlebox.app.widget

import android.content.Context
import com.ivandsky.puzzlebox.app.R
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget

/**
 * What the widget's button says and asks for. It never names a game in the request: the app picks one when the button is
 * tapped, so a widget that is hours stale still opens what is up next now.
 */
internal data class UpNextButton(val label: String, val target: ShortcutTarget)

internal fun upNextButton(upNext: WidgetUpNext, context: Context): UpNextButton = when (upNext) {
    is WidgetUpNext.Play -> UpNextButton(context.getString(R.string.widget_up_next_game, upNext.gameName), ShortcutTarget.UpNext)
    WidgetUpNext.Done -> UpNextButton(context.getString(R.string.widget_all_done), ShortcutTarget.Today)
    WidgetUpNext.Unknown -> UpNextButton(context.getString(R.string.widget_up_next), ShortcutTarget.UpNext)
}
