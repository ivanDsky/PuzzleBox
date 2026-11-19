package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.SelfMatchRepository
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.data.match.isSelf
import com.ivandsky.puzzlebox.data.match.mySeat
import com.ivandsky.puzzlebox.data.match.seatFor
import com.ivandsky.puzzlebox.play.domain.FinishResult
import com.ivandsky.puzzlebox.play.domain.ObserveLegTarget
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a duel puzzle's route draws. */
internal sealed interface MatchLegState {

    /** Before the taken check passes; a taken puzzle stays here while the route leaves for its result. */
    data object Checking : MatchLegState

    /**
     * The board of [play] (null when the puzzle is unplayable in this build), where it sits in its duel
     * ([position], null until the match is known) and whether the bar's menu offers Give up ([canGiveUp]: the
     * player holds a seat, so the loss can be recorded). [target] is past you's time while the leg's timer counts
     * down to it (a self duel with the setting on), else null.
     */
    data class Playing(
        val play: ActivePlay?,
        val position: LegPosition?,
        val canGiveUp: Boolean,
        val target: Duration? = null,
    ) : MatchLegState
}

/** Where the route leaves to. */
internal sealed interface MatchLegExit {

    /** My own result for this puzzle already exists: its result page. */
    data class Taken(val result: MatchLegResultKey) : MatchLegExit

    /** The give up was queued (or lost to a finish that was already queued, with no row to show). */
    data object GaveUp : MatchLegExit
}

/**
 * Where a duel puzzle sits: the [opponent]'s name (null while the seat is open), its 1-based [number] of [count].
 * [played] is how many of my puzzles are posted, and is set only for a self duel (null for a friend duel).
 */
internal data class LegPosition(val opponent: String?, val number: Int, val count: Int, val played: Int? = null)

/**
 * The state holder of one duel puzzle's board for [key]: the store's live play of the key's leg, opened only
 * once my own queued result for the puzzle is known not to exist. A taken puzzle never reopens: my row
 * in the outbox (the single truth for "taken") sends the route to [MatchLegExit.Taken] with that row's result.
 * "My" is the seat this device gives the player ([seatFor]): a self duel has one signed in or out, read from the
 * device, never from the match document. Without a seat (a
 * friend duel signed out) the check waits up to [IDENTITY_WAIT_MS] for the signed-in player (restored after a kill
 * only some moments in), runs seatless past that, and runs again when the player changes. The play is the store's,
 * so it outlives this ViewModel; releasing it is [PlaySessionStore.retain]'s job, never this one's.
 *
 * The board never waits on the match document: once it arrives [MatchLegState.Playing.position] names the
 * puzzle in its duel and the self duel's [legTarget] and `progress[ME]` row feed the position line and the
 * timer's target; nothing else of the match is read from here. [giveUp] queues the loss through the store; without a seat, nothing
 * could record it and nothing happens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MatchLegViewModel(
    private val key: MatchLegKey,
    private val store: PlaySessionStore,
    private val matches: MatchRepository,
    private val outbox: MatchResultOutbox,
    private val identity: IdentityService,
    private val self: SelfMatchRepository,
    private val legTarget: ObserveLegTarget,
) : ViewModel() {

    private val code = ShareCode.parse(key.code)

    /** The opened play once the check has passed; its `play` is null when the puzzle is unplayable. */
    private val opened = MutableStateFlow<Opened?>(null)

    private val exitEvents = Channel<MatchLegExit>(Channel.BUFFERED)
    val exits: Flow<MatchLegExit> = exitEvents.receiveAsFlow()

    private var givingUp = false

    private val uid: Flow<String?> = identity.current.map { it?.uid }.distinctUntilChanged()

    val state: StateFlow<MatchLegState> = opened.flatMapLatest { open ->
        if (open == null) {
            flowOf(MatchLegState.Checking)
        } else {
            // The board is already open: the match document only fills the position line when it arrives.
            val fetch: Flow<MatchFetch?> = code?.let { matches.observe(it).onStart<MatchFetch?> { emit(null) } } ?: flowOf(null)
            val target = code?.let { legTarget(it, key.legIndex) } ?: flowOf(null)
            combine(fetch, uid, target) { fetch, uid, target ->
                val seat = seatOf(fetch, uid)
                MatchLegState.Playing(open.play, legPosition(fetch, key, seat), canGiveUp = seat != null, target = target)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MatchLegState.Checking)

    init {
        viewModelScope.launch {
            uid.collectLatest { uid ->
                val seat = seat(uid)
                // No seat so far: give a restoring player a moment; its arrival restarts this check.
                if (seat == null) delay(IDENTITY_WAIT_MS)
                val result = taken(seat)
                if (result != null) exitEvents.send(MatchLegExit.Taken(result)) else openPlay()
            }
        }
    }

    /** Confirmed Give up: queues the loss and leaves; once is enough while it runs. */
    fun giveUp() {
        if (givingUp) return
        givingUp = true
        viewModelScope.launch {
            when (store.giveUp(key)) {
                FinishResult.Queued -> exitEvents.send(MatchLegExit.GaveUp)
                // A finish landed first (a solve at the same moment): its row is the result.
                FinishResult.AlreadyRecorded -> {
                    val result = taken(seat(identity.current.value?.uid))
                    exitEvents.send(result?.let(MatchLegExit::Taken) ?: MatchLegExit.GaveUp)
                }
                FinishResult.SignedOut, null -> givingUp = false
            }
        }
    }

    private fun openPlay() {
        if (opened.value == null) opened.value = Opened(store.open(key))
    }

    /** From the device alone: the taken check must not wait on a remote read of a friend match. */
    private suspend fun seat(uid: String?): String? = code?.let { self.seatFor(it, uid) } ?: uid

    private suspend fun taken(seat: String?): MatchLegResultKey? =
        code?.let { takenResultKey(outbox.observe(it).first(), key, seat) }

    private class Opened(val play: ActivePlay?)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** How long a duel puzzle waits for the restored player before checking "taken" without a seat. */
        const val IDENTITY_WAIT_MS = 2_000L
    }
}

/** The seat [uid] holds in a found match: the self seat in a self duel, signed in or out; [uid] itself until the match is found. */
internal fun seatOf(fetch: MatchFetch?, uid: String?): String? {
    val match = (fetch as? MatchFetch.Found)?.match ?: return uid
    return match.mySeat(uid)
}

/** [key]'s place in a found match as the seat [uid] sees it; null until the match is found or when the index is past its puzzles. */
internal fun legPosition(fetch: MatchFetch?, key: MatchLegKey, uid: String?): LegPosition? {
    val match = (fetch as? MatchFetch.Found)?.match ?: return null
    if (key.legIndex !in match.legs.indices) return null
    val played = if (match.isSelf) match.progress[SelfSeats.ME].orEmpty().size else null
    return LegPosition(uid?.let(match::opponentOf)?.name, key.legIndex + 1, match.legs.size, played)
}

/** The result page of [key] when [rows] hold the seat [uid]'s own result for it, else null (the board opens). */
internal fun takenResultKey(rows: List<OutboxResult>, key: MatchLegKey, uid: String?): MatchLegResultKey? {
    uid ?: return null
    val mine = rows.firstOrNull { it.result.uid == uid && it.result.legIndex == key.legIndex }?.result ?: return null
    return MatchLegResultKey(key.code, key.legIndex, mine.timeMs, mine.hints, mine.gaveUp)
}
