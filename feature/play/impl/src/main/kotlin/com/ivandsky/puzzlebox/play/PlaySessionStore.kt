package com.ivandsky.puzzlebox.play

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
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
        ).also {
            slots[slot] = it
            it.load()
        }
    }

    /** The live play of [slot]; never creates one. */
    fun peek(slot: PlaySlot): ActivePlay? = slots[slot]

    /**
     * Releases every live slot that no key in [keys] names: a [GameKey] with a day names
     * that day, a null-day one the slot it pinned, and a [ResultKey] its play's slot (the
     * result's badge and retry read it). A released play folds its clock and queues its
     * final save before this returns, so a reopen's read is ordered after it.
     */
    fun retain(keys: Collection<NavKey>) {
        val gameKeys = keys.filterIsInstance<GameKey>().toSet()
        pinned.keys.retainAll(gameKeys)
        val named = gameKeys.mapNotNullTo(HashSet()) { key ->
            if (key.day == null) pinned[key] else PlaySlot(GameId(key.gameId), LocalDate.parse(key.day))
        }
        keys.filterIsInstance<ResultKey>().mapTo(named) { PlaySlot(GameId(it.gameId), LocalDate.parse(it.day)) }
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
}
