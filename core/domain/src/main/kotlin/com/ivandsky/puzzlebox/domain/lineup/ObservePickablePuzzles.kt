package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.model.PuzzleRefKeys
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The one list the add sheet picks an exact puzzle from: community, friends' and the player's own puzzles and the pages of
 * the daily archive read so far, newest first, narrowed on the device by the query. The community is read once per call
 * of [invoke], so changing the query only refilters.
 */
class ObservePickablePuzzles internal constructor(
    private val completions: CompletionRepository,
    private val community: CommunitySource,
    private val friends: FriendSource,
    private val own: OwnSource,
    private val archive: DailyArchiveSource,
) {
    /** [archivePages] is how many pages of the daily archive the sheet has asked for so far. */
    operator fun invoke(query: Flow<PickerQuery>, archivePages: Flow<Int>): Flow<PickerResult> {
        val played = completions.observeRecords().map { records -> records.mapTo(HashSet()) { PuzzleRefKeys.encode(it.puzzleRef) } }
        return combine(query, community.observe(played), friends.observe(played), own.observe(played), archive.observe(played, archivePages)) { q, c, f, o, a ->
            // A friend's puzzle that is also public is one puzzle: the friend's row wins.
            val rows = (f.rows + c.rows + o.rows + a.rows).distinctBy { it.puzzle.entry.ref }
                .sortedByDescending { it.atMs }.map { it.puzzle }.filter { it.passes(q) }
            PickerResult(rows, offline = c.offline)
        }
    }
}

private fun PickablePuzzle.passes(query: PickerQuery): Boolean =
    matches(query.text) &&
        (query.fresh.isEmpty() || Freshness.New in query.fresh && isNew || Freshness.NotPlayed in query.fresh && !played) &&
        (query.sources.isEmpty() || entry.source in query.sources) &&
        (query.games.isEmpty() || entry.gameId in query.games)

private fun PickablePuzzle.matches(text: String): Boolean {
    val needle = text.trim()
    if (needle.isEmpty()) return true
    return listOfNotNull(entry.title, entry.author, code?.value, code?.display).any { it.contains(needle, ignoreCase = true) }
}
