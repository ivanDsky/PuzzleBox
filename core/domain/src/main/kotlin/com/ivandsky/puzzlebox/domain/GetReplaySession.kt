package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * The replay hand-off to the play route (ticket 14 → 13 contracts; tango-05 generalizes
 * it to any registered game): a self-describing [com.ivandsky.puzzlebox.game.SessionBlob]
 * envelope (ADR-0005) carrying THIS game's session bytes, whose puzzle rides BY VALUE and
 * whose ref is the day's identity — the play entry starts the named game's flow and
 * restores through its own codec, unchanged. A restored completed day shows its win
 * without emitting; a fresh completion of an already-won day lands as a replay row with
 * the day's baseline, never a second record (A4 — owned and tested in :feature:play:impl /
 * :core:data).
 */
sealed interface ReplayOutcome {
    /** Play-route arg: the play entry starts blob.game's flow and calls adoptRestored. */
    data class Ready(val session: ByteArray) : ReplayOutcome

    /** The day is no longer in the cache (stale list), or the game is unregistered — nothing to replay. */
    data object Missing : ReplayOutcome

    /** The cached payload failed the engine decode — never playable (A16). */
    data class Unreadable(val error: DecodeError) : ReplayOutcome
}

/**
 * Builds the replay launch for a day from its cache-only pieces: the day's cached
 * payload and its puzzleNumber identity (ADR-0004). The game's own plugin
 * ([com.ivandsky.puzzlebox.game.GamePlugin.replaySession]) decodes and wraps it, so the
 * envelope names the game without any nav-shape change (ADR-0005).
 */
class GetReplaySession(
    private val daily: DailyRepository,
    private val registry: GameRegistry,
) {

    suspend operator fun invoke(game: GameId, day: LocalDate, puzzleNumber: Int?): ReplayOutcome {
        val cached = daily.cached(game, day) ?: return ReplayOutcome.Missing
        // A game no registration authored is never replayed.
        val plugin = registry[game] ?: return ReplayOutcome.Missing
        return when (val session = plugin.replaySession(cached, day, puzzleNumber)) {
            is DecodeResult.Ok -> ReplayOutcome.Ready(session.value)
            is DecodeResult.Err -> ReplayOutcome.Unreadable(session.error)
        }
    }
}
