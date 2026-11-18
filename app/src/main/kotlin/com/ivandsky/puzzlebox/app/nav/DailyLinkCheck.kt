package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.game.GameRegistry

/**
 * Whether [link] may open: only a [FriendLink.Daily] can fail it, when its game is not one this build ships or its day is
 * after the game's today (no puzzle yet). An unplayable daily link is dropped so the app opens on its usual screen: the
 * board would otherwise show an "unavailable" screen for it. Other kinds always open.
 */
internal fun FriendLink.isPlayable(games: GameRegistry, calendar: GameCalendar): Boolean {
    if (this !is FriendLink.Daily) return true
    val today = calendar.today(ref.gameId) ?: return false
    return games[ref.gameId] != null && !ref.day.isAfter(today)
}
