package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.TangoDailyRepository
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.games.zip.ZipSession
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.time.LocalDate

/**
 * The surface every game's play state shares with the route shell (tango-05: the play
 * tab drives Zip OR Tango behind one seam; the screens stay game-specific where the
 * boards differ). The record surface is what the result route reads; [adoptRestored]
 * is the one session-restore path for saves AND history replays (A4 semantics shared).
 */
interface ActivePlay {
    val game: PlayGame

    /** A fresh entry must fetch: no live session, or the live one is finished. */
    val needsLoad: Boolean

    /** The A4 verdict for THIS play while the write is in flight/unknown: null. */
    val recordBadge: Boolean?
    val recordWriteFailed: Boolean

    /** Restore a session blob through this state's own codec (A19). */
    fun adoptRestored(bytes: ByteArray)

    /** Fresh-entry fetch (today's daily, degrade states first-class). */
    fun load()

    /** Result's "Play again": a fresh session of the SAME puzzle (A9). */
    fun replay()

    /** Retry after a failed record write (a new emitter lifetime). */
    fun retryRecordWrite()
}

/**
 * The play screen's clock seam (F2 §2: active foreground play only; the loop lives in
 * [PlayScreenKt.PlayClockEffects]). [clockWindow] is a per-window identity that changes
 * each time the running window (re)opens — null while paused/not started.
 */
interface PlayClock {
    val clockWindow: Any?
    fun tick()
    fun onBackground()
    fun onForeground()
    fun displayedElapsedMs(): Long
}

/**
 * Owner of the play flow's active [ActivePlay] (ticket 23). The state must outlive the
 * play entry's composition once the result route replaces it: the record write is
 * fire-and-observe (a navigation must not cancel it), the badge verdict lands on the
 * result screen, and "Play again" resets in place. A Koin single is the process
 * lifetime; [end] releases the scope on a mid-game exit.
 *
 * tango-05: the flow starts per GAME — the picker (A14) picks Zip or Tango and the
 * matching state holder owns the session. The session-changed callback receives the
 * session serialized through THAT game's codec and wrapped in a [SessionBlob]
 * envelope naming the game, so the route's saveable is self-describing: after process
 * death the saveable outlives this Koin single, and the envelope is what lets the
 * fresh flow know WHICH game to restart.
 */
class PlayFlow(
    private val zips: ZipDailyRepository,
    private val tangos: TangoDailyRepository,
    private val completions: CompletionRepository,
    private val zipToday: () -> LocalDate = {
        LocalDate.now((com.ivandsky.puzzlebox.games.zip.ZipDefinition.cadence as com.ivandsky.puzzlebox.game.Cadence.Daily).zone)
    },
    private val tangoToday: () -> LocalDate = {
        LocalDate.now((com.ivandsky.puzzlebox.games.tango.TangoDefinition.cadence as com.ivandsky.puzzlebox.game.Cadence.Daily).zone)
    },
) {
    private var flowScope: CoroutineScope? = null

    // Snapshot-backed: the result route composes BEFORE the flow may have started
    // (fresh entry) and must recompose when the state appears — a plain var would
    // strand the route on a stale null.
    var state: ActivePlay? by mutableStateOf(null)
        private set

    /** The flow's state for [game], created on first entry and reused until [end]. */
    fun start(game: PlayGame, onSessionChanged: (ByteArray?) -> Unit): ActivePlay {
        state?.let { return it }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        flowScope = scope
        return when (game) {
            PlayGame.Zip -> ZipPlayState(
                daily = zips,
                completions = completions,
                scope = scope,
                today = zipToday,
                onSessionChanged = { session ->
                    onSessionChanged(session?.let { SessionBlob.encode(game.id, ZipSessionCodec.serialize(it)) })
                },
            )

            PlayGame.Tango -> TangoPlayState(
                daily = tangos,
                completions = completions,
                scope = scope,
                today = tangoToday,
                onSessionChanged = { session ->
                    onSessionChanged(session?.let { SessionBlob.encode(game.id, TangoSessionCodec.serialize(it)) })
                },
            )
        }.also { state = it }
    }

    /**
     * The one session-restore path for saves AND history replays (both are
     * [SessionBlob] envelopes naming their game). A live same-game state adopts in
     * place; a stale other-game flow is released; and after PROCESS DEATH — the
     * saveable outlived this Koin single while [state] is null — the saved game's
     * flow is restarted here, so a mid-play day is never dropped (fix-round-1 C1).
     * An unparseable envelope names nothing: null, flow untouched (never guessed).
     */
    fun restore(envelope: SessionBlob.Decoded?, onSessionChanged: (ByteArray?) -> Unit): ActivePlay? {
        val game = envelope?.let { PlayGame.byId(it.game) } ?: return null
        if (state?.game != game) end() // stale other-game flow, or dead flow (no-op)
        return start(game, onSessionChanged).also { it.adoptRestored(envelope.session) }
    }

    /** Mid-game exit: the next play entry shows the picker again (A14), never resumes. */
    fun end() {
        flowScope?.cancel()
        flowScope = null
        state = null
    }
}
