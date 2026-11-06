package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.FetchError

/**
 * A past day's hand-off to the play route (ticket 14 → 13 contracts; tango-05 generalizes
 * it to any registered game): a self-describing [com.ivandsky.puzzlebox.game.SessionBlob]
 * envelope (ADR-0005) carrying THIS game's session bytes, whose puzzle rides BY VALUE and
 * whose ref is the day's identity — the play entry starts the named game's flow and
 * restores through its own codec, unchanged. A never-played day opens as a normal ranked
 * play; a fresh completion of an already-won day lands as practice with the day's
 * baseline, never a second record (A4, A31 — owned and tested in :feature:play:impl /
 * :core:data).
 */
sealed interface PastDayOutcome {
    /** Play-route arg: the play entry starts blob.game's flow and calls adoptRestored. */
    data class Ready(val session: ByteArray) : PastDayOutcome

    /** The day was never captured and its source has no archive, or the game is unregistered. */
    data object Missing : PastDayOutcome

    /** Before the source's archive starts or after the game's today. */
    data object OutOfRange : PastDayOutcome

    /** Fetching the uncached day failed; [error] names why (offline, not found, untrusted, throttled). */
    data class Failed(val error: FetchError) : PastDayOutcome

    /** The cached payload failed the engine decode — never playable (A16). */
    data class Unreadable(val error: DecodeError) : PastDayOutcome
}
