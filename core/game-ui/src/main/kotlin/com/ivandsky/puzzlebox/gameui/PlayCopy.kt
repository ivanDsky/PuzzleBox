package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.FetchError

/** Copy for the first-class degrade states. */
fun unavailableMessage(error: FetchError): String = when (error) {
    is FetchError.Unavailable -> "Couldn't reach LinkedIn, and no copy of today's puzzle is saved yet. Check your connection and try again."
    is FetchError.Throttled -> "LinkedIn is rate-limiting requests right now. Give it a minute and try again."
    is FetchError.Untrusted -> "Today's puzzle failed verification, so it can't be played. Try again later."
}

/** Banner copy when a captured day is played because the fetch failed (A7/A8). */
fun degradeBannerMessage(error: FetchError): String = when (error) {
    is FetchError.Unavailable -> "Offline — playing today's saved puzzle."
    is FetchError.Throttled -> "LinkedIn is rate-limiting — playing today's saved puzzle."
    is FetchError.Untrusted -> "Fresh puzzle was rejected — playing the saved copy."
}

/** Copy for a fetched payload the game's codec cannot decode (A16: never playable). */
fun untrustedDecodeMessage(error: DecodeError): String =
    "The saved puzzle couldn't be read (${error::class.simpleName}). Try refreshing."
