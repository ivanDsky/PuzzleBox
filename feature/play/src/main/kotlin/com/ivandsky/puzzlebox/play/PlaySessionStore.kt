package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.game.GameDefinition
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.today
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owner of the play feature's [ActivePlay] (ticket 23). The play must outlive the play
 * entry's composition once the result route replaces it: the record write is
 * fire-and-observe (a navigation must not cancel it), the badge verdict lands on the
 * result screen, and "Play again" resets in place. A Koin single is the process
 * lifetime; [end] releases the play's scope on a mid-game exit.
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
    private val today: (GameDefinition) -> LocalDate = { it.cadence.today() },
    private val scopeFactory: () -> CoroutineScope = { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) },
) {
    /** The picker's entries (A14): every game with both a plugin and a play UI, in
     *  [GameRegistry.all] order, with its display name. */
    val playable: List<Pair<GameId, String>> =
        games.all.filter { ui[it.definition.id] != null }.map { it.definition.id to it.definition.displayName }

    private val _active = MutableStateFlow<ActivePlay?>(null)

    /** The live play, null = fresh entry (the picker). */
    val active: StateFlow<ActivePlay?> = _active.asStateFlow()

    private var scope: CoroutineScope? = null

    // The entry's session saver (rememberSaveable) and the newest blob it was handed.
    // A re-entered entry (rotation) brings a NEW saveable, so every start/restore REBINDS
    // the sink and backfills it with the live blob — otherwise the play keeps writing
    // into the dead first composition and a process death after a rotation loses the
    // session (queens fix round 4 R-4, shared by every game).
    private var sink: (ByteArray) -> Unit = {}
    private var latest: ByteArray? = null

    /**
     * The play for [game], created on first entry and reused until [end]. A live play
     * of ANOTHER game is released first (F5); an unplayable id returns null untouched.
     * Never fetches — the route decides whether the play needs a load.
     */
    fun start(game: GameId, onSessionChanged: (ByteArray) -> Unit): ActivePlay? {
        val plugin = games[game] ?: return null
        val gameUi = ui[game] ?: return null
        sink = onSessionChanged
        _active.value?.let { existing ->
            if (existing.gameId == game) {
                latest?.let(onSessionChanged)
                return existing
            }
            end()
            sink = onSessionChanged
        }
        val playScope = scopeFactory()
        scope = playScope
        return ActivePlay(
            plugin = plugin,
            ui = gameUi,
            daily = daily,
            completions = completions,
            today = { today(plugin.definition) },
            scope = playScope,
            publish = { play, bytes -> if (_active.value === play) publish(SessionBlob.encode(game, bytes)) },
        ).also { _active.value = it }
    }

    /**
     * The one session-restore path for saves AND history replays (both are
     * [SessionBlob] envelopes naming their game). A live same-game play adopts in
     * place; a stale other-game play is released; and after PROCESS DEATH — the
     * saveable outlived this single while [active] is null — the saved game's play is
     * restarted here, so a mid-play day is never dropped (fix-round-1 C1). An
     * unparseable or unplayable envelope names nothing: null, store untouched.
     */
    fun restore(envelope: SessionBlob.Decoded?, onSessionChanged: (ByteArray) -> Unit): ActivePlay? {
        envelope ?: return null
        val play = start(envelope.game, onSessionChanged) ?: return null
        play.adoptRestored(envelope.session)
        // The saveable was consumed on the way in: hand the adopted blob back so the
        // restored session survives the NEXT process death even before a move.
        publish(SessionBlob.encode(envelope.game, envelope.session))
        return play
    }

    /** Mid-game exit: the next play entry shows the picker again (A14), never resumes.
     *  The dead entry's sink is dropped with the play (F6). */
    fun end() {
        scope?.cancel()
        scope = null
        latest = null
        sink = {}
        _active.value = null
    }

    private fun publish(blob: ByteArray) {
        latest = blob
        sink(blob)
    }
}
