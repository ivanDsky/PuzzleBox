package com.ivandsky.puzzlebox.play

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.domain.FinishResult
import com.ivandsky.puzzlebox.domain.LegFinish
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Owner of the play feature's live plays: one [ActivePlay] per [PlaySlot], so
 * every stack that shows a game's day shares its board. A play outlives its entry's
 * composition: the record write is fire-and-observe (a navigation must not cancel it),
 * the badge lands on the result screen, and [playAgain] (Result's "Play again") resets in place.
 * A Koin single is the process lifetime; [retain] releases what no back stack names (fold →
 * flush → cancel) and [flush] writes every pending save at app stop.
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
    private val finishLeg: FinishLeg,
    private val calendar: GameCalendar,
    dispatchers: DispatcherProvider,
) {
    /** The picker's entries: every game with both a plugin and a play UI, in
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

    /** Which days are solved: the records snapshot plus released plays' solves. */
    private val solved = SolvedDays(completions, scope)

    private fun resultOf(slot: PlaySlot.Daily, solve: ActivePlay.Solve) =
        ResultKey(slot.gameId.value, slot.day.toString(), solve.timeMs, solve.hints)

    private fun ResultKey.slot() = PlaySlot.Daily(GameId(gameId), LocalDate.parse(day))

    /**
     * The live play of [key]'s slot, created through [SlotOpening] on first open
     * (saved row → today's fetch → a past day's build). A null day resolves to the
     * game's today once and stays pinned while the key is retained. Never resets a complete
     * play: a finished day is shown by [completedResult] before this opens, and a fresh
     * attempt is [playAgain]'s. Null when the game is unplayable.
     */
    fun open(key: GameKey): ActivePlay? {
        val game = GameId(key.gameId)
        if (games[game] == null || ui[game] == null) return null
        return open(slotOf(key, game) ?: return null)
    }

    /** The live play of a friend's puzzle, opened as [open] opens a daily's; its board is
     *  the received puzzle stored when the code opened. A live play is returned as it
     *  stands: its key is still on a back stack, so a solve it shows has not handed off
     *  yet. Null when the game is unplayable. */
    fun open(key: CustomGameKey): ActivePlay? = open(key.slot)

    /**
     * What opening [key] shows, without a read: a live play is its own answer (complete →
     * its solve's result, else its board); with none live, a day the snapshot does not hold
     * is a board, and a solved one is a [OpenDecision.Check] (a saved play-again row would still
     * win). [OpenDecision.Cold] until the first records read lands.
     */
    internal fun decide(key: GameKey): OpenDecision {
        val game = GameId(key.gameId)
        if (games[game] == null || ui[game] == null) return OpenDecision.Open
        val slot = slotOf(key, game) as? PlaySlot.Daily ?: return OpenDecision.Open
        val live = slots[slot]
        if (live != null) {
            if (!live.controller.isComplete) return OpenDecision.Open
            val solve = live.record.value.solve ?: solved.solvedBefore(slot) ?: return OpenDecision.Open
            return OpenDecision.Finished(resultOf(slot, solve))
        }
        if (solved.isCold) return OpenDecision.Cold
        return solved.solvedBefore(slot)?.let { OpenDecision.Check(resultOf(slot, it)) } ?: OpenDecision.Open
    }

    /**
     * The result a [GameKey]'s day already has, or null when its board should open: [decide]
     * after the snapshot has loaded, with a solved day's saved play-again row read. The one place
     * "open a finished day" is decided; custom games never ask.
     */
    suspend fun completedResult(key: GameKey): ResultKey? {
        solved.awaitLoaded()
        return when (val decision = decide(key)) {
            is OpenDecision.Finished -> decision.result
            is OpenDecision.Check -> decision.result.takeIf { opening.savedRow(it.slot()) == null }
            OpenDecision.Open, OpenDecision.Cold -> null
        }
    }

    /**
     * Result's "Play again": [key]'s slot starts a fresh attempt of the same puzzle. A
     * complete live play is reset in place; with none live the slot opens fresh (a solve
     * deleted its row). The play is live afterwards, so the board that follows opens it as is.
     */
    fun playAgain(key: ResultKey) {
        val live = open(PlaySlot.Daily(GameId(key.gameId), LocalDate.parse(key.day))) ?: return
        if (live.controller.isComplete) live.playAgain()
    }

    /** The live play of a duel leg, opened and kept as [open] keeps a friend's puzzle's; its
     *  board is the leg's puzzle, built from the match on first open. Null when the key's
     *  code is unreadable or the game is unplayable. */
    fun open(key: MatchLegKey): ActivePlay? = key.slot?.let { open(it) }

    /** Gives up [key]'s leg (a loss, never restarted): the loss is queued first, then the live
     *  play is dropped without its save, or the saved row when none is live; no completion. Signed
     *  out, nothing is queued and the play and its save stay. Runs on the store's scope, so a
     *  navigation right after can't cut it. Null for an unreadable code. */
    suspend fun giveUp(key: MatchLegKey): FinishResult? {
        val code = ShareCode.parse(key.code) ?: return null
        val slot = PlaySlot.Leg(GameId(key.gameId), code.value, key.legIndex)
        return scope.async {
            finishLeg(code, key.legIndex, LegFinish.GaveUp).also {
                if (it == FinishResult.SignedOut) return@also
                val live = slots.remove(slot)
                if (live != null) live.close(save = false) else writers(slot).discard()
            }
        }.await()
    }

    /** The live play of [slot]; never creates one. */
    fun peek(slot: PlaySlot): ActivePlay? = slots[slot]

    /**
     * [slot]'s newest unfinished session as a SessionBlob envelope, for the hub's
     * thumbnail: a live play's change still waiting in its writer's window, else the saved
     * row, read after every save already queued (a just-released play's final save
     * included). Null when the slot has neither. Never opens a play.
     */
    internal suspend fun latestSave(slot: PlaySlot): ByteArray? = slots[slot]?.unsavedSession ?: opening.savedRow(slot)

    /**
     * Releases every live slot no key in [keys] names: a [GameKey] with a day names that day, a null-day one the slot it pinned,
     * a [ResultKey] its play's slot (its badge and retry read it), a [CustomGameKey] its friend's puzzle, a [MatchLegKey] its leg,
     * and a [MatchLegResultKey] the live leg of its code and index. A released play folds and queues its last save before return.
     */
    fun retain(keys: Collection<NavKey>) {
        val gameKeys = keys.filterIsInstance<GameKey>().toSet()
        pinned.keys.retainAll(gameKeys)
        val named = gameKeys.mapNotNullTo(HashSet()) { key ->
            if (key.day == null) pinned[key] else PlaySlot.Daily(GameId(key.gameId), LocalDate.parse(key.day))
        }
        keys.filterIsInstance<ResultKey>().mapTo(named) { PlaySlot.Daily(GameId(it.gameId), LocalDate.parse(it.day)) }
        keys.filterIsInstance<CustomGameKey>().mapTo(named) { it.slot }
        keys.filterIsInstance<MatchLegKey>().mapNotNullTo(named) { it.slot }
        val legResults = keys.filterIsInstance<MatchLegResultKey>().mapTo(HashSet()) { it.code to it.legIndex }
        slots.keys.filterIsInstance<PlaySlot.Leg>().filterTo(named) { (it.matchCode to it.legIndex) in legResults }
        val released = slots.filterKeys { it !in named }.values.toList()
        released.forEach { play ->
            slots.remove(play.slot)
            // A solve whose record write has not landed must still read as solved on reopen.
            val daily = play.slot as? PlaySlot.Daily
            val solve = play.record.value.solve
            if (daily != null && solve != null && play.controller.isComplete) solved.release(daily, solve)
            scope.launch(start = CoroutineStart.UNDISPATCHED) { play.close() }
        }
    }

    /** Drops [slot]'s live play without its final save and cancels its pending one; no-op when
     *  none is live. Returns once the play is gone, so a delete queued after it cannot be
     *  overtaken. Safe from any thread: the work runs on the store's own. */
    suspend fun discard(slot: PlaySlot.Custom) {
        scope.launch { slots.remove(slot)?.close(save = false) }.join()
    }

    /** Folds every live play's clock and writes every pending save; the job ends when all are on disk. */
    fun flush(): Job {
        val saves = slots.values.map { play -> scope.launch(start = CoroutineStart.UNDISPATCHED) { play.save() } }
        return scope.launch(start = CoroutineStart.UNDISPATCHED) { saves.joinAll() }
    }

    private fun open(slot: PlaySlot): ActivePlay? {
        val plugin = games[slot.gameId] ?: return null
        val gameUi = ui[slot.gameId] ?: return null
        slots[slot]?.let { return it }
        return ActivePlay(
            slot = slot,
            plugin = plugin,
            ui = gameUi,
            opening = opening,
            writer = writers(slot),
            completions = completions.forSlot(slot, finishLeg),
            scope = playScope(),
            recordScope = scope,
        ).also {
            slots[slot] = it
            it.load()
        }
    }

    private fun slotOf(key: GameKey, game: GameId): PlaySlot? {
        key.day?.let { return PlaySlot.Daily(game, LocalDate.parse(it)) }
        pinned[key]?.let { return it }
        val today = calendar.today(game) ?: return null
        return PlaySlot.Daily(game, today).also { pinned[key] = it }
    }

    private fun playScope(): CoroutineScope = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))
}

private val CustomGameKey.slot: PlaySlot get() = PlaySlot.Custom(GameId(gameId), puzzleId)

private val MatchLegKey.slot: PlaySlot?
    get() = ShareCode.parse(code)?.let { PlaySlot.Leg(GameId(gameId), it.value, legIndex) }
