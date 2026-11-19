package com.ivandsky.puzzlebox.history.ranks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.data.visibility.VisibilityRepository
import com.ivandsky.puzzlebox.data.visibility.VisibilityTier
import com.ivandsky.puzzlebox.resultui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The replay opt-in prompt: [show] is true while the sheet should be up, which is only when the player's tier
 * was never chosen ([OwnTier.Unset]; [OwnTier.Unknown] fails closed, so a cold start never flashes it) and the hub shows
 * their official ranked row ([rankedRow]). Either answer writes a tier, which is what keeps it from coming back; [answered]
 * also hides it at once, before the write lands. With [ReleaseFlag.SheetScreens] on it also waits while another sheet is
 * open ([hold]), so two sheets are never up together.
 */
internal class ReplayOptInViewModel(private val visibility: VisibilityRepository, releaseFlags: ReleaseFlags) : ViewModel() {

    private val holdable = releaseFlags.isEnabled(ReleaseFlag.SheetScreens)
    private val ranked = MutableStateFlow(false)
    private val answered = MutableStateFlow(false)
    private val held = MutableStateFlow(false)

    val show: StateFlow<Boolean> = combine(visibility.ownTier, ranked, answered, held) { tier, ranked, answered, held ->
        tier == OwnTier.Unset && ranked && !answered && !held
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Whether the board on screen has the viewer's official row. */
    fun rankedRow(present: Boolean) {
        ranked.value = present
    }

    /** Another Ranks sheet is open: the question waits for it to close (SheetScreens on). */
    fun hold(sheetOpen: Boolean) {
        if (holdable) held.value = sheetOpen
    }

    /** "Share my replays": future solves only. */
    fun share() = answer(VisibilityTier.Replays)

    /** "Keep private", and a dismissal: the default tier, written so the choice is on record. */
    fun keepPrivate() = answer(VisibilityTier.Board)

    private fun answer(tier: VisibilityTier) {
        answered.value = true
        viewModelScope.launch { visibility.setOwnTier(tier) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** The viewer's official placed row is on this board; an unofficial or LinkedIn-only row has no PuzzleBox place. */
internal fun DailyBoardState.hasOfficialOwnRow(): Boolean {
    if (this !is DailyBoardState.Ranked) return false
    fun DailyBoardRowModel.official() = isSelf && !unofficial && !fromLinkedIn
    return rows.any { it.official() } || pinned?.official() == true
}
