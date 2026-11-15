package com.ivandsky.puzzlebox.testing.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInPostState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInPosts
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Scripted [LinkedInPosts]. It decides nothing: a test sets the exact state a day's button reports through
 * [emit], and a submit only records its call and answers [submitResult], so nothing a submit does shows up
 * in [observe] by itself.
 */
class FakeLinkedInPosts : LinkedInPosts {

    private val recorded = CopyOnWriteArrayList<Pair<GameId, LocalDate>>()
    private val states = MutableStateFlow<Map<Pair<GameId, LocalDate>, LinkedInPostState>>(emptyMap())

    /** Every submit so far as `(game, day)`, oldest first, recorded when the submit is entered. */
    val submitCalls: List<Pair<GameId, LocalDate>> get() = recorded.toList()

    /** The answer of every following [submit]. */
    @Volatile
    var submitResult: LinkedInPostState = LinkedInPostState.Posted

    /** When set, a submit suspends on it after recording its call, so a test can leave the screen midway. */
    @Volatile
    var submitGate: CompletableDeferred<Unit>? = null

    override suspend fun submit(game: GameId, day: LocalDate): LinkedInPostState {
        recorded += game to day
        submitGate?.await()
        return submitResult
    }

    /** A day never set through [emit] has no button. */
    override fun observe(game: GameId, day: LocalDate): Flow<LinkedInPostState> =
        states.map { it[game to day] ?: LinkedInPostState.Hidden }.distinctUntilChanged()

    fun emit(game: GameId, day: LocalDate, state: LinkedInPostState) {
        states.update { it + ((game to day) to state) }
    }
}
