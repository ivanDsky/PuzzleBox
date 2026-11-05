package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.game.FetchError

/** Shown when a puzzle arrived but can't be played: it failed the checks or couldn't be decoded (A16). */
const val BROKEN_PUZZLE_MESSAGE: String = "This puzzle didn't load right. Try again later."

/** Copy for the first-class degrade states. */
fun unavailableMessage(error: FetchError): String = when (error) {
    is FetchError.Unavailable -> "Can't load today's puzzle. Check your connection and try again."
    is FetchError.Throttled -> "Can't load today's puzzle right now. Wait a minute and try again."
    is FetchError.Untrusted -> BROKEN_PUZZLE_MESSAGE
}

/** Banner copy when a captured day is played because the fetch failed (A7/A8). */
fun degradeBannerMessage(error: FetchError): String = when (error) {
    is FetchError.Unavailable -> "You're offline. Playing today's saved puzzle."
    is FetchError.Throttled -> "Can't load a new copy right now. Playing today's saved puzzle."
    is FetchError.Untrusted -> "The new copy didn't load right. Playing today's saved puzzle."
}
