package com.ivandsky.puzzlebox.train.drill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.trainer.ArchiveLoad
import com.ivandsky.puzzlebox.data.trainer.TrainerArchive
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.model.PuzzleKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One technique's drill: a full puzzle that needs it, played on the game's own play screen. The puzzle is the first of
 * the technique's drill candidates (the most target steps first) that is already on the device, [DrillCandidate.Bundled]
 * or an archive number [archive] holds, preferring one that is not in the `avoid` set [start] is given (what the round
 * just used) when another is there. With none on the device the drill is [DrillState.NeedsConnection], or
 * [DrillState.Empty] when the technique names no candidate at all.
 *
 * [tryAgain] is one tap: it asks [archive] to load the first named archive puzzle that is not on the device, one fetch
 * at most. A load that gives no puzzle, a puzzle the game will not adopt, or a mismatch leaves [DrillState.CantLoad]
 * naming a puzzle already on the device, which [tryAnother] opens, or [DrillState.NeedsConnection] again when there is
 * none. A candidate that cannot open is not offered again by this view model until every one has failed once; a busy or
 * offline load asks for the same puzzle on the next tap.
 *
 * The board is the Studio's test-solve chain: the game's controller is created on a lifetime of this view model and
 * adopts the puzzle under the throwaway key `PuzzleKey.Custom("trainer-<game>-<n>")`, `n` the archive number or the
 * bundled id. Its environment writes nothing (no saved session, no move log), and this class holds no repository, so a
 * drill leaves no completion, record, replay, streak day, saved play or archive mark. A win only marks the board won; the
 * screen waits for the solve sweep, which reports through [solved].
 *
 * Turning the device keeps this view model and so the board; a restore after the system closed the app builds a new
 * one and starts again (A15). [leave] drops the board.
 */
internal class DrillViewModel(
    private val game: TrainerGame,
    private val technique: TechniqueId,
    private val plays: GamePlayRegistry,
    private val games: GameRegistry,
    private val archive: TrainerArchive,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val mutableState = MutableStateFlow<DrillState>(DrillState.Loading)
    private var attempt: Int? = null
    private var build: Job? = null
    private var lifetime: CoroutineScope? = null

    /** The candidates that failed to open, by [key]; only touched on the main thread. */
    private val skipped = mutableSetOf<String>()

    val state: StateFlow<DrillState> = mutableState.asStateFlow()

    /**
     * Opens a drill for [attempt], the number of the time the technique was opened. The same number again (the screen
     * turned, the host composed anew) leaves the drill as it is; a new one drops it and picks another puzzle, avoiding
     * the archive numbers in [avoid] when a puzzle outside them is on the device.
     */
    fun start(attempt: Int, avoid: Set<Int> = emptySet()) {
        if (attempt == this.attempt) return
        reset()
        this.attempt = attempt
        build = viewModelScope.launch {
            val candidates = withContext(dispatchers.default) { candidates() }
            if (candidates.isEmpty()) {
                mutableState.value = DrillState.Empty
                return@launch
            }
            val ready = ready(candidates)
            val pick = ready.firstOrNull { !(it is DrillCandidate.Archive && it.number in avoid) } ?: ready.firstOrNull()
            if (pick == null) mutableState.value = DrillState.NeedsConnection else open(pick)
        }
    }

    /**
     * The needs-a-connection tap: loads the first named archive puzzle that is not on the device. Ignored in any other
     * state, so a second tap while the first is loading changes nothing.
     */
    fun tryAgain() {
        if (mutableState.value != DrillState.NeedsConnection) return
        mutableState.value = DrillState.Loading
        build = viewModelScope.launch {
            val named = withContext(dispatchers.default) { candidates() }.filterIsInstance<DrillCandidate.Archive>()
            val onDevice = archive.onDevice(game.gameId, technique, named.map { it.number }).toSet()
            var target = named.firstOrNull { it.number !in onDevice && it.key() !in skipped }
            if (target == null && skipped.isNotEmpty()) {
                // Every named puzzle has failed once: begin again from the top rather than strand the player.
                skipped.clear()
                target = named.firstOrNull { it.number !in onDevice }
            }
            if (target == null) mutableState.value = DrillState.Empty else open(target)
        }
    }

    /** The can't-load tap: opens the puzzle already on the device that the state names. Ignored in any other state. */
    fun tryAnother() {
        val current = mutableState.value as? DrillState.CantLoad ?: return
        mutableState.value = DrillState.Loading
        build = viewModelScope.launch { open(current.alternative) }
    }

    /**
     * The board's solve sweep has played: shows the solved page. Ignored unless the board was won, and in any other
     * state, so a stray report after leaving changes nothing.
     */
    fun solved() {
        val current = mutableState.value as? DrillState.Playing ?: return
        if (!current.won) return
        lifetime?.cancel()
        lifetime = null
        mutableState.value = DrillState.Solved
    }

    /** Back to the technique list: drops the load and the board, so the next [start] begins from nothing. */
    fun leave() {
        reset()
        attempt = null
    }

    private fun reset() {
        build?.cancel()
        build = null
        lifetime?.cancel()
        lifetime = null
        skipped.clear()
        mutableState.value = DrillState.Loading
    }

    /** Opens [candidate]: its puzzle, loaded from the trainer cache or fetched once, then adopted; any other outcome is a failed open. */
    private suspend fun open(candidate: DrillCandidate) {
        val (puzzle, name) = when (candidate) {
            is DrillCandidate.Bundled -> candidate.puzzle to candidate.id
            is DrillCandidate.Archive -> when (val load = archive.load(game.gameId, technique, candidate.number)) {
                is ArchiveLoad.Loaded -> load.puzzle to load.number.toString()
                else -> return fail(candidate, retryable = load.isRetryable())
            }
        }
        if (!adopt(puzzle, name)) fail(candidate, retryable = false)
    }

    /** Puts [puzzle] on a fresh controller under the drill's throwaway key; false when the game has no play screen or refuses it. */
    private fun adopt(puzzle: Puzzle, name: String): Boolean {
        val ui = plays[game.gameId] ?: return false
        val plugin = games[game.gameId] ?: return false
        val parent = viewModelScope.coroutineContext
        val life = CoroutineScope(parent + SupervisorJob(parent[Job]))
        val controller = ui.createController(
            PlayEnvironment(life, onSessionChanged = {}, onWon = { mutableState.update { it.markWon() } }),
        )
        val ref = plugin.refOf(PuzzleKey.Custom("trainer-${game.gameId.value}-$name"))
        if (controller.adoptToday(puzzle, ref) is Adopt.Rejected) {
            life.cancel()
            return false
        }
        lifetime = life
        mutableState.value = DrillState.Playing(ui, controller, won = false)
        return true
    }

    /** [candidate] did not open: remember it unless a later tap could help, then offer another puzzle on the device, or ask for a connection. */
    private suspend fun fail(candidate: DrillCandidate, retryable: Boolean) {
        if (!retryable) skipped += candidate.key()
        val alternative = ready(withContext(dispatchers.default) { candidates() }).firstOrNull()
        mutableState.value = if (alternative == null) DrillState.NeedsConnection else DrillState.CantLoad(alternative)
    }

    /** The [candidates] that open without a fetch and have not failed: every bundled one and each archive puzzle the device holds. */
    private suspend fun ready(candidates: List<DrillCandidate>): List<DrillCandidate> {
        val onDevice = archive.onDevice(game.gameId, technique, candidates.filterIsInstance<DrillCandidate.Archive>().map { it.number }).toSet()
        return candidates.filter { candidate ->
            candidate.key() !in skipped && when (candidate) {
                is DrillCandidate.Bundled -> true
                is DrillCandidate.Archive -> candidate.number in onDevice
            }
        }
    }

    /** What the technique's drill sources name, the most target steps first, each puzzle once. */
    private fun candidates(): List<DrillCandidate> = game.drillSources
        .flatMap { it.candidates(technique) }
        .sortedByDescending { it.targetSteps }
        .distinctBy { it.key() }

    private fun DrillState.markWon(): DrillState = if (this is DrillState.Playing) copy(won = true) else this

    /** Busy and offline are worth the same puzzle again; every other outcome is not. */
    private fun ArchiveLoad.isRetryable(): Boolean =
        this is ArchiveLoad.CantLoad && (reason == ArchiveLoad.CantLoad.Reason.Busy || reason == ArchiveLoad.CantLoad.Reason.Offline)

    private fun DrillCandidate.key(): String = when (this) {
        is DrillCandidate.Archive -> "archive-$number"
        is DrillCandidate.Bundled -> "bundled-$id"
    }
}
