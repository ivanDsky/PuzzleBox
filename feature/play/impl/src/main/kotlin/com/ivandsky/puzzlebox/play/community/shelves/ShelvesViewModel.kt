package com.ivandsky.puzzlebox.play.community.shelves

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.PublishedPuzzle
import com.ivandsky.puzzlebox.data.repository.CommunityFilter
import com.ivandsky.puzzlebox.data.repository.CommunityListResult
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.GameChoice
import com.ivandsky.puzzlebox.play.domain.FriendPuzzleRow
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.ObserveFriendPuzzles
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
 * The entry tiles read local data and keep up with it; the shelves are read once per load, and [retry] loads again.
 */
internal class ShelvesViewModel(
    private val list: ListCommunityPuzzles,
    friendPuzzles: ObserveFriendPuzzles,
    drafts: DraftRepository,
    published: PublishedPuzzleRepository,
    private val completions: CompletionRepository,
    private val games: GameRegistry,
    plays: GamePlayRegistry,
    private val clock: () -> Long,
) : ViewModel() {

    private val choices = games.all.filter { it.editor != null }.map {
        val id = it.definition.id
        GameChoice(id, games.displayName(id), plays.brandOf(id))
    }.associateBy { it.id }
    private val friends = friendPuzzles()
    private val own = published.observeAll()
    private val sketches = drafts.observeAll()
    private val loaded = MutableStateFlow<ShelvesUiState>(ShelvesUiState.Loading)
    private var loading: Job? = null

    val state: StateFlow<ShelvesUiState> =
        combine(loaded, friends, own, sketches) { shown, friendRows, mine, kept -> shown.withEntries(entriesOf(friendRows, mine, kept)) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ShelvesUiState.Loading)

    init {
        load()
    }

    /** Loads the shelves again; Retry after an offline or refused read. */
    fun retry() = load()

    private fun load() {
        loading?.cancel()
        loaded.value = ShelvesUiState.Loading
        loading = viewModelScope.launch { loaded.value = read() }
    }

    private suspend fun read(): ShelvesUiState {
        val result = list(CommunityFilter(windowMs = null))
        return when (result) {
            CommunityListResult.Offline -> ShelvesUiState.Offline()
            CommunityListResult.Refused -> ShelvesUiState.Refused()
            is CommunityListResult.Listed -> {
                val shown = result.puzzles.filter { GameId(it.gameId) in choices }
                if (shown.isEmpty()) return ShelvesUiState.Empty()
                val friendCodes = friends.first().map { it.code }.toSet()
                val ownCodes = own.first().map { it.code }.toSet()
                val puzzles = shown.map {
                    ShelfPuzzle(it.code, GameId(it.gameId), it.createdAtMs, solves = null, isFriend = it.code in friendCodes, isOwn = it.code in ownCodes)
                }
                ShelvesUiState.Content(
                    plan = buildShelfPlan(puzzles, gameShelfOrder(mostPlayedFirst(), choices.keys.toList()), clock()),
                    puzzles = shown.associate { it.code to ShelfPuzzleModel(it.title, it.authorName, it.difficulty) },
                    games = choices,
                )
            }
        }
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
