package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntries
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.game.CompletionRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart

/**
 * Overall standings with their completeness (A11, A12), derived from the window-boards flow it is given (the hub's one
 * shared collection, or [ObserveFriendsOverall]'s own): it attaches no listener and reads nothing of its own, so the
 * read budget (A23) stays [ObserveRankWindow]'s.
 */
class ObserveOverall(
    private val identity: IdentityService,
    private val completions: CompletionRepository,
) {

    /**
     * Ranks the [boards] it is given: the shared collection of [ObserveRankWindow], or its [amongFriends] projection for
     * Overall among friends (F18). Its boards already carry the window's days.
     */
    operator fun invoke(boards: Flow<RankWindowBoards>): Flow<OverallState> =
        combine(boards, identity.current, completions.observeRecords(), ::stateOf)
            .onStart { emit(OverallState.Loading) }
            .distinctUntilChanged()

    private fun stateOf(boards: RankWindowBoards, me: Identity?, records: List<CompletionRecord>): OverallState {
        if (boards !is RankWindowBoards.Boards || me == null) return OverallState.SignedOut
        val loaded = boards.boards.filterIsInstance<BoardLoad.Loaded>()
        val loading = boards.boards.count { it is BoardLoad.Loading }
        val unavailable = boards.boards.count { it is BoardLoad.Unavailable }
        val total = boards.boards.size
        if (loaded.isEmpty()) {
            return when {
                loading > 0 -> OverallState.Loading
                unavailable > 0 -> OverallState.Offline(null)
                else -> OverallState.Empty
            }
        }

        val standings = windowStandings(loaded, me, records)
        return when {
            loading > 0 -> OverallState.Partial(standings, loaded.size, total)
            unavailable == 0 -> if (standings.rows.isEmpty()) OverallState.Empty else OverallState.Complete(standings)
            // An open board listener answers from cache even offline, so no live board means no connection.
            loaded.all { it.final } -> OverallState.Offline(standings)
            else -> OverallState.Partial(standings, loaded.size, total)
        }
    }
}

/**
 * Ranks [loaded] by points (A10): Overall over every game's boards, and a game's Week or Month over that game's days
 * (WeekMonthPoints). [me], when signed in, is placed past a board's read page where [records] hold an official solve
 * of that board but the page has no row of theirs (A11), and is named from the profile where no page names them.
 */
fun windowStandings(loaded: List<BoardLoad.Loaded>, me: Identity?, records: List<CompletionRecord>): OverallStandings {
    val ranked = loaded.map { rankBoard(it.board, it.page.entries, it.order, it.page.count) }
    if (me == null) return overall(ranked, viewerUid = null, viewerBeyondPage = emptySet())
    val standings = overall(ranked, me.uid, beyondPage(loaded, me, records))
    // `overall` has no name for a viewer on no page; the profile has.
    return standings.copy(
        rows = standings.rows.map { if (it.uid == me.uid && it.displayName.isEmpty()) it.copy(displayName = me.displayName) else it },
    )
}

/**
 * A11: the boards at the cap where the viewer has an official solve of their own ([records]) but no row, so they placed
 * beyond the page. Their points count those boards; a board the viewer is unofficial on is not one of them.
 */
fun beyondPage(loaded: List<BoardLoad.Loaded>, me: Identity, records: List<CompletionRecord>): Set<LeaderboardRef> {
    val official = records.mapNotNullTo(HashSet()) { record ->
        LeaderboardEntries.fromRecord(record, me.uid, me.displayName)?.let { LeaderboardRef.fromPuzzleRefKey(it.puzzleRef) }
    }
    return loaded.filter { load ->
        load.page.entries.size >= BoardPage.PAGE_SIZE &&
            load.board in official &&
            load.page.entries.none { it.uid == me.uid }
    }.mapTo(HashSet()) { it.board }
}
