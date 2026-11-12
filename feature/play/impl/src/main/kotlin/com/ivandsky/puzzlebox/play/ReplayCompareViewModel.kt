package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.replay.CompareNames
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.api.ReplayCompareKey
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the compare page shows: nothing yet, no replay on either side, or the two sides, one of which may be null (S7). */
internal sealed interface ReplayCompareState {
    data object Loading : ReplayCompareState

    data object None : ReplayCompareState

    data class Ready(val mine: Timeline?, val theirs: Timeline?) : ReplayCompareState
}

/**
 * The state holder of the compare page for [key]. It reads both timelines once when it opens, through
 * [LoadSolveReplay], so a key (ids only) is all a surface passes and the page survives process death. Read-only.
 */
internal class ReplayCompareViewModel(
    private val key: ReplayCompareKey,
    loadReplay: LoadSolveReplay,
    private val games: GameRegistry,
    ui: GamePlayRegistry,
    private val calendar: GameCalendar,
) : ViewModel() {

    private val gameId = GameId(key.gameId)

    private val _state = MutableStateFlow<ReplayCompareState>(ReplayCompareState.Loading)
    val state: StateFlow<ReplayCompareState> = _state

    /** The game's replay board; null for a game that has none, which the page shows as no replay. */
    val board: GameReplayBoard? = ui[gameId]?.replayBoard

    val names = CompareNames(key.myName, key.theirName)

    /** The verdict the lead line ends on, as the surface that opened the page said it. */
    val finalLead: Lead? = when (key.lead) {
        ReplayCompareKey.LEAD_MINE -> Lead.Mine
        ReplayCompareKey.LEAD_THEIRS -> Lead.Theirs
        ReplayCompareKey.LEAD_LEVEL -> Lead.Level
        else -> null // a lead this build does not know (a newer key): no verdict to end on, never a wrong one
    }

    /** The page's "Game · where it came from" line. */
    val context: String get() = "${games.displayName(gameId)} · ${key.mine().origin(calendar)}"

    init {
        viewModelScope.launch {
            val (mine, theirs) = coroutineScope {
                val mine = async { key.mine().toSolveReplayRef(games)?.let { loadReplay(it) } }
                val theirs = async { key.theirs().toSolveReplayRef(games)?.let { loadReplay(it) } }
                mine.await() to theirs.await()
            }
            _state.value = if (mine == null && theirs == null) ReplayCompareState.None else ReplayCompareState.Ready(mine, theirs)
        }
    }
}
