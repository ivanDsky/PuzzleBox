package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.common.result.AppResult
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.CompletionRecordEmitter
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayClock
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One slot's live play (ticket 23, A9): the game's [GamePlayController] plus everything the
 * play feature owns around it — the opening → adopt lifecycle (A7/A8 degrade states
 * first-class, A11 restore order through [opening]), the record identity (ADR-0004), the
 * record write (F2 §7 fire-and-observe, on [recordScope] so a release never cancels it),
 * the A4 badge derived from the session-start baseline, and the slot's saves: every
 * in-progress session change is offered to [writer], a solve discards the row (A10).
 *
 * Every [load] and restore adopts into a NEW controller; callbacks and async results of
 * an older controller are dropped (the generation guard), so a superseded session can
 * never publish bytes or write a record. The one exception is a restore of the session
 * the live controller already holds (rotation): that controller is kept, transient
 * state and all. The record write is tracked apart from the controller (the record
 * token): only [load], [replay] and a restore of ANOTHER puzzle start a new record, so a
 * write in flight across a same-puzzle restore still delivers its badge or failure.
 */
class ActivePlay internal constructor(
    /** The (game, day) this is the one live play of (N21). */
    val slot: PlaySlot,
    private val plugin: GamePlugin<*, *, *>,
    /** The game's play registration — draws [controller]. */
    val ui: GamePlayUi,
    /** Where each [load]'s board comes from; asked again on every load and retry. */
    private val opening: SlotOpening,
    /** The slot's saves; null for a legacy play, which never touches disk (deleted in 14). */
    private val writer: SlotWriter?,
    private val completions: CompletionRepository,
    private val scope: CoroutineScope,
    private val recordScope: CoroutineScope,
    private val publish: (ActivePlay, ByteArray) -> Unit,
) {
    /** What the play screen draws: the live controller and the shared fetch state. */
    data class Board(val controller: GamePlayController, val status: PlayBoardStatus)

    /**
     * What the result route reads. [badge] is the A4 verdict for THIS play — null while
     * the write is in flight or the baseline is unknown; [label] is the result screen's
     * game line ("Zip · #123").
     */
    data class Record(val badge: Boolean?, val writeFailed: Boolean, val label: String)

    val gameId: GameId get() = plugin.definition.id

    private var generation = 0

    /** The session bytes the live controller last published or was restored from. */
    private var held: ByteArray? = null

    /** The record write's identity: bumped by [resetRecord] only (load/replay, or a
     *  restore of ANOTHER puzzle) — never by a controller swap. */
    private var recordToken = 0

    private val _board = MutableStateFlow(Board(newController(), PlayBoardStatus.Loading))
    val board: StateFlow<Board> = _board.asStateFlow()

    private val _record = MutableStateFlow(Record(badge = null, writeFailed = false, label = labelFor(null)))
    val record: StateFlow<Record> = _record.asStateFlow()

    val controller: GamePlayController get() = _board.value.controller

    /** A fresh entry must fetch: nothing playable in hand, or the live session is finished. */
    val needsLoad: Boolean
        get() = _board.value.status !is PlayBoardStatus.Ready || controller.isComplete

    private var ref: PuzzleRef? = null

    /** Whether a record already existed for [ref] BEFORE this session's win — the A4
     *  badge is derived from this baseline, never from the stored rows. */
    private var baselineHadRecord: Boolean? = null
    private var lastWon: CompletionRecord? = null

    /** Opening → board (A7/A8/A11): a restored session resumes; a Fresh fetch plays; a
     *  Cached one plays with a degrade banner; Unavailable is the error state; an envelope
     *  the game rejects is never playable. */
    fun load() = load(newController())

    /** The error state's retry: opens the slot again. */
    fun retry() = load()

    private fun load(controller: GamePlayController) {
        val gen = generation
        resetRecord(ref = null)
        _board.value = Board(controller, PlayBoardStatus.Loading)
        scope.launch {
            val status = when (val opened = opening.open(slot)) {
                is SlotOpening.Origin.Fetched -> adopt(controller, opened)
                is SlotOpening.Origin.Restored -> resume(controller, opened.session)
                is SlotOpening.Origin.Unavailable -> PlayBoardStatus.Unavailable(opened.message)
            }
            if (gen == generation) _board.value = Board(controller, status)
        }
    }

    /** Record identity = game id + day/puzzleNumber (ADR-0004): the slot's day and the
     *  number the opening read from the cache's history rows. */
    private fun adopt(controller: GamePlayController, fetched: SlotOpening.Origin.Fetched): PlayBoardStatus {
        val resolved = plugin.refOf(slot.day, fetched.puzzleNumber)
        if (controller !== this.controller) return PlayBoardStatus.Loading // superseded; never shown
        return when (val adopted = controller.adoptToday(fetched.puzzle, resolved)) {
            is Adopt.Rejected -> PlayBoardStatus.Unavailable(adopted.message)
            Adopt.Adopted -> {
                resetRecord(resolved)
                refreshBaseline(resolved)
                PlayBoardStatus.Ready(fetched.banner)
            }
        }
    }

    /** A saved play or a past day's replay build: the [SessionBlob] envelope the opening
     *  checked. Bytes the controller still cannot read are unavailable, never a loop. */
    private fun resume(controller: GamePlayController, envelope: ByteArray): PlayBoardStatus {
        if (controller !== this.controller) return PlayBoardStatus.Loading // superseded; never shown
        val session = SessionBlob.decode(envelope)?.session
        return if (session != null && adoptSession(controller, session)) {
            PlayBoardStatus.Ready(banner = null)
        } else {
            PlayBoardStatus.Unavailable(UNREADABLE)
        }
    }

    /**
     * Rotation/process-death + history-replay restore: the game's codec carries the
     * puzzle BY VALUE, so a corrupt blob falls back to a normal [load] (on the controller
     * that failed to read it — it adopted nothing); a restored COMPLETED session shows
     * its win without re-emitting (emission happens on the transition only) and reads
     * no baseline. The session the live controller already holds (rotation: the store
     * outlived the composition) keeps that controller — hint cooldown, visible hint,
     * rejection and the record write untouched. A restore of the same puzzle keeps the
     * record; another puzzle starts a new one.
     */
    internal fun adoptRestored(bytes: ByteArray) {
        if (held?.contentEquals(bytes) == true) return
        val controller = newController()
        if (!adoptSession(controller, bytes)) return load(controller)
        _board.value = Board(controller, PlayBoardStatus.Ready(banner = null))
    }

    /** Adopts the session [bytes] into [controller]; false when the game cannot read them. */
    private fun adoptSession(controller: GamePlayController, bytes: ByteArray): Boolean {
        val restored = controller.adoptRestored(bytes) ?: return false
        held = bytes
        val newPuzzle = restored.ref != ref
        if (newPuzzle) resetRecord(restored.ref)
        if (newPuzzle && !controller.isComplete) refreshBaseline(restored.ref)
        return true
    }

    /** Result's "Play again": a fresh session of the SAME puzzle (A9) — the badge resets
     *  until the new write lands and the A4 baseline is re-read. The banner stays. */
    fun replay() {
        if (_board.value.status !is PlayBoardStatus.Ready) return
        resetRecord(ref)
        refreshBaseline(ref)
        controller.replay()
    }

    /** Retry after a failed record write: a new emitter (the old one deduped this ref). */
    fun retryRecordWrite() {
        emit(lastWon ?: return)
    }

    /** Banks the running clock window (F2 §2) so the next save carries it. */
    internal fun fold() {
        (controller as? PlayClock)?.onBackground()
    }

    /** App stop: folds the clock and waits until the slot's pending save is written. */
    internal suspend fun save() {
        fold()
        writer?.flush()
    }

    /** Release: the final save is written, then the play's own work stops. The record
     *  write runs on [recordScope] and outlives it. */
    internal suspend fun close() {
        save()
        scope.cancel()
    }

    //region internals

    private fun newController(): GamePlayController {
        val gen = ++generation
        held = null
        lateinit var created: GamePlayController
        created = ui.createController(
            PlayEnvironment(
                scope = scope,
                onSessionChanged = { bytes ->
                    if (gen == generation) {
                        held = bytes
                        publish(this, bytes)
                        // A finished session never saves: the solve deleted the row (A10).
                        if (!created.isComplete) writer?.offer(SessionBlob.encode(slot.gameId, bytes))
                    }
                },
                onWon = { record -> if (gen == generation) onWon(record) },
            ),
        )
        return created
    }

    private fun onWon(record: CompletionRecord) {
        writer?.discard()
        lastWon = record
        emit(record)
    }

    private fun emit(record: CompletionRecord) {
        val token = recordToken
        _record.update { it.copy(badge = null, writeFailed = false) }
        CompletionRecordEmitter(recordScope) { r ->
            completions.onCompleted(r)
            AppResult.Ok(Unit)
        }.emit(record) { result ->
            if (token != recordToken) return@emit
            when (result) {
                // A4 badge for THIS play: true only when no record existed at session
                // start; unknown baseline (write raced the baseline read) stays null.
                is AppResult.Ok -> _record.update { it.copy(badge = baselineHadRecord?.let { had -> !had }) }
                is AppResult.Err -> _record.update { it.copy(writeFailed = true) }
            }
        }
    }

    private fun resetRecord(ref: PuzzleRef?) {
        recordToken++
        this.ref = ref
        baselineHadRecord = null
        lastWon = null
        _record.value = Record(badge = null, writeFailed = false, label = labelFor(ref))
    }

    private fun refreshBaseline(ref: PuzzleRef?) {
        if (ref == null) return
        val token = recordToken
        scope.launch {
            val had = completions.observeRecords().first().any { it.puzzleRef == ref }
            if (token == recordToken) baselineHadRecord = had
        }
    }

    private fun labelFor(ref: PuzzleRef?): String {
        val name = plugin.definition.displayName
        return ref?.puzzleNumber?.let { "$name · #$it" } ?: name
    }

    private companion object {
        const val UNREADABLE = "This saved game couldn't be read."
    }

    //endregion
}
