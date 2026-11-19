package com.ivandsky.puzzlebox.play.community

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.ReportResult
import com.ivandsky.puzzlebox.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.domain.ReportPuzzle
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.PlayFlag
import com.ivandsky.puzzlebox.play.board.BoardSignInHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * New today (nav-rethink C2): the community puzzles of the last day through [list], newest first, narrowed by a
 * game and a difficulty picked in the filter menu (All, then each game that has an editor; Any, then Easy to Hard).
 * Picking reloads the list; the difficulty picked again is dropped, and [clearFilters] drops both. Report
 * ([report]) hides a puzzle at once on this device and says so in a notice; signed out it opens the sign-in prompt instead and the player taps Report again.
 * [releaseFlags] (null: every flag off) decides how the filter is reached, [CommunityState.filterSheet]; live, so a debug
 * switch applies without a restart.
 *
 * A puzzle of a game this build cannot open is left out.
 */
internal class CommunityViewModel(
    private val list: ListCommunityPuzzles,
    private val reportPuzzle: ReportPuzzle,
    private val games: GameRegistry,
    private val plays: GamePlayRegistry,
    private val identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
    private val clock: () -> Long,
    releaseFlags: ReleaseFlags? = null,
) : ViewModel() {

    private val choices = games.all.filter { it.editor != null }.map {
        val id = it.definition.id
        GameChoice(id, games.displayName(id), plays.brandOf(id))
    }
    private val shipped = choices.map { it.id }.toSet()
    private val signInHolder = BoardSignInHolder(identity, idTokens)
    private val reporting = mutableSetOf<ShareCode>()
    private var loading: Job? = null
    private var noticeCount = 0

    private val current = MutableStateFlow(
        CommunityState(games = choices, filterSheet = releaseFlags?.isEnabled(PlayFlag.CommunityFilterSheet) == true),
    )

    val state: StateFlow<CommunityState> = current.asStateFlow()

    init {
        viewModelScope.launch { signInHolder.status.collect { status -> current.update { it.copy(signIn = status) } } }
        releaseFlags?.let { flags ->
            viewModelScope.launch { flags.enabled.collect { on -> current.update { it.copy(filterSheet = PlayFlag.CommunityFilterSheet in on) } } }
        }
        load()
    }

    /** Narrows to [game], or to every game for null; the chip already picked does nothing. */
    fun pickGame(game: GameId?) {
        if (game == current.value.game) return
        current.update { it.copy(game = game) }
        load()
    }

    /** Narrows to [choice]; the one already picked, or null (Any), drops back to every difficulty. */
    fun pickDifficulty(choice: DifficultyChoice?) {
        current.update { it.copy(difficulty = choice.takeIf { picked -> picked != it.difficulty }) }
        load()
    }

    /** Drops the game and the difficulty together, back to every puzzle, in one reload. */
    fun clearFilters() {
        current.update { it.copy(game = null, difficulty = null) }
        load()
    }

    /** Loads the list again under the filters picked now; Retry after an offline or refused list. */
    fun retry() = load()

    /** Reports [code]. Reported (or already reported) it drops from the list at once; every other result hides nothing. */
    fun report(code: ShareCode) {
        if (!reporting.add(code)) return
        viewModelScope.launch {
            try {
                when (reportPuzzle(code)) {
                    ReportResult.Reported, ReportResult.AlreadyReported -> {
                        current.update { it.without(code) }
                        say("Reported. It's hidden on this device.")
                    }
                    ReportResult.SignedOut -> current.update { it.copy(signInPrompt = true) }
                    ReportResult.Offline -> say("You're offline. Report it when you're back.")
                    is ReportResult.Rejected -> say("Couldn't send the report. Try again later.")
                }
            } finally {
                reporting -= code
            }
        }
    }

    /** The sign-in prompt's Google button; [activityContext] hosts Google's account picker and is not kept. Signed in, the prompt closes. */
    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            signInHolder.signIn(activityContext)
            if (identity.current.value != null) current.update { it.copy(signInPrompt = false) }
        }
    }

    fun dismissSignIn() = current.update { it.copy(signInPrompt = false) }

    /** The snackbar for notice [id] has been and gone. */
    fun noticeShown(id: Int) = current.update { if (it.notice?.id == id) it.copy(notice = null) else it }

    private fun say(message: String) = current.update { it.copy(notice = CommunityNotice(++noticeCount, message)) }

    private fun load() {
        loading?.cancel()
        val picked = current.value
        current.update { it.copy(phase = CommunityPhase.Loading) }
        loading = viewModelScope.launch {
            val result = list(CommunityFilter(picked.game?.value, picked.difficulty?.levels.orEmpty()))
            val now = clock()
            val phase = when (result) {
                is CommunityListResult.Listed -> CommunityPhase.Loaded(
                    result.puzzles.filter { GameId(it.gameId) in shipped }.map {
                        val game = GameId(it.gameId)
                        it.toRow(games.displayName(game), plays.brandOf(game), now)
                    },
                )
                CommunityListResult.Offline -> CommunityPhase.Offline
                CommunityListResult.Refused -> CommunityPhase.Refused
            }
            current.update { it.copy(phase = phase) }
        }
    }

    private fun CommunityState.without(code: ShareCode): CommunityState {
        val shown = phase as? CommunityPhase.Loaded ?: return this
        return copy(phase = shown.copy(rows = shown.rows.filterNot { it.code == code }))
    }
}
