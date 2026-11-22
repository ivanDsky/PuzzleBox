package com.ivandsky.puzzlebox.play.community.shelves

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.visibility.HiddenPuzzles
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.GameChoice
import com.ivandsky.puzzlebox.play.community.shelves.seeall.SeeAllReportState
import com.ivandsky.puzzlebox.play.community.shelves.seeall.SeeAllReporter
import com.ivandsky.puzzlebox.play.domain.FriendPuzzleRow
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.ObserveFriendPuzzles
import com.ivandsky.puzzlebox.play.domain.ReportPuzzle
import com.ivandsky.puzzlebox.play.domain.shelves.ShelfPuzzle
import com.ivandsky.puzzlebox.play.domain.shelves.buildShelfPlan
import com.ivandsky.puzzlebox.play.domain.shelves.gameShelfOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val INITIALS_SHOWN = 3

/**
 * The shelves home: the newest public puzzles of any age through [list] (the repository caps them at 30, no paging),
 * laid out by [buildShelfPlan] with the player's friends' puzzles marked and the game shelves ordered by the game the
 * player has solved most. A puzzle of a game this build cannot make is left out. There is no solve counter yet, so
 * the plan has no Spotlight and no Most played.
 *
 * The entry tiles read local data and keep up with it; the community list is read once per load, and [retry] loads again.
 * The plan is laid out again from that read whenever the friends' puzzles, your own or the reported puzzles change, so a
 * removed friend's badge goes and a reported puzzle leaves the shelves without a new read.
 *
 * A card's long-press Report is the See all page's ([SeeAllReporter]): [report] hides the puzzle on this device and
 * says so on [reportState]; signed out it asks to sign in first.
 */
internal class ShelvesViewModel(
    private val list: ListCommunityPuzzles,
    friendPuzzles: ObserveFriendPuzzles,
    drafts: DraftRepository,
    published: PublishedPuzzleRepository,
    private val completions: CompletionRepository,
    private val games: GameRegistry,
    plays: GamePlayRegistry,
    reportPuzzle: ReportPuzzle,
    hidden: HiddenPuzzles,
    identity: IdentityService,
    idTokens: GoogleIdTokenProvider,
    private val clock: () -> Long,
) : ViewModel() {

    /** What the last read of the community list left. */
    private sealed interface Read {
        data object Reading : Read

        /** A read with no shelves to lay out: [ui] is Empty, Offline or Refused. */
        data class Done(val ui: ShelvesUiState) : Read

        /** The [puzzles] of a games-this-build-makes read and the game shelf [order]. */
        data class Listed(val puzzles: List<SharedPuzzleDoc>, val order: List<GameId>) : Read
    }

    private val choices = games.all.filter { it.editor != null }.map {
        val id = it.definition.id
        GameChoice(id, games.displayName(id), plays.brandOf(id))
    }.associateBy { it.id }
    private val own = published.observeAll()
    private val sketches = drafts.observeAll()
    private val loaded = MutableStateFlow<Read>(Read.Reading)
    private val removed = MutableStateFlow(emptySet<ShareCode>())
    private val friends = combine(friendPuzzles(), removed) { rows, leaving -> rows.filter { it.code !in leaving } }
    private var loading: Job? = null
    // A Report hides the puzzle in the persisted set; reading that set means a Report made on any page applies here.
    private val reporter = SeeAllReporter(viewModelScope, reportPuzzle, identity, idTokens) {}

    val state: StateFlow<ShelvesUiState> =
        combine(loaded, friends, own, sketches, hidden.observe()) { read, friendRows, mine, kept, gone ->
            val shown = when (read) {
                Read.Reading -> ShelvesUiState.Loading()
                is Read.Done -> read.ui
                is Read.Listed -> contentOf(read, friendRows, mine, gone)
            }
            shown.withEntries(entriesOf(friendRows, mine, kept))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ShelvesUiState.Loading())

    /** What a card's Report leaves on the home: the notice and the sign-in prompt. */
    val reportState: StateFlow<SeeAllReportState> = reporter.state

    /**
     * The friends' puzzles in their Undo window ([codes], from the host's Remove friend): they stop counting as friends'
     * at once, on the badge and the From friends tile, and count again when Undo empties it.
     */
    fun friendsRemoved(codes: Set<ShareCode>) {
        removed.value = codes
    }

    /** Hides [code]'s puzzle on this device and says so; signed out it asks to sign in. */
    fun report(code: ShareCode) = reporter.report(code)

    /** The sign-in dialog's Google button; [activityContext] hosts Google's account picker and is not kept. */
    fun signIn(activityContext: Context) = reporter.signIn(activityContext)

    fun dismissSignIn() = reporter.dismissSignIn()

    /** The snackbar for notice [id] has been and gone. */
    fun noticeShown(id: Int) = reporter.noticeShown(id)

    init {
        load()
    }

    /** Loads the shelves again; Retry after an offline or refused read. */
    fun retry() = load()

    private fun load() {
        loading?.cancel()
        loaded.value = Read.Reading
        loading = viewModelScope.launch { loaded.value = read() }
    }

    private suspend fun read(): Read {
        val result = list(CommunityFilter(windowMs = null))
        return when (result) {
            CommunityListResult.Offline -> Read.Done(ShelvesUiState.Offline())
            CommunityListResult.Refused -> Read.Done(ShelvesUiState.Refused())
            is CommunityListResult.Listed -> {
                val shown = result.puzzles.filter { GameId(it.gameId) in choices }
                if (shown.isEmpty()) Read.Done(ShelvesUiState.Empty()) else Read.Listed(shown, gameShelfOrder(mostPlayedFirst(), choices.keys.toList()))
            }
        }
    }

    /** The shelves of [read] without the [gone] puzzles, with the friends' and your own marked as they are now. */
    private fun contentOf(read: Read.Listed, friendRows: List<FriendPuzzleRow>, mine: List<PublishedPuzzle>, gone: Set<ShareCode>): ShelvesUiState {
        val shown = read.puzzles.filter { it.code !in gone }
        if (shown.isEmpty()) return ShelvesUiState.Empty()
        val friendCodes = friendRows.map { it.code }.toSet()
        val ownCodes = mine.map { it.code }.toSet()
        val puzzles = shown.map {
            ShelfPuzzle(it.code, GameId(it.gameId), it.createdAtMs, solves = null, isFriend = it.code in friendCodes, isOwn = it.code in ownCodes)
        }
        return ShelvesUiState.Content(
            plan = buildShelfPlan(puzzles, read.order, clock()),
            puzzles = shown.associate { it.code to ShelfPuzzleModel(it.title, it.authorName, it.difficulty) },
            games = choices,
        )
    }

    /** The games the player has solved puzzles of, the one with the most solved puzzles first: all the local source of play counts there is. */
    private suspend fun mostPlayedFirst(): List<GameId> =
        completions.observeRecords().first().groupingBy { it.puzzleRef.gameId }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }

    private fun entriesOf(friendRows: List<FriendPuzzleRow>, mine: List<PublishedPuzzle>, kept: List<Draft>): ShelvesEntries {
        val live = mine.filterNot { it.unpublished }
        val liveCodes = live.map { it.code.value }.toSet()
        return ShelvesEntries(
            friendPuzzles = friendRows.size,
            friendInitials = friendRows.mapNotNull { it.authorName.trim().firstOrNull()?.uppercaseChar() }.distinct().take(INITIALS_SHOWN),
            published = live.size,
            drafts = kept.count { it.reservedCode !in liveCodes },
        )
    }
}
