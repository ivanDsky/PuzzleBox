package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.KeptSession
import com.ivandsky.puzzlebox.data.repository.KeptSessionRepository
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owner of the play feature's [ActivePlay] (ticket 23). The play must outlive the play
 * entry's composition once the result route replaces it: the record write is
 * fire-and-observe (a navigation must not cancel it), the badge verdict lands on the
 * result screen, and "Play again" resets in place. A Koin single is the process
 * lifetime; [leave] releases the play's scope on a mid-game exit.
 *
 * Kept sessions (ADR-0010): at most ONE play is live; every other game's session is
 * kept as bytes in [kept] (a [SessionBlob] envelope + its cadence day + the displayed
 * clock). The store writes on [leave], on [background] and when a live play of another
 * game is released; a solve discards. [pick] re-adopts a kept session of today through
 * the [restore] path before [active] publishes, and is the one owner of the day filter:
 * a kept session of another day is discarded and the game starts fresh.
 *
 * Game-agnostic (ADR-0001, ADR-0007): a game is playable when [games] ships its plugin
 * and [ui] its play registration; nothing here names a game. Every session change
 * reaches the entry's sink as a [SessionBlob] envelope naming the game, so the route's
 * saveable is self-describing: after process death the saveable outlives this single,
 * and the envelope is what tells the fresh store WHICH game to restart.
 */
class PlaySessionStore(
    private val games: GameRegistry,
    private val ui: GamePlayRegistry,
    private val daily: DailyRepository,
    private val completions: CompletionRepository,
    private val kept: KeptSessionRepository,
    private val today: (GameDefinition) -> LocalDate = { it.cadence.today() },
    private val scopeFactory: () -> CoroutineScope = { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) },
    /** Where kept-session writes run: never a play's scope, which [leave] cancels. */
    private val keepScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    private val _active = MutableStateFlow<ActivePlay?>(null)

    /** The live play, null = fresh entry (the picker). */
    val active: StateFlow<ActivePlay?> = _active.asStateFlow()

    // The one live play — set as soon as it is created, while [active] may publish it
    // only after a restore adopted (a resumed play is never exposed as needs-load).
    private var live: ActivePlay? = null
    private var scope: CoroutineScope? = null

    // The entry's session saver (rememberSaveable) and the newest blob it was handed.
    // A re-entered entry (rotation) brings a NEW saveable, so every start/restore REBINDS
    // the sink and backfills it with the live blob — otherwise the play keeps writing
    // into the dead first composition and a process death after a rotation loses the
    // session (queens fix round 4 R-4, shared by every game).
    private var sink: (ByteArray) -> Unit = {}
    private var latest: ByteArray? = null

    /**
     * The picker's pick: a session kept for [game]'s cadence today resumes through
     * [restore] (never fetches); a kept session of another day is discarded; otherwise
     * the game starts as [start] does and the route loads it.
     */
    suspend fun pick(game: GameId, onSessionChanged: (ByteArray) -> Unit): ActivePlay? {
        val plugin = games[game] ?: return null
        kept.kept.first()[game]?.let { session ->
            if (session.day == today(plugin.definition)) {
                val envelope = SessionBlob.decode(session.envelope)?.takeIf { it.game == game }
                restore(envelope, onSessionChanged)?.let { return it }
            }
            kept.discard(game)
        }
        return start(game, onSessionChanged)
    }

    /**
     * The play for [game], created on first entry and reused until [leave]. A live play
     * of ANOTHER game is kept and released first (F5); an unplayable id returns null
     * untouched. Never fetches — the route decides whether the play needs a load.
     */
    fun start(game: GameId, onSessionChanged: (ByteArray) -> Unit): ActivePlay? =
        open(game, onSessionChanged)?.also { _active.value = it }

    /**
     * The one session-restore path for saves, history replays and kept sessions (all
     * [SessionBlob] envelopes naming their game). A live same-game play adopts in
     * place — and keeps its controller when the envelope is the session it already
     * holds (rotation); any OTHER envelope replaces the live session, so that session
     * is kept first (a same-game history replay over today's play); a stale other-game
     * play is kept and released; and after PROCESS DEATH — the saveable outlived this
     * single while [active] is null — the saved game's play is restarted here, so a
     * mid-play day is never dropped (fix-round-1 C1). The play publishes only once
     * adopted. An unparseable or unplayable envelope names nothing: null, store untouched.
     */
    fun restore(envelope: SessionBlob.Decoded?, onSessionChanged: (ByteArray) -> Unit): ActivePlay? {
        envelope ?: return null
        val play = open(envelope.game, onSessionChanged) ?: return null
        if (!play.holds(envelope.session)) keepLive()
        play.adoptRestored(envelope.session)
        _active.value = play
        // The saveable was consumed on the way in: hand the adopted blob back so the
        // restored session survives the NEXT process death even before a move.
        publish(SessionBlob.encode(envelope.game, envelope.session))
        return play
    }

    /** Mid-game exit (back): the live play is kept, then released — the next play entry
     *  shows the picker. The leaving entry's sink is dropped FIRST, so the leave's own
     *  clock fold never writes into the dying entry's saveable (F6). */
    fun leave() {
        sink = {}
        keepLive()
        release()
    }

    /** The app went to the background (ON_STOP): keep the live play, which stays live. */
    fun background() = keepLive()

    /**
     * How to play opened over [play] (spec A11): its clock folds through the same hook a
     * background uses, and stays paused while the tutorial shows (the screen and its
     * clock loop are gone meanwhile). Nothing is kept and the session is untouched.
     */
    fun pauseClock(play: ActivePlay) {
        if (live === play) play.foldClock()
    }

    /** The tutorial over [play] closed: its clock reopens where it paused, as on foreground. */
    fun resumeClock(play: ActivePlay) {
        if (live === play) (play.controller as? PlayClock)?.onForeground()
    }

    /** A new play entry opened over [play], the result's FINISHED play (the Play tab after
     *  a solve): it is dropped so the entry shows the picker. Nothing is kept (a solve
     *  discards), and its scope is NOT cancelled — an in-flight record write must still
     *  land; every other job a play scope runs is finite. */
    fun dropFinished(play: ActivePlay) {
        if (live !== play || !play.controller.isComplete) return
        live = null
        scope = null
        latest = null
        sink = {}
        _active.value = null
    }

    private fun open(game: GameId, onSessionChanged: (ByteArray) -> Unit): ActivePlay? {
        val plugin = games[game] ?: return null
        val gameUi = ui[game] ?: return null
        live?.let { existing ->
            if (existing.gameId == game) {
                sink = onSessionChanged
                latest?.let(onSessionChanged)
                return existing
            }
            leave()
        }
        sink = onSessionChanged
        val playScope = scopeFactory()
        scope = playScope
        return ActivePlay(
            plugin = plugin,
            ui = gameUi,
            daily = daily,
            completions = completions,
            today = { today(plugin.definition) },
            scope = playScope,
            publish = { play, bytes -> if (live === play) publish(SessionBlob.encode(game, bytes)) },
            onSolved = { play -> if (live === play) keepScope.launch { kept.discard(game) } },
        ).also { live = it }
    }

    /**
     * Keeps the live play when it holds an unfinished session of its cadence today: the
     * clock folds first (its own background hook — idempotent with the chrome's ON_PAUSE
     * fold), so the kept bytes and elapsed include the running window. A session never
     * started (no clock time, nothing published) and a replay of another day keep nothing.
     */
    private fun keepLive() {
        val play = live ?: return
        if (play.needsLoad) return
        val day = play.todayDay ?: return
        val elapsedMs = play.foldClock()
        if (elapsedMs == 0L) return
        val envelope = latest ?: return
        val session = KeptSession(play.gameId, day, elapsedMs ?: 0L, envelope)
        keepScope.launch { kept.keep(session) }
    }

    private fun release() {
        scope?.cancel()
        scope = null
        live = null
        latest = null
        sink = {}
        _active.value = null
    }

    private fun publish(blob: ByteArray) {
        latest = blob
        sink(blob)
    }
}
