package com.ivandsky.puzzlebox.train.round

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.trainer.TrainerPuzzleSource
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
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
 * shorter round; none is [RoundState.Empty]. The snippets live in memory only: turning the device keeps this view model
 * and so the round, while a restore after the system closed the app builds a new one and starts again (A15). What the
 * player did is recorded through [recordAnswer] as it happens, so the in-a-row count outlives the round (A6).
 *
 * A snippet takes one answer, whichever comes first, a move on the board or Show me; any later one is ignored.
 */
internal class RoundViewModel(
    private val game: TrainerGame,
    private val technique: TechniqueId,
    private val source: TrainerPuzzleSource,
    private val recordAnswer: RecordAnswer,
    private val dispatchers: DispatcherProvider,
) : ViewModel() {

    private val mutableState = MutableStateFlow<RoundState>(RoundState.Loading)
    private var attempt: Int? = null
    private var build: Job? = null

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
            mutableState.value = if (snippets.isEmpty()) RoundState.Empty else RoundState.Question(snippets, 0, null, 0)
        }
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

    /** Walks the puzzles newest first until the round is full; the walk is CPU work, so it runs off the main thread. */
    private suspend fun cut(): List<Snippet> {
        val snippets = mutableListOf<Snippet>()
        source.puzzles(game.gameId).firstOrNull { puzzle ->
            snippets += game.buildSnippets(puzzle, technique, minOf(PER_PUZZLE, ROUND_SIZE - snippets.size))
            snippets.size >= ROUND_SIZE
        }
        return snippets
    }
}
