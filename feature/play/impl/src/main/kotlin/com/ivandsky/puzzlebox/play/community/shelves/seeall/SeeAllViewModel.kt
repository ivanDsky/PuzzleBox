package com.ivandsky.puzzlebox.play.community.shelves.seeall

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.visibility.HiddenPuzzles
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.DifficultyChoice
import com.ivandsky.puzzlebox.play.community.GameChoice
import com.ivandsky.puzzlebox.play.community.toRow
import com.ivandsky.puzzlebox.play.domain.FriendPuzzleRow
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.play.domain.ReportPuzzle
import com.ivandsky.puzzlebox.play.community.CommunityRowModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The line a friend's puzzle that is not in the community list wears where the others say how old they are. */
private const val FRIEND_AGE = "sent to you"

/**
 * The See all page of the shelves home: one page for Most played, Newest, a game, From friends and Search ([open] picks
 * which). It lists the community's newest puzzles of any age (one read through [list], capped at 30 by the repository, no
 * paging), narrowed by a query over title, author and game name and by a game and a difficulty, newest first. The shelves home does not keep its puzzles' age, game or solve count, so this reads the list once for the
 * first page opened and again on [retry]; every page after it reuses what was read.
 *
 * From friends is the puzzles the player's friends sent ([friendPuzzles], local data, so it works offline); one that is
 * also in the community list keeps that list's level and age. A puzzle of a game this build cannot make is left out of
 * the community list.
 */
internal class SeeAllViewModel(
    private val list: ListCommunityPuzzles,
    friendPuzzles: ObserveFriendPuzzles,
    reportPuzzle: ReportPuzzle,
    hidden: HiddenPuzzles,
    private val games: GameRegistry,
    private val plays: GamePlayRegistry,
    identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
    private val clock: () -> Long,
) : ViewModel() {

    private val choices = games.all.filter { it.editor != null }.map {
        val id = it.definition.id
        GameChoice(id, games.displayName(id), plays.brandOf(id))
    }
    private val shipped = choices.map { it.id }.toSet()
    private val scope = MutableStateFlow<SeeAllScope?>(null)
    private val filters = MutableStateFlow(SeeAllFilters())
    private val loaded = MutableStateFlow<Loaded>(Loaded.Reading)
    private val removed = MutableStateFlow(emptySet<ShareCode>())
    private var loading: Job? = null
    private var opened = 0
    // A Report hides the puzzle in the persisted set; reading that set means a Report made on any page applies here.
    private val reporter = SeeAllReporter(viewModelScope, reportPuzzle, identity, idTokens) {}
    private val friends = combine(friendPuzzles(), removed) { rows, leaving -> rows.filter { it.code !in leaving } }

    val state: StateFlow<SeeAllUiState> = combine(
        combine(scope, filters, loaded, hidden.observe()) { page, picked, read, gone -> Page(page, picked, read, gone) },
        friends,
        reporter.state,
    ) { page, friends, report -> stateOf(page, friends, report) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SeeAllUiState(games = choices))

    /**
     * Opens [page]: clean (no query, no filters) when it is another page or another [visit] (the page was
     * closed in between), and as it was left when the same page opens again, as after a rotation or a return from a
     * row. The community list is read when nothing good is held yet.
     */
    fun open(page: SeeAllScope, visit: Int = 0) {
        if (scope.value != page || opened != visit) filters.value = SeeAllFilters()
        opened = visit
        scope.value = page
        if (loaded.value !is Loaded.Listed) load()
    }

    fun setQuery(query: String) = filters.update { it.copy(query = query) }

    fun setGame(game: GameId?) = filters.update { it.copy(game = game) }

    fun setDifficulty(difficulty: DifficultyChoice?) = filters.update { it.copy(difficulty = difficulty) }

    /** Drops the query, the game and the difficulty. */
    fun clearFilters() = filters.update { it.copy(query = "", game = null, difficulty = null) }

    /** Reads the community list again; Retry after an offline or refused list. */
    fun retry() = load()

    /**
     * The friends' puzzles in their Undo window ([codes], from the host's Remove friend): From friends drops them at once
     * and lists them again when Undo empties it.
     */
    fun friendsRemoved(codes: Set<ShareCode>) {
        removed.value = codes
    }

    fun report(code: ShareCode) = reporter.report(code)

    fun signIn(activityContext: Context) = reporter.signIn(activityContext)

    fun dismissSignIn() = reporter.dismissSignIn()

    fun noticeShown(id: Int) = reporter.noticeShown(id)

    private fun load() {
        loading?.cancel()
        loaded.value = Loaded.Reading
        loading = viewModelScope.launch {
            loaded.value = when (val result = list(CommunityFilter(windowMs = null))) {
                CommunityListResult.Offline -> Loaded.Offline
                CommunityListResult.Refused -> Loaded.Refused
                is CommunityListResult.Listed -> Loaded.Listed(
                    result.puzzles.filter { GameId(it.gameId) in shipped }.map {
                        val game = GameId(it.gameId)
                        val row = it.toRow(games.displayName(game), plays.brandOf(game), clock())
                        SeeAllEntry(row, game, it.createdAtMs)
                    },
                )
            }
        }
    }

    private fun stateOf(page: Page, friends: List<FriendPuzzleRow>, report: SeeAllReportState): SeeAllUiState {
        val community = (page.read as? Loaded.Listed)?.entries.orEmpty().filter { it.row.code !in page.dropped }
        val everything = if (page.scope == SeeAllScope.Friends) friendEntries(friends, community) else community.inScope(page.scope)
        // From friends is the friends' own order (non-solved first, newest opened first); every other page is newest first.
        val friendsPage = page.scope == SeeAllScope.Friends
        val shown = if (friendsPage) everything.narrowed(page.filters) else everything.selected(page.filters)
        val phase = when {
            page.scope == null || page.scope != SeeAllScope.Friends && page.read == Loaded.Reading -> SeeAllPhase.Loading
            page.scope != SeeAllScope.Friends && page.read == Loaded.Offline -> SeeAllPhase.Offline
            page.scope != SeeAllScope.Friends && page.read == Loaded.Refused -> SeeAllPhase.Refused
            else -> SeeAllPhase.Ready(shown.map { it.row })
        }
        return SeeAllUiState(
            scope = page.scope,
            phase = phase,
            query = page.filters.query,
            game = page.filters.game,
            difficulty = page.filters.difficulty,
            games = choices,
            showGameFilter = page.scope !is SeeAllScope.Game,
            noMatch = phase is SeeAllPhase.Ready && shown.isEmpty() && everything.isNotEmpty() && page.filters.narrowing,
            signInPrompt = report.signInPrompt,
            signIn = report.signIn,
            notice = report.notice,
        )
    }

    /** The friends' puzzles in their own order, each with the community list's level and age when it has the puzzle. */
    private fun friendEntries(friends: List<FriendPuzzleRow>, community: List<SeeAllEntry>): List<SeeAllEntry> {
        val byCode = community.associateBy { it.row.code }
        return friends.map { friend ->
            byCode[friend.code] ?: SeeAllEntry(friend.toRow(), friend.gameId, createdAtMs = null)
        }
    }

    private fun FriendPuzzleRow.toRow() =
        CommunityRowModel(code, games.displayName(gameId), plays.brandOf(gameId), title, authorName, difficulty = null, age = FRIEND_AGE)

    private data class Page(val scope: SeeAllScope?, val filters: SeeAllFilters, val read: Loaded, val dropped: Set<ShareCode>)

    private sealed interface Loaded {
        data object Reading : Loaded
        data object Offline : Loaded
        data object Refused : Loaded
        data class Listed(val entries: List<SeeAllEntry>) : Loaded
    }
}
