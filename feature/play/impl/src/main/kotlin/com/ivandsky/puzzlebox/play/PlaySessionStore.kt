package com.ivandsky.puzzlebox.play

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.GameKey
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Owner of the play feature's live plays: one [ActivePlay] per [PlaySlot] (A9, N21), so
 * every stack that shows a game's day shares its board. A play outlives its entry's
 * composition: the record write is fire-and-observe (a navigation must not cancel it),
 * the badge lands on the result screen, and "Play again" resets in place. A Koin single
 * is the process lifetime; [retain] releases what no back stack names (fold → flush →
 * cancel) and [flush] writes every pending save at app stop (A10).
 *
 * Confined to [DispatcherProvider.main] (immediate where it has one): the plays, their
 * [SlotWriter]s and the release/flush jobs all run on that one thread.
 *
 * Game-agnostic (ADR-0001, ADR-0007): a game is playable when [games] ships its plugin
 * and [ui] its play registration; nothing here names a game.
 *
 * Until 14, the legacy single-[active] API rides beside the slots: [start]/[restore]/[end]
 * hold one play that never touches disk, and every session change reaches the entry's
 * sink as a [SessionBlob] envelope naming the game, so the route's saveable is
 * self-describing after process death.
 */
class PlaySessionStore internal constructor(
    private val games: GameRegistry,
    private val ui: GamePlayRegistry,
    private val opening: SlotOpening,
    private val writers: (PlaySlot) -> SlotWriter,
    private val completions: CompletionRepository,
    private val calendar: GameCalendar,
    dispatchers: DispatcherProvider,
) {
    /** The picker's entries (A14): every game with both a plugin and a play UI, in
     *  [GameRegistry.all] order, with its display name. */
    val playable: List<Pair<GameId, String>> =
        games.all.filter { ui[it.definition.id] != null }.map { it.definition.id to it.definition.displayName }

    /** The store's lifetime: releases, flushes and record writes run here; each play's
     *  own work runs in a child scope cancelled on release. */
    private val scope = CoroutineScope(
        SupervisorJob() + ((dispatchers.main as? MainCoroutineDispatcher)?.immediate ?: dispatchers.main),
    )

    private val slots = LinkedHashMap<PlaySlot, ActivePlay>()

    /** The slot each null-day key resolved to on its first [open] — today, pinned. */
    private val pinned = HashMap<GameKey, PlaySlot>()

    /**
     * The live play of [key]'s slot (N21), created through [SlotOpening] on first open
     * (saved row → today's fetch → a past day's build, A11). A null day resolves to the
     * game's today once and stays pinned while the key is retained. A complete live play
     * is reset to a fresh attempt (Play again). Null when the game is unplayable.
     */
    fun open(key: GameKey): ActivePlay? {
        val game = GameId(key.gameId)
        val plugin = games[game] ?: return null
        val gameUi = ui[game] ?: return null
        val slot = slotOf(key, game) ?: return null
        slots[slot]?.let { live ->
            if (live.controller.isComplete) live.replay()
            return live
        }
        return ActivePlay(
            slot = slot,
            plugin = plugin,
            ui = gameUi,
            opening = opening,
            writer = writers(slot),
            completions = completions,
            scope = playScope(),
            recordScope = scope,
            publish = { _, _ -> },
        ).also {
            slots[slot] = it
            it.load()
        }
    }

    /** The live play of [slot]; never creates one. */
    fun peek(slot: PlaySlot): ActivePlay? = slots[slot]

    /**
     * Releases every live slot that no key in [keys] names: a [GameKey] with a day names
     * that day, a null-day one the slot it pinned. A released play folds its clock and
     * queues its final save before this returns, so a reopen's read is ordered after it.
     */
    fun retain(keys: Collection<NavKey>) {
        val gameKeys = keys.filterIsInstance<GameKey>().toSet()
        pinned.keys.retainAll(gameKeys)
        val named = gameKeys.mapNotNullTo(HashSet()) { key ->
            if (key.day == null) pinned[key] else PlaySlot(GameId(key.gameId), LocalDate.parse(key.day))
        }
        val released = slots.filterKeys { it !in named }.values.toList()
        released.forEach { play ->
            slots.remove(play.slot)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { play.close() }
        }
    }

    /** Folds every live play's clock and writes every pending save; the job ends when all are on disk. */
    fun flush(): Job {
        val saves = slots.values.map { play -> scope.launch(start = CoroutineStart.UNDISPATCHED) { play.save() } }
        return scope.launch(start = CoroutineStart.UNDISPATCHED) { saves.joinAll() }
    }

    private fun slotOf(key: GameKey, game: GameId): PlaySlot? {
        key.day?.let { return PlaySlot(game, LocalDate.parse(it)) }
        pinned[key]?.let { return it }
        val today = calendar.today(game) ?: return null
        return PlaySlot(game, today).also { pinned[key] = it }
    }

    private fun playScope(): CoroutineScope = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))

    //region legacy single-active API (deleted in 14)

    private val _active = MutableStateFlow<ActivePlay?>(null)

    /** The live play, null = fresh entry (the picker). */
    val active: StateFlow<ActivePlay?> = _active.asStateFlow()

    private var legacyScope: CoroutineScope? = null

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
        val today = calendar.today(game) ?: return null
        sink = onSessionChanged
        _active.value?.let { existing ->
            if (existing.gameId == game) {
                latest?.let(onSessionChanged)
                return existing
            }
            end()
            sink = onSessionChanged
        }
        val playScope = playScope()
        legacyScope = playScope
        return ActivePlay(
            slot = PlaySlot(game, today),
            plugin = plugin,
            ui = gameUi,
            opening = opening,
            writer = null,
            completions = completions,
            scope = playScope,
            recordScope = playScope,
            publish = { play, bytes -> if (_active.value === play) publish(SessionBlob.encode(game, bytes)) },
        ).also { _active.value = it }
    }

    /**
     * The one session-restore path for saves AND history replays (both are
     * [SessionBlob] envelopes naming their game). A live same-game play adopts in
     * place — and keeps its controller when the envelope is the session it already
     * holds (rotation); a stale other-game play is released; and after PROCESS DEATH — the
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
        legacyScope?.cancel()
        legacyScope = null
        latest = null
        sink = {}
        _active.value = null
    }

    private fun publish(blob: ByteArray) {
        latest = blob
        sink(blob)
    }

    //endregion
}
