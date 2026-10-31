package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.data.repository.ZipDailyEntry
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec

/**
 * The replay hand-off to the play route (ticket 14 → 13 contracts): a self-describing
 * Zip session blob (A19) whose puzzle rides BY VALUE and whose ref is THIS day's
 * identity, so the play screen restores it through its existing process-death restore
 * path — [com.ivandsky.puzzlebox.play.ZipPlayState.adoptRestored] — unchanged. A
 * restored completed day shows its win without emitting; a fresh completion of an
 * already-won day lands as a replay row with the day's baseline, never a second
 * record (A4 — owned and tested in :feature:play / :core:data).
 */
sealed interface ReplayLaunch {
    /** Play-route arg: the play entry calls adoptRestored(session) with these bytes. */
    data class Ready(val session: ByteArray) : ReplayLaunch

    /** The day is no longer in the cache (stale list) — nothing to replay. */
    data object Missing : ReplayLaunch

    /** The cached payload failed the engine decode — never playable (A16). */
    data class Unreadable(val error: DecodeError) : ReplayLaunch
}

/**
 * Builds the replay launch from the day's cache-only pieces: the payload [Puzzle] and
 * the history row that carries the day's puzzleNumber identity (ADR-0004).
 */
fun replayLaunch(cached: Puzzle?, entry: ZipDailyEntry?): ReplayLaunch =
    when {
        cached == null || entry == null -> ReplayLaunch.Missing
        else -> when (val decoded = ZipCodec.decode(cached.payload)) {
            is DecodeResult.Ok -> ReplayLaunch.Ready(
                session = ZipSessionCodec.serialize(
                    ZipSession(
                        ref = ZipPuzzleRef(date = entry.day, puzzleNumber = entry.puzzleNumber),
                        puzzle = decoded.value,
                    ),
                ),
            )
            is DecodeResult.Err -> ReplayLaunch.Unreadable(decoded.error)
        }
    }
