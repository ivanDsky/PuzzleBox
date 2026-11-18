package com.ivandsky.puzzlebox.compete.reveal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.isSelf
import com.ivandsky.puzzlebox.domain.CreateMatch
import com.ivandsky.puzzlebox.domain.CreateOutcome
import com.ivandsky.puzzlebox.domain.CreateSelfDuel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the rematch sheet draws: [sending] while the new duel is made, and the [note] of a refusal. */
internal data class RematchState(val sending: Boolean = false, val note: String? = null)

/**
 * The rematch sheet's state holder for [match]: Send new invite creates the duel again through [createMatch] (a self duel
 * through [createSelfDuel], which has no sheet: the reveal sends it from Rematch) with the
 * same settings and a fresh draw (with DuelLineup on, each puzzle an "Any" of its game, in the duel's order).
 * One send runs at a time; a refusal becomes a [RematchState.note] and frees the button, while a created duel is handed over once on [created] and keeps the sheet [RematchState.sending] until
 * [reset] says it has left.
 */
internal class RematchViewModel(
    private val match: Match,
    private val createMatch: CreateMatch,
    private val createSelfDuel: CreateSelfDuel,
    private val failures: FailureLog = LogcatFailureLog,
    private val releaseFlags: ReleaseFlags? = null,
) : ViewModel() {

    private val working = MutableStateFlow(RematchState())
    private val createdDuels = Channel<CreateOutcome.Created>(Channel.BUFFERED)

    val state: StateFlow<RematchState> = working.asStateFlow()

    /** The new duel, once, after Send new invite succeeded. */
    val created: Flow<CreateOutcome.Created> = createdDuels.receiveAsFlow()

    fun send() {
        if (working.getAndUpdate { RematchState(sending = true) }.sending) return
        viewModelScope.launch {
            val outcome = try {
                if (match.isSelf) createSelfDuel(selfRematchSettings(match)) else createMatch(rematchSettings(match, lineup = releaseFlags?.isEnabled(ReleaseFlag.DuelLineup) == true), rematchOf = match)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures.warn(TAG, "rematch create failed", e)
                CreateOutcome.Failed(null)
            }
            when (outcome) {
                is CreateOutcome.Created -> createdDuels.send(outcome)
                else -> working.update { RematchState(note = refusalNote(outcome)) }
            }
        }
    }

    /** The sheet has left: the next one opens clean. Never while a send is in flight. */
    fun reset() {
        working.value = RematchState()
    }
}

private fun refusalNote(outcome: CreateOutcome): String? = when (outcome) {
    is CreateOutcome.Created -> null
    CreateOutcome.Offline -> "You're offline. Check the connection and try again."
    CreateOutcome.DailyCapReached -> "You've reached today's limit on new duels. Try again tomorrow."
    CreateOutcome.SignedOut -> "Sign in to send a rematch."
    CreateOutcome.NoPuzzles -> "There are no puzzles left to draw. Try again later."
    is CreateOutcome.Failed -> outcome.message ?: "Couldn't send the invite. Try again."
    is CreateOutcome.EntryUndrawable -> "Couldn't send the invite. Try again."
}

private const val TAG = "RematchViewModel"
