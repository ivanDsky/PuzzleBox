package com.ivandsky.puzzlebox.train.round

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.trainer.ArchiveLoad
import com.ivandsky.puzzlebox.data.trainer.TrainerArchive
import com.ivandsky.puzzlebox.data.trainer.TrainerPuzzleSource
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.gameui.trainer.Snippet
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.train.domain.RecordAnswer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ROUND_SIZE = 5
private const val PER_PUZZLE = 2

/**
 * One technique's round of up to [ROUND_SIZE] snippets, cut from the puzzles [source] has on the device, newest first
 * and at most [PER_PUZZLE] from one puzzle so a round is not one board over and over. Fewer than five puzzles' worth is a
 * shorter round. The snippets live in memory only: turning the device keeps this view model and so the round, while a
 * restore after the system closed the app builds a new one and starts again (A15). What the player did is recorded
 * through [recordAnswer] as it happens, so the in-a-row count outlives the round (A6).
 *
 * With nothing on the device the round is [RoundState.Empty] when the technique's drill sources name no archive puzzle,
 * else [RoundState.NeedsConnection]. [tryAgain] is one tap: it asks [archive] to load the first named puzzle that is not
 * on the device, one fetch at most, and cuts the round from it and what is on the device. A load that gives no puzzle
 * leaves [RoundState.CantLoad] naming a puzzle already on the device, which [tryAnother] opens, or [RoundState.NeedsConnection]
 * again when there is none. A puzzle that is gone, untrusted or has no snippet is not asked for again by this view model
 * until every named puzzle has been tried; a busy or offline load asks for the same puzzle on the next tap. No load,
 * failed or not, is ever an answer: only [onVerdict] and [showMe] reach [recordAnswer].
 *
 * A snippet takes one answer, whichever comes first, a move on the board or Show me; any later one is ignored.
 */
internal class RoundViewModel(
    private val game: TrainerGame,
    private val technique: TechniqueId,
    private val source: TrainerPuzzleSource,
    private val archive: TrainerArchive,
    private val recordAnswer: RecordAnswer,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val mutableState = MutableStateFlow<RoundState>(RoundState.Loading)
    private var attempt: Int? = null
    private var build: Job? = null

    /** The archive numbers a load found no use in; only touched on the main thread. */
    private val skipped = mutableSetOf<Int>()

    val state: StateFlow<RoundState> = mutableState.asStateFlow()

    /**
     * Builds a round for [attempt], the number of the time the technique was opened. The same number again (the screen
     * turned, the host composed anew) leaves the round as it is; a new one drops it and builds another.
     */
    fun start(attempt: Int) {
        if (attempt == this.attempt) return
        this.attempt = attempt
        build?.cancel()
        mutableState.value = RoundState.Loading
        build = viewModelScope.launch {
            val snippets = withContext(dispatchers.default) { cut() }
            mutableState.value = when {
                snippets.isNotEmpty() -> RoundState.Question(snippets, 0, null, 0)
                withContext(dispatchers.default) { candidates() }.isEmpty() -> RoundState.Empty
                else -> RoundState.NeedsConnection
            }
        }
    }

    /**
     * The needs-a-connection tap: loads the first named puzzle that is not on the device. Ignored in any other state, so
     * a second tap while the first is loading changes nothing.
     */
    fun tryAgain() {
        if (mutableState.value != RoundState.NeedsConnection) return
        mutableState.value = RoundState.Loading
        build = viewModelScope.launch {
            val numbers = withContext(dispatchers.default) { candidates() }
            val onDevice = archive.onDevice(game.gameId, technique, numbers).toSet()
            var target = numbers.firstOrNull { it !in onDevice && it !in skipped }
            if (target == null && skipped.isNotEmpty()) {
                // Every named puzzle has failed once: begin again from the top rather than strand the player.
                skipped.clear()
                target = numbers.firstOrNull { it !in onDevice }
            }
            if (target == null) {
                mutableState.value = RoundState.Empty
            } else {
                resolve(archive.load(game.gameId, technique, target))
            }
        }
    }

    /** The can't-load tap: opens the puzzle already on the device that the state names. Ignored in any other state. */
    fun tryAnother() {
        val current = mutableState.value as? RoundState.CantLoad ?: return
        mutableState.value = RoundState.Loading
        build = viewModelScope.launch { resolve(archive.load(game.gameId, technique, current.alternative)) }
    }

    /** The player made [verdict]'s move on [snippet]. Ignored unless [snippet] is the one up and still unanswered. */
    fun onVerdict(snippet: Snippet, verdict: TrainerVerdict) = answer(snippet, RecordAnswer.Answer.Verdict(verdict))

    /** The player asked to be shown [snippet]'s step. Ignored unless [snippet] is the one up and still unanswered. */
    fun showMe(snippet: Snippet) = answer(snippet, RecordAnswer.Answer.ShowMe)

    /** Goes on from an answered snippet to the next, or to the summary after the last. */
    fun next() {
        val current = mutableState.value as? RoundState.Question ?: return
        if (!current.answered) return
        mutableState.value = if (current.isLast) {
            RoundState.Summary(current.right, current.total)
        } else {
            current.copy(index = current.index + 1, answer = null)
        }
    }

    private fun answer(snippet: Snippet, answer: RecordAnswer.Answer) {
        val current = mutableState.value as? RoundState.Question ?: return
        if (current.snippet !== snippet || current.answered) return
        mutableState.value = current.copy(answer = answer, right = current.right + if (answer.isRight) 1 else 0)
        viewModelScope.launch { recordAnswer(game.gameId, technique, answer) }
    }

    /** A loaded puzzle starts the round; any other outcome, or a puzzle with no snippet, is a failed load. */
    private suspend fun resolve(load: ArchiveLoad) {
        if (load is ArchiveLoad.Loaded) {
            val snippets = withContext(dispatchers.default) { cut(first = load.puzzle) }
            if (snippets.isNotEmpty()) {
                mutableState.value = RoundState.Question(snippets, 0, null, 0)
                return
            }
        }
        // Busy and offline are worth the same puzzle again; every other outcome is not.
        val retryable = load is ArchiveLoad.CantLoad &&
            (load.reason == ArchiveLoad.CantLoad.Reason.Busy || load.reason == ArchiveLoad.CantLoad.Reason.Offline)
        if (!retryable) skipped += load.number
        val numbers = withContext(dispatchers.default) { candidates() }
        val alternative = archive.onDevice(game.gameId, technique, numbers).firstOrNull { it !in skipped }
        mutableState.value = if (alternative == null) RoundState.NeedsConnection else RoundState.CantLoad(alternative)
    }

    /** The archive puzzle numbers the technique's drill sources name, the most target steps first. */
    private fun candidates(): List<Int> = game.drillSources
        .flatMap { it.candidates(technique) }
        .filterIsInstance<DrillCandidate.Archive>()
        .sortedByDescending { it.targetSteps }
        .map { it.number }
        .distinct()

    /**
     * Walks [first], then the puzzles on the device newest first, until the round is full; the walk is CPU work, so it
     * runs off the main thread. [first] is not walked twice when the device lists it too.
     */
    private suspend fun cut(first: Puzzle? = null): List<Snippet> {
        val snippets = mutableListOf<Snippet>()
        fun full(puzzle: Puzzle): Boolean {
            snippets += game.buildSnippets(puzzle, technique, minOf(PER_PUZZLE, ROUND_SIZE - snippets.size))
            return snippets.size >= ROUND_SIZE
        }
        if (first != null && full(first)) return snippets
        source.puzzles(game.gameId).firstOrNull { puzzle -> puzzle != first && full(puzzle) }
        return snippets
    }
}
