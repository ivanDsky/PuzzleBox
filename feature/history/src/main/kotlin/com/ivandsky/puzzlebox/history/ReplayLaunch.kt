package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzleRef
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipPuzzleRef
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import java.time.LocalDate

/**
 * The replay hand-off to the play route (ticket 14 → 13 contracts; tango-05 generalizes
 * it to any registered game): a self-describing [SessionBlob] envelope (ADR-0005)
 * carrying THIS game's session bytes, whose puzzle rides BY VALUE and whose ref is the
 * day's identity — the play entry starts the named game's flow and restores through its
 * own codec, unchanged. A restored completed day shows its win without emitting; a
 * fresh completion of an already-won day lands as a replay row with the day's baseline,
 * never a second record (A4 — owned and tested in :feature:play / :core:data).
 */
sealed interface ReplayLaunch {
    /** Play-route arg: the play entry starts blob.game's flow and calls adoptRestored. */
    data class Ready(val session: ByteArray) : ReplayLaunch

    /** The day is no longer in the cache (stale list), or the game is unregistered — nothing to replay. */
    data object Missing : ReplayLaunch

    /** The cached payload failed the engine decode — never playable (A16). */
    data class Unreadable(val error: DecodeError) : ReplayLaunch
}

/**
 * Builds the replay launch for a day's cache-only pieces: the game [game], the payload
 * [Puzzle], and the day's puzzleNumber identity (ADR-0004). The envelope names the game
 * so the play route dispatches without any nav-shape change (ADR-0005).
 */
fun replayLaunch(
    game: GameId,
    day: LocalDate,
    cached: Puzzle?,
    puzzleNumber: Int?,
): ReplayLaunch {
    if (cached == null) return ReplayLaunch.Missing
    return when (game.value) {
        ZipDefinition.id.value -> zipLaunch(day, puzzleNumber, cached)
        TangoDefinition.id.value -> tangoLaunch(day, puzzleNumber, cached)
        QueensDefinition.id.value -> queensLaunch(day, puzzleNumber, cached)
        else -> ReplayLaunch.Missing // a game no registration authored is never replayed
    }
}

/** Zip branch (ticket-14 semantics, unchanged — now wrapped in the game-named envelope). */
private fun zipLaunch(day: LocalDate, puzzleNumber: Int?, cached: Puzzle): ReplayLaunch =
    when (val decoded = ZipCodec.decode(cached.payload)) {
        is DecodeResult.Ok -> ReplayLaunch.Ready(
            session = SessionBlob.encode(
                ZipDefinition.id,
                ZipSessionCodec.serialize(
                    ZipSession(
                        ref = ZipPuzzleRef(date = day, puzzleNumber = puzzleNumber),
                        puzzle = decoded.value,
                    ),
                ),
            ),
        )

        is DecodeResult.Err -> ReplayLaunch.Unreadable(decoded.error)
    }

/**
 * Tango branch: same shape, plus the A15 gate — an archived puzzle that decodes but is
 * not uniquely solvable is never playable (payload untrusted), exactly as the play
 * state refuses it on a fresh load.
 */
private fun tangoLaunch(day: LocalDate, puzzleNumber: Int?, cached: Puzzle): ReplayLaunch =
    when (val decoded = TangoCodec.decode(cached.payload)) {
        is DecodeResult.Ok ->
            if (!TangoUniqueness.isUniquelySolvable(decoded.value)) {
                ReplayLaunch.Unreadable(DecodeError.Malformed("archived puzzle is not uniquely solvable"))
            } else {
                ReplayLaunch.Ready(
                    session = SessionBlob.encode(
                        TangoDefinition.id,
                        TangoSessionCodec.serialize(
                            TangoSession(
                                ref = TangoPuzzleRef(date = day, puzzleNumber = puzzleNumber),
                                puzzle = decoded.value,
                            ),
                        ),
                    ),
                )
            }

        is DecodeResult.Err -> ReplayLaunch.Unreadable(decoded.error)
    }

/** Queens branch: same shape as Zip — the play state trusts any payload the codec decodes. */
private fun queensLaunch(day: LocalDate, puzzleNumber: Int?, cached: Puzzle): ReplayLaunch =
    when (val decoded = QueensCodec.decode(cached.payload)) {
        is DecodeResult.Ok -> ReplayLaunch.Ready(
            session = SessionBlob.encode(
                QueensDefinition.id,
                QueensSessionCodec.serialize(
                    QueensSession(
                        ref = QueensPuzzleRef(date = day, puzzleNumber = puzzleNumber),
                        puzzle = decoded.value,
                    ),
                ),
            ),
        )

        is DecodeResult.Err -> ReplayLaunch.Unreadable(decoded.error)
    }
