package com.ivandsky.puzzlebox.compete.tab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.compete.create.DuelChallenge
import com.ivandsky.puzzlebox.compete.domain.DuelRow
import com.ivandsky.puzzlebox.compete.domain.ObserveMyDuels
import com.ivandsky.puzzlebox.compete.domain.SeatAction
import com.ivandsky.puzzlebox.compete.domain.SeatResult
import com.ivandsky.puzzlebox.compete.domain.UpdateMatchSeat
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the Duels tab shows: the [rows] exactly as [ObserveMyDuels] orders and kinds them, whether the
 * challenge card can start a duel ([signedIn]), and [now], which only redraws the rows' "time left".
 */
internal data class DuelsTabState(
    val rows: List<DuelRow>,
    val signedIn: Boolean,
    /** The name Past you's avatar takes its initial from: the viewer's own, "You" while signed out. */
    val selfName: String = SELF_NAME_FALLBACK,
    /** The photo Past you's avatar draws over that initial: the viewer's own, null signed out, without one, or with the flag off. */
    val selfPhotoUrl: String? = null,
    val now: Instant,
    /** The signed-in player's name, for the invite text; null while signed out. */
    val inviter: String?,
    /** Rows whose Accept or Decline call is running; their buttons are disabled. */
    val inFlight: Set<ShareCode> = emptySet(),
    /** The one-line outcome shown on an Invited row after its last call, until the next call on it starts. */
    val notes: Map<ShareCode, InviteNote> = emptyMap(),
    /** The duel the tablet's right pane shows and its row highlights ([resolveSelection]); null with no duels. */
    val selected: ShareCode? = null,
)

/** What an Invited row says after a call that did not open the duel or remove the row. */
internal enum class InviteNote { AlreadyTaken, Cancelled, Closed, AcceptFailed, DeclineFailed }

private data class RowCalls(val inFlight: Set<ShareCode> = emptySet(), val notes: Map<ShareCode, InviteNote> = emptyMap())

private const val SELF_NAME_FALLBACK = "You"

private const val NOW_REFRESH_MS = 60_000L
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * Passes [observeMyDuels] through to [state], runs the Invited rows' Accept and Decline through [updateSeat] on
 * [viewModelScope] (one call per row at a time) and keeps [DuelsTabState.now] one minute fresh from [clock].
 * Row kinds, including open, closed and reveal-ready, come only from the use case, which re-emits at each
 * time boundary itself. Nothing is collected, the ticker included, while no one is looking at the tab.
 * It also holds which duel the tablet's right pane shows ([DuelsTabState.selected]): the one the player picked
 * ([select]), else the [defaultSelection] pinned as soon as there is a row, so the pane does not hop to another duel
 * when the shown one stops needing the player. The pane and the list read this one holder. It also holds the New duel
 * being composed in the pane ([newDuel]), which a pick from the list ends.
 */
internal class DuelsTabViewModel(
    observeMyDuels: ObserveMyDuels,
    private val updateSeat: UpdateMatchSeat,
    identity: IdentityService,
    clock: () -> Instant,
) : ViewModel() {

    private val calls = MutableStateFlow(RowCalls())
    private val opens = Channel<ShareCode>(Channel.BUFFERED)
    private val pinned = MutableStateFlow<ShareCode?>(null)
    private val composing = MutableStateFlow<NewDuelRequest?>(null)

    /** One-shot: the duel screen to open for this code, after an accept or when sign-in is needed first. */
    val openDuel: Flow<ShareCode> = opens.receiveAsFlow()

    /** The New duel being composed in the tablet's pane, or null. */
    val newDuel: StateFlow<NewDuelRequest?> = composing.asStateFlow()

    val state: StateFlow<DuelsTabState> = combine(
        observeMyDuels().onEach { pin(it.rows) },
        identity.current,
        flow {
            while (true) {
                emit(clock())
                delay(NOW_REFRESH_MS)
            }
        },
        calls,
        pinned,
    ) { duels, who, now, pending, pick ->
        DuelsTabState(
            rows = duels.rows,
            signedIn = who != null,
            selfName = who?.displayName ?: SELF_NAME_FALLBACK,
            selfPhotoUrl = who?.photoUrl,
            now = now,
            inviter = who?.displayName,
            inFlight = pending.inFlight,
            notes = pending.notes,
            selected = resolveSelection(duels.rows, pick),
        )
    }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            DuelsTabState(
                rows = emptyList(),
                signedIn = identity.current.value != null,
                selfName = identity.current.value?.displayName ?: SELF_NAME_FALLBACK,
                selfPhotoUrl = identity.current.value?.photoUrl,
                now = clock(),
                inviter = identity.current.value?.displayName,
            ),
        )

    /** Shows [code] in the tablet's right pane; a New duel being composed there ends. */
    fun select(code: ShareCode) {
        composing.value = null
        pinned.value = code
    }

    /** Opens New duel in the tablet's pane, filled in from [challenge]. */
    fun startNewDuel(challenge: DuelChallenge? = null) {
        composing.value = NewDuelRequest(challenge)
    }

    /** The pane is done with New duel; it shows the selected duel again. */
    fun endNewDuel() {
        composing.value = null
    }

    /** The pane's duel [code] is done with (its invite closed): shows the next one in its place, if any. */
    fun leave(code: ShareCode) {
        pinned.value = defaultSelection(state.value.rows.filter { it.code != code })
    }

    private fun pin(rows: List<DuelRow>) {
        val next = resolveSelection(rows, pinned.value)
        if (next != pinned.value) pinned.value = next
    }

    /** Claims the seat; on success, or when sign-in is needed first, the duel screen opens. */
    fun accept(code: ShareCode) = call(code, SeatAction.Claim)

    /** Declines the invite; on success the row leaves with the next list emission. */
    fun decline(code: ShareCode) = call(code, SeatAction.Decline)

    private fun call(code: ShareCode, action: SeatAction) {
        while (true) {
            val before = calls.value
            if (code in before.inFlight) return
            if (calls.compareAndSet(before, before.copy(inFlight = before.inFlight + code, notes = before.notes - code))) break
        }
        viewModelScope.launch {
            val result = try {
                updateSeat(code, action)
            } finally {
                calls.update { it.copy(inFlight = it.inFlight - code) }
            }
            val failed = if (action == SeatAction.Claim) InviteNote.AcceptFailed else InviteNote.DeclineFailed
            val claim = action == SeatAction.Claim
            if (claim && (result == SeatResult.SignedOut || result == SeatResult.Done)) opens.send(code)
            val note = when (result) {
                SeatResult.Done -> null
                SeatResult.SignedOut -> failed.takeUnless { claim }
                SeatResult.Taken -> InviteNote.AlreadyTaken
                SeatResult.Cancelled -> InviteNote.Cancelled
                SeatResult.Closed -> InviteNote.Closed
                SeatResult.Offline, is SeatResult.Rejected -> failed
            }
            if (note != null) calls.update { it.copy(notes = it.notes + (code to note)) }
        }
    }
}
