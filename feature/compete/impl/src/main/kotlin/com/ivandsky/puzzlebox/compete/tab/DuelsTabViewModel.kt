package com.ivandsky.puzzlebox.compete.tab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.domain.ObserveMyDuels
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * What the Duels tab shows: the [rows] exactly as [ObserveMyDuels] orders and kinds them, whether the
 * challenge card can start a duel ([signedIn]), and [now], which only redraws the rows' "time left".
 */
internal data class DuelsTabState(
    val rows: List<DuelRow>,
    val signedIn: Boolean,
    val now: Instant,
)

private const val NOW_REFRESH_MS = 60_000L
private const val STOP_TIMEOUT_MS = 5_000L

/**
 * Passes [observeMyDuels] through to [state] and keeps [DuelsTabState.now] one minute fresh from [clock].
 * Row kinds, including open, closed and reveal-ready, come only from the use case, which re-emits at each
 * time boundary itself. Nothing is collected, the ticker included, while no one is looking at the tab.
 */
internal class DuelsTabViewModel(
    observeMyDuels: ObserveMyDuels,
    identity: IdentityService,
    clock: () -> Instant,
) : ViewModel() {

    val state: StateFlow<DuelsTabState> = combine(
        observeMyDuels(),
        identity.current,
        flow {
            while (true) {
                emit(clock())
                delay(NOW_REFRESH_MS)
            }
        },
    ) { duels, who, now -> DuelsTabState(rows = duels.rows, signedIn = who != null, now = now) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            DuelsTabState(rows = emptyList(), signedIn = identity.current.value != null, now = clock()),
        )
}
